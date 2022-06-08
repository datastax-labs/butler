/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.jenkins.JenkinsException.error;
import static java.lang.String.format;

import com.datastax.butler.commons.web.Credentials;
import com.datastax.butler.commons.web.CredentialsException;
import com.datastax.butler.commons.web.OkHttpWebClient;
import com.datastax.butler.commons.web.WebClient;
import com.datastax.butler.commons.web.WebClient.InvalidRequestException;
import com.datastax.butler.commons.web.WebClient.NotFoundException;
import com.google.errorprone.annotations.FormatMethod;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of a Jenkins client.
 *
 * <p>JenkinsClient allows making requests to jenkins rest api. Depending on the configuration it
 * may use / not use basic authentication.
 */
public class JenkinsClient {

  private static final Logger logger = LogManager.getLogger();

  private final String siteUrl;
  private final WebClient webClient;
  private final String credentials;

  /**
   * Creates a new Jenkins client with authentication.
   *
   * @param siteUrl url to Jenkins.
   * @param webClient the {@link WebClient} implementation to use to query the Jenkins server.
   * @param credentials the credentials to use to connect to the Jenkins server.
   */
  public JenkinsClient(String siteUrl, WebClient webClient, Credentials credentials) {
    this.siteUrl = siteUrl;
    this.webClient = webClient;
    this.credentials = okhttp3.Credentials.basic(credentials.login(), credentials.password());
  }

  /**
   * Creates a new Jenkins client without authentication.
   *
   * @param siteUrl url to Jenkins.
   * @param webClient the {@link WebClient} implementation to use to query the Jenkins server.
   */
  public JenkinsClient(String siteUrl, WebClient webClient) {
    this.siteUrl = siteUrl;
    this.webClient = webClient;
    this.credentials = null;
  }

  /** Base for all Jenkins URL. */
  public HttpUrl baseUrl() {
    HttpUrl url = HttpUrl.parse(siteUrl);
    if (url == null) {
      throw JenkinsException.error("Unable to parse url: %s", siteUrl);
    }
    return url;
  }

  private Request makeGetRequest(HttpUrl.Builder urlBuilder) {
    var builder = new Request.Builder().url(urlBuilder.build());
    if (credentials != null) {
      builder.addHeader("Authorization", credentials);
    }
    return builder.get().build();
  }

  private String get(HttpUrl.Builder urlBuilder) throws InvalidRequestException {
    var request = makeGetRequest(urlBuilder);
    try {
      return webClient.request(request);
    } catch (UnknownHostException e) {
      throw JenkinsException.ioError(
          e, "Unknown host %s. Have you enabled the DataStax VPN?", siteUrl);
    } catch (IOException e) {
      throw JenkinsException.ioError(e, "I/O error while requesting %s", request.url());
    } catch (NotFoundException e) {
      logger.warn("Resource {} not found", request.url());
      // do not convert to JenkinsException here because we loose the information about the cause -
      // NotFoundException is frequently handled in a special way
      throw e;
    } catch (InvalidRequestException e) {
      logger.error("Request {} failed with status code {}", request, e.statusCode());
      // do not convert to JenkinsException here because the caller method may want to use a custom
      // error message or handle the failure differently
      throw e;
    }
  }

