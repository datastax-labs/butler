/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import static com.datastax.butler.commons.jira.client.JiraException.error;
import static java.lang.String.format;

import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.issues.jira.JiraIssueId;
import com.datastax.butler.commons.web.Credentials;
import com.datastax.butler.commons.web.CredentialsException;
import com.datastax.butler.commons.web.OkHttpWebClient;
import com.datastax.butler.commons.web.WebClient;
import com.datastax.butler.commons.web.WebClient.InvalidRequestException;
import com.datastax.butler.commons.web.WebClient.NotFoundException;
import com.google.errorprone.annotations.FormatMethod;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of a JIRA rest-api based client. The only responsibility of this class is to make
 * REST api calls. Parsing issues etc. is provided by the JiraProject implementations.
 */
public class JiraClient {
  private static final Logger logger = LogManager.getLogger();
  private static final MediaType JSON_TYPE = MediaType.parse("application/json");

  private final String siteUrl;
  private final WebClient webClient;
  private final String credentials;
  private final JiraJsonConverter jsonConverter = new JiraJsonConverter();

  /**
   * Creates a new JIRA client with authentication.
   *
   * @param siteUrl the url of the JIRA server to connect to.
   * @param webClient the {@link WebClient} implementation to use to query the JIRA server.
   * @param credentials the credentials to use to connect to the JIRA server.
   */
  public JiraClient(String siteUrl, WebClient webClient, Credentials credentials) {
    this.siteUrl = siteUrl;
    this.webClient = webClient;
    this.credentials =
        credentials != null
            ? okhttp3.Credentials.basic(credentials.login(), credentials.password())
            : null;
  }

  /** Constructor for jira client without authentication. */
  public JiraClient(String siteUrl, WebClient webClient) {
    this(siteUrl, webClient, null);
  }

  /**
   * Creates authenticated / not authenticated jira client depending if credentials present in
   * netrc.
   */
  public static JiraClient create(String jiraUrl) {
    try {
      var url = HttpUrl.parse(jiraUrl);
      if (url == null) throw new IllegalArgumentException("Cannot parse " + jiraUrl);
      var credentials = Credentials.readFromNetrcFile(url.host());
      return new JiraClient(jiraUrl, new OkHttpWebClient(), credentials);
    } catch (CredentialsException ex) {
      return new JiraClient(jiraUrl, new OkHttpWebClient());
    }
  }

  private HttpUrl.Builder builder() {
    return Objects.requireNonNull(HttpUrl.parse(siteUrl)).newBuilder();
  }

  private HttpUrl.Builder restApiRequest() {
    // Note: we use version 2 of the JIRA rest API, not version 3. The main difference is that
    // version 3 uses the "Atlassian Document Format"
    // (https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/) for fields
    // like description, comments, ...., where version 2 is just using strings with the JIRA markup.
    return builder().addPathSegments("rest/api/2");
  }

  private Request makeGetRequest(HttpUrl.Builder urlBuilder) {
    var builder = new Request.Builder().url(urlBuilder.build());
    if (credentials != null) builder.addHeader("Authorization", credentials);
    return builder.get().build();
  }

  private String get(HttpUrl.Builder urlBuilder) throws InvalidRequestException {
    return request(makeGetRequest(urlBuilder));
  }

  private String request(Request request) throws InvalidRequestException {
    try {
      return webClient.request(request);
    } catch (IOException e) {
      throw JiraException.ioError(e, "I/O error while requesting %s", request);
    }
  }

  private Request makePostRequest(HttpUrl.Builder urlBuilder, String body) {
    var builder = new Request.Builder().url(urlBuilder.build());
    if (credentials != null) builder.addHeader("Authorization", credentials);
    return builder.post(RequestBody.create(body, JSON_TYPE)).build();
  }

  private String post(HttpUrl.Builder urlBuilder, String body) throws InvalidRequestException {
    return request(makePostRequest(urlBuilder, body));
  }

  // We're building our underlying formatting string dynamically, which error prone is not able
  // to validate. It's legit in that case however, so suppressing warning.
  @SuppressWarnings("FormatStringAnnotation")
  @FormatMethod
  private JiraException toJiraException(
      InvalidRequestException exc, String requested, Object... args) {
    String msg = format(requested, args);
    switch (exc.statusCode()) {
      case 400:
        throw error(format("Invalid request while %s: %s", msg, exc.getMessage()));
      case 401:
      case 403:
        throw error(
            format(
                "Unauthorized access while %s (make sure your credentials are available "
                    + "and correct)",
                msg));
      default:
        throw error(format("Unexpected error while %s (status code: %d)", msg, exc.statusCode()));
    }
  }

