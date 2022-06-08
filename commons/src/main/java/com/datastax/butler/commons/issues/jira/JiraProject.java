/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.jira;

import static java.lang.String.format;

import com.datastax.butler.commons.StringSanitizer;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.issues.Issue;
import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.issues.IssueTrackingProject;
import com.datastax.butler.commons.issues.content.JiraMarkdown;
import com.datastax.butler.commons.issues.content.Markdown;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jira.client.JiraClient;
import com.datastax.butler.commons.jira.client.JiraException;
import com.datastax.butler.commons.jira.client.JiraJsonConverter;
import com.google.common.annotations.VisibleForTesting;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generic JIRA project.
 *
 * <p>JiraProjects may have different implementations. They can differ in the way they created
 * issues (what fields are set and how).
 */
public class JiraProject implements IssueTrackingProject {

  private static final Logger logger = LogManager.getLogger();

  final JiraJsonConverter jsonConverter;
  final JiraClient jiraClient;
  final String projectKey;

  /**
   * Configuration for the freemarker templating engine. We pass it for each project as we want only
   * single instance to be present in the app. We do not pass template because we want to be able to
   * refresh templates with app running.
   */
  Configuration templateConfiguration;

  public JiraProject(JiraClient jiraClient, String projectKey) {
    this.jiraClient = jiraClient;
    this.projectKey = projectKey;
    this.jsonConverter = new JiraJsonConverter();
  }

  /** Factory method to create jira project for given jira server url and project key. */
  public static JiraProject forKey(String serverUrl, String projectKey) {
    return new JiraProject(JiraClient.create(serverUrl), projectKey);
  }

  public JiraProject withTemplateConfiguration(Configuration config) {
    this.templateConfiguration = config;
    return this;
  }

  public JiraClient jira() {
    return this.jiraClient;
  }

  public String projectKey() {
    return projectKey;
  }

  @Override
  public String projectName() {
    return projectKey;
  }

  @Override
  public IssueLink getLink(IssueId issueId) {
    var link = jiraClient.link(issueId);
    return IssueLink.withClosed(link, isClosed(issueId).orElse(false));
  }

  @Override
  public Issue fetchIssue(IssueId issueId) {
    return fetchJiraIssue(issueId);
  }

  @Override
  public Optional<Boolean> isClosed(IssueId issueId) {
    var issue = fetchJiraIssue(issueId);
    if (issue == null) return Optional.empty();
    if (StringUtils.isBlank(issue.resolution())) return Optional.of(Boolean.FALSE);
    return Optional.of(isResolutionClosed(issue.resolution()));
  }

  boolean isResolutionClosed(String resolution) {
    return Set.of("Fixed", "Done").contains(resolution);
  }

  @Override
  public Issue newIssue() {
    return new JiraIssue(null, "", "");
  }

  @Override
  public IssueLink createIssue(Issue issue, Collection<TestFailure> failures) {
    if (!(issue instanceof JiraIssue)) {
      throw new IllegalArgumentException(
          "Oops! Provided issue is not a jira issue! How did it happen?");
    }
    try {
      String jsonIssueBody = issueToJson((JiraIssue) issue, failures);
      if (logger.isDebugEnabled()) {
        logger.debug("Attempting to create new jira issue: {}", jsonIssueBody);
      }
      IssueId newIssueId = jiraClient.createIssue(jsonIssueBody);
      return this.getLink(newIssueId);
    } catch (IOException | TemplateException e) {
      throw new JiraException("Error creating new jira issue in project " + projectKey(), e);
    }
  }

  @VisibleForTesting
  public String issueToJson(JiraIssue jiraIssue, Collection<TestFailure> failures)
      throws IOException, TemplateException {
    Template template = issueTemplate();
    Object issueData = prepareIssueData(jiraIssue, failures);
    StringWriter out = new StringWriter();
    template.process(issueData, out);
    return out.toString();
  }

  /**
   * Translate JiraIssue into DataModel object to be process by issueTemplate().
   *
   * <p>This method should create object that will have all the data required by the template. Basic
   * implementation will match basic template and will provide minimal set of fields. If more
   * detailed jira needs to be reported then a project-specific template should be created together
   * with a project-specific implemenation of this method.
   *
   * @param jiraIssue content of the jira to be created
   * @return object with data, e.g. Map[String, Object] (see freemarked documentation).
   */
  protected Map<String, Object> prepareIssueData(
      JiraIssue jiraIssue, Collection<TestFailure> failures) {
    Map<String, Object> res = new HashMap<>();
    res.put("projectKey", this.projectKey);
    res.put("summary", jiraIssue.title());
    res.put("body", jiraIssue.body());
    res.put("issueType", "Task");
    return res;
  }

  public String issueTemplateName() {
    return "jira-issue.ftl";
  }

  private Template issueTemplate() throws IOException {
    if (templateConfiguration == null) {
      throw new IllegalArgumentException(
          "Configuration error: template configuration not set for project " + this);
    }
    return templateConfiguration.getTemplate(issueTemplateName());
  }

  @Override
  public Set<IssueLink> searchIssuesForTest(TestName test, boolean onlyOpen) {
    var testNameJql = testNameSearchJQL(test);
    var projectJql = format("project in (%s)", this.projectKey());
    var jql = projectJql + " AND " + testNameJql;
    if (onlyOpen) {
      jql = jql + " AND (resolution != Fixed OR resolution is EMPTY)";
    }
    return jiraClient.search(jql).stream().map(this::getLink).collect(Collectors.toSet());
  }

  @Override
  public Markdown markdown() {
    return new JiraMarkdown();
  }

  private JiraIssue fetchJiraIssue(IssueId issueId) {
    var json = jiraClient.fetchIssue(issueId, Collections.emptyList());
    if (json.isEmpty()) {
      logger.warn(
          "JIRA issue {} was not found and cannot be fetched",
          StringSanitizer.sanitize(issueId.toString()));
      return null;
    }
    return jsonConverter.parseIssue(json.get()).toIssue();
  }

  @Override
  public String toString() {
    return projectKey;
  }

  /**
   * Build JQL to search for jira issues e.g. matching some test name.
   *
   * @param conditions additional conditions
   * @return full jql condition string
   */
  public String issueSearchJQL(String conditions) {
    StringBuilder b = new StringBuilder();
    b.append(String.format("project in (%s)", projectKey));
    b.append(" AND labels=butler");
    if (!conditions.isBlank()) b.append(" AND ").append(conditions);
    return b.toString();
  }

  /** Build JQL to search for issue potentially related to give test name. */
  protected String testNameSearchJQL(TestName testName) {
    String searchPhrase = JiraClient.escapeJqlPhrase(testName.fullName());
    return format("(summary ~ '%s' OR description ~ '%s')", searchPhrase, searchPhrase);
  }
}
