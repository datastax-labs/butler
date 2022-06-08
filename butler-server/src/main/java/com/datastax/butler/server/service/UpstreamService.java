/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service;

import static java.lang.String.format;

import com.datastax.butler.api.commons.Msg;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.server.db.StoredTestLinkedIssue;
import com.datastax.butler.server.db.TestLinkedIssuesDb;
import com.datastax.butler.server.db.UpstreamFailuresDb;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.issues.IssueContent;
import com.datastax.butler.server.service.issues.IssueTrackersService;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UpstreamService {
  private static final Logger logger = LogManager.getLogger();

  @Value("${butlerAppUrl}")
  private String butlerAppUrl;

  private final UpstreamWorflowsDb upstreamWorflowsDb;
  private final UpstreamFailuresDb upstreamFailuresDb;
  private final TestLinkedIssuesDb testLinkedIssuesDb;
  private final IssueTrackersService issuesService;

  /** Creates the service (Auto-wired by Spring). */
  @Autowired
  public UpstreamService(
      UpstreamWorflowsDb upstreamWorflowsDb,
      UpstreamFailuresDb upstreamFailuresDb,
      TestLinkedIssuesDb testLinkedIssuesDb,
      IssueTrackersService issuesService) {
    this.upstreamWorflowsDb = upstreamWorflowsDb;
    this.upstreamFailuresDb = upstreamFailuresDb;
    this.testLinkedIssuesDb = testLinkedIssuesDb;
    this.issuesService = issuesService;
  }

  /** Get links to all issues linked for particular test. */
  public List<IssueLink> getLinkedIssues(TestName testName) {
    return testLinkedIssuesDb.linkedIssues(testName).stream()
        .map(StoredTestLinkedIssue::linkedIssue)
        .map(IssueId::fromString)
        .map(issuesService::issueLink)
        .collect(Collectors.toList());
  }

  /** Whether the provided job is an upstream dev job. */
  public boolean isUpstreamDevJob(JobId jobId) {
    var workflow = upstreamWorflowsDb.getWorkflow(jobId.workflow().name());
    if (workflow.isEmpty()) return false;
    var jobCategory = workflow.get().jobCategory(jobId.jobName());
    return jobCategory == JobId.Category.UPSTREAM;
  }

  /**
   * Link tests to an existing jira ticket.
   *
   * <p>This will create any missing "upstream_failures" as necessary
   *
   * @param names A list of TestNames to link to the ticket
   * @param ticketId A String representation of the ticket id
   * @return A link to the issue
   */
  public Msg<IssueLink> linkFailuresToJira(List<TestName> names, String ticketId) {
    var issueId = IssueId.fromString(ticketId);
    var issueProject = issuesService.getProject(issueId);
    var issue = issueProject.fetchIssue(issueId);
    if (issue == null) {
      throw new IllegalArgumentException(
          "Unable to find issue " + ticketId + " in project " + issueProject);
    }
    names.forEach(name -> testLinkedIssuesDb.linkIssueToTest(name, issueId));
    return new Msg<>(issueProject.getLink(issueId), issue.toDetailedString());
  }

  /**
   * Creates a ticket for the provided test.
   *
   * @param names the names of the test for which to create a JIRA ticket.
   * @return the result of the creation, indicating the ticket name in particular.
   */
  public Msg<IssueLink> reportFailureToJira(String jiraProjectKey, List<TestName> names) {
    var project = issuesService.getProject(jiraProjectKey);
    var markdown = project.markdown();
    var newIssue = project.newIssue();

    var issueContent = new IssueContent(project.markdown());
    var testFailures =
        names.stream()
            .map(upstreamFailuresDb::getFailureDetails)
            .flatMap(Optional::stream)
            .collect(Collectors.toList());
    if (testFailures.isEmpty()) {
      throw new IllegalArgumentException(
          "No test failures found when creating tickets for: " + StringUtils.join(names, ","));
    }
    // add information about test failures to the content
    for (TestFailure testFailure : testFailures) {
      var historyLink = this.butlerHistoryLink(testFailure.test(), testFailure);
      issueContent.addParagraph(format("History view: %s", markdown.link("butler", historyLink)));
      issueContent.addParagraph(issueContent.paragraphForFailure(testFailure.test(), testFailure));
    }
    // add information about who created the ticket
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    issueContent.addParagraph(format("Created by butler user {{%s}}", auth.getPrincipal()));
    newIssue.setTitle(issueContent.title(names));
    newIssue.setBody(issueContent.body());
    var issueLink = project.createIssue(newIssue, testFailures);
    logger.info("Ticket {} created : {}", issueLink.id(), issueLink.url());

    // at the very end we need to update database and link newly created issue to failures
    names.forEach(name -> testLinkedIssuesDb.linkIssueToTest(name, issueLink.id()));

    return new Msg<>(issueLink, newIssue.toDetailedString());
  }

  /** Searching for open issues matching testNames in given issue tracking project. */
  public Set<IssueLink> searchOpenIssuesForTests(
      String projectName, Collection<TestName> testNames) {
    // see if we can find some existing jiras for given test names
    // in such case we will throw
    var projectsToSearch = List.of(projectName);
    return testNames.stream()
        .map(x -> issuesService.searchOpenIssuesForTest(projectsToSearch, x))
        .filter(y -> !y.isEmpty())
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  private String applicationUrl(String path) {
    return butlerAppUrl.trim() + "/#/" + path;
  }

  private String butlerHistoryLink(TestName name, TestFailure failureDetails) {
    String workflowName = failureDetails.lastRunWorkflowName().orElse("workflow-not-known");
    String testPath = name.className() + "/" + name.testName();
    if (name.hasPath()) {
      testPath = name.path() + "/" + testPath;
    }
    String uri = format("ci/upstream/workflow/%s/failure/%s", workflowName, testPath);
    return applicationUrl(uri);
  }
}