  /** The url to the issue this link to. */
  public URL browseUrl(IssueId issueId) {
    return builder().addPathSegment("browse").addPathSegment(issueId.toString()).build().url();
  }

  /**
   * Creates a "link" to the provided issue with unknown "closed" status.
   *
   * @param issueId the name of the issue for which to create a link.
   * @return the link object.
   */
  public IssueLink link(IssueId issueId) {
    return new IssueLink(issueId, browseUrl(issueId));
  }

  public Optional<String> fetchIssue(IssueId issueId) {
    return fetchIssue(issueId, Collections.emptyList());
  }

  /**
   * Fetch the provided issue by name returning json with requested fields.
   *
   * <p>from butler perspective we not not fetch extra/special fields as the reason of fetch is to
   * check status and print basic info.
   *
   * @param issueId the name of the issue/ticket to fetch.
   * @return the JIRA issue corresponding to {@code name} if it exists, an empty optional otherwise.
   * @throws JiraException if an unexpected error occurs while fetching the issue.
   */
  public Optional<String> fetchIssue(IssueId issueId, List<String> additionalFields) {
    try {
      var request = restApiRequest().addPathSegment("issue").addPathSegment(issueId.toString());
      Set<String> fields = new HashSet<>();
      fields.addAll(List.of("summary", "description", "resolution", "status"));
      fields.addAll(additionalFields);
      setFieldsToFetch(request, fields);
      String response = get(request);
      return Optional.of(response);
    } catch (NotFoundException e) {
      return Optional.empty();
    } catch (InvalidRequestException e) {
      throw toJiraException(e, "retrieving issue %s", issueId);
    }
  }

  /**
   * Create new issue in given project using provided issue body json.
   *
   * @param issueBody full, rendered, issue body (json)
   * @return newly created issue id e.g. XYZ-12
   */
  public JiraIssueId createIssue(String issueBody) {
    // https://blog.developer.atlassian.com/creating-a-jira-cloud-issue-in-a-single-rest-call/
    // https://developer.atlassian.com/server/jira/platform/jira-rest-api-examples/
    HttpUrl.Builder request = restApiRequest().addPathSegment("issue");
    try {
      return jsonConverter.parseCreatedIssueResponse(post(request, issueBody));
    } catch (NotFoundException e) {
      logger.error("404 from jira: {} {} {}", request, issueBody, e);
      throw error("Got 404 error when creating issue: %s %s", request, issueBody);
    } catch (InvalidRequestException e) {
      logger.error("Invalid request from jira: {} {} {}", request, issueBody, e);
      throw toJiraException(e, "creating issue");
    }
  }

  /**
   * Search for issues using Jira Query Language (JQL).
   *
   * @param jql the JQL query to search for.
   * @return the list of issues returned by JIRA for {@code jql}.
   */
  public List<JiraIssueId> search(String jql) {
    try {
      List<JiraIssueId> allIssues = new ArrayList<>();
      int startAt = 0;
      while (true) {
        HttpUrl.Builder request =
            restApiRequest()
                .addPathSegment("search")
                .addQueryParameter("jql", jql)
                .addQueryParameter("startAt", Integer.toString(startAt));
        IssuesPage page = jsonConverter.parseIssuesPage(get(request));
        allIssues.addAll(
            page.issues.stream()
                .map(i -> i.key)
                .map(JiraIssueId::new)
                .collect(Collectors.toList()));
        if (page.total <= startAt + page.maxResults) {
          return Collections.unmodifiableList(allIssues);
        }
        startAt += page.maxResults;
      }
    } catch (NotFoundException e) {
      return Collections.emptyList();
    } catch (InvalidRequestException e) {
      throw toJiraException(e, "retrieving issues (jql=%s)", jql);
    }
  }

  private void setFieldsToFetch(HttpUrl.Builder requestBuilder, Collection<String> fields) {
    requestBuilder.addEncodedQueryParameter("fields", StringUtils.join(fields, ","));
  }

  /**
   * Replaces special characters in the phrase with jira-friendly version.
   *
   * <p>https://confluence.atlassian.com/jiracoreserver073/advanced-searching-861257209.html?_ga=2.65336877.221228506.1597322149-165187147.1593767782#id-__JQLTextFields-Summary
   *
   * @return jql-friendly phrase
   */
  public static String escapeJqlPhrase(String phrase) {
    return phrase.replaceAll("[ ,\\[\\]\\*\\{\\}=\"\\(\\)]", "?");
  }

  @Override
  public String toString() {
    return siteUrl;
  }
}