  // We're building our underlying formatting string dynamically, which error prone is not able
  // to validate. It's legit in that case however, so suppressing warning.
  @SuppressWarnings("FormatStringAnnotation")
  @FormatMethod
  private JenkinsException toJenkinsException(
      InvalidRequestException exc, String requested, Object... args) {
    String msg = format(requested, args);
    switch (exc.statusCode()) {
      case 400:
        throw error(format("Invalid request while %s; this is almost surely a bug", msg));
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

  public JenkinsUrlScheme urlScheme(WorkflowId workflowId) {
    return new JenkinsUrlScheme(baseUrl(), isMultiBranchPipeline(workflowId));
  }

  /**
   * Uses jenkins json api to check if given workflow is a multi-branch pipeline.
   *
   * @param workflowId workflow id (name)
   * @return True if is multi-branch pipeline, false if not.
   */
  public boolean isMultiBranchPipeline(WorkflowId workflowId) {
    return rawWorkflows().isMultiBranch(workflowId.name()).orElse(true);
  }

  /**
   * Queries all the workflows known.
   *
   * @return a list of all the workflows.
   */
  public List<WorkflowId> listWorkflows() {
    return rawWorkflows().get();
  }

  private RawWorkflowListing rawWorkflows() {
    try {
      var url = RawWorkflowListing.getUrl(baseUrl().newBuilder());
      return RawWorkflowListing.parse(get(url));
    } catch (NotFoundException e) {
      return RawWorkflowListing.empty();
    } catch (InvalidRequestException e) {
      throw toJenkinsException(e, "listing workflows");
    }
  }

  /**
   * Queries all the jobs of the provided workflow (if it is a multi-branch pipeline).
   *
   * @param workflow the identifier of the workflow to query
   * @param color an optional color of job to restrict the list to
   * @return a list of the jobs in {@code workflow} or empty list if the workflow does not exists.
   */
  public List<JobId> listJobs(WorkflowId workflow, Optional<String> color) {
    try {
      if (!isMultiBranchPipeline(workflow)) {
        throw new IllegalArgumentException(
            "listJobs() does not apply to simple (not multi-branch) workflows");
      }
      var url = RawJobListing.getUrl(urlScheme(workflow).workflowUrl(workflow).newBuilder());
      return RawJobListing.parse(get(url)).get(workflow, color);
    } catch (NotFoundException e) {
      return Collections.emptyList();
    } catch (InvalidRequestException e) {
      throw toJenkinsException(e, "listing the jobs in %s", workflow);
    }
  }

  /** Return list of buildIDs for all builds in given job. */
  public List<BuildId> getJobBuildIds(JobId jobId) {
    try {
      var jobUrl = urlScheme(jobId.workflow()).jobUrl(jobId);
      var url = RawBuildNumberListing.getUrl(jobUrl.newBuilder());
      return RawBuildNumberListing.parse(get(url)).get(jobId);
    } catch (WebClient.NotFoundException e) {
      return Collections.emptyList();
    } catch (WebClient.InvalidRequestException e) {
      throw toJenkinsException(e, "retrieving job %s", jobId);
    }
  }

  /**
   * Retrieve a Jenkins build.
   *
   * @param buildId the identifier of the build to retrieve.
   * @return an optional with the fetched build if it can be found, or an empty optional otherwise.
   */
  public Optional<RawBuild> getRawBuild(BuildId buildId) {
    var buildUrl = urlScheme(buildId.jobId().workflow()).buildUrl(buildId);
    return getRawBuild(buildUrl);
  }

  /** Retrieves a raw JenkinsBuild from given build url. */
  public Optional<RawBuild> getRawBuild(HttpUrl buildUrl) {
    try {
      var url = RawBuild.getUrl(buildUrl.newBuilder());
      return Optional.of(RawBuild.parse(get(url)));
    } catch (NotFoundException e) {
      return Optional.empty();
    } catch (InvalidRequestException e) {
      throw toJenkinsException(e, "retrieving build from %s", buildUrl);
    }
  }

  /**
   * Retrieve the test report of the provided build.
   *
   * @param testReportUrl full url to the build test report
   * @return an optional with the fetched test report if it can be found, or an empty optional
   *     otherwise.
   */
  public Optional<RawTestReport> getRawTestReport(HttpUrl testReportUrl) {
    try {
      var url = RawTestReport.getUrl(testReportUrl.newBuilder());
      return Optional.of(RawTestReport.parse(get(url)));
    } catch (NotFoundException e) {
      return Optional.empty();
    } catch (InvalidRequestException e) {
      throw toJenkinsException(e, "retrieving test report for %s", testReportUrl);
    }
  }

  public Optional<RawTestReport> getRawTestReport(BuildId buildId) {
    var testReportUrl = urlScheme(buildId.jobId().workflow()).buildTestReportUrl(buildId);
    return getRawTestReport(testReportUrl);
  }

  /**
   * Create JenkinsClient from given url.
   *
   * <p>Example URLs: https://ci-cassandra.apache.org/view/patches/job/Cassandra-devbranch/334/
   *
   * @param url url of the request e.g. build link
   */
  public static JenkinsClient createForUrl(HttpUrl url, Optional<String> pathSegment) {
    String host = url.host();
    var siteBuilder = new HttpUrl.Builder().scheme(url.scheme()).host(host);
    pathSegment.ifPresent(siteBuilder::addPathSegments);
    String site = siteBuilder.build().toString();
    try {
      var credentials = Credentials.readFromNetrcFile(host);
      return new JenkinsClient(site, new OkHttpWebClient(), credentials);
    } catch (CredentialsException ex) {
      logger.info("no credentials found for {}, assuming no-auth jenkins", host);
      return new JenkinsClient(site, new OkHttpWebClient());
    }
  }

  public static JenkinsClient createForUrl(HttpUrl url) {
    return createForUrl(url, Optional.empty());
  }
}
