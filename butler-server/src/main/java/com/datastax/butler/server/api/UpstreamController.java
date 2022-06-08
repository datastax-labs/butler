/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import com.datastax.butler.api.commons.ChartData;
import com.datastax.butler.api.commons.ChartData.ChartDataBuilder;
import com.datastax.butler.api.commons.ChartData.Point;
import com.datastax.butler.api.commons.Msg;
import com.datastax.butler.api.upstream.UpstreamTrends;
import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.BranchVersion;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.dev.UpstreamFailures;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.StoredBuild;
import com.datastax.butler.server.db.TestNamesDb;
import com.datastax.butler.server.db.UpstreamFailuresDb;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.UpstreamService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.security.RolesAllowed;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Controller for REST API related to upstream branches. */
@RestController
@RequestMapping("/api/upstream")
public class UpstreamController {
  private static final Logger logger = LogManager.getLogger();
  private final JobsDb jobsDb;
  private final BuildsDb buildsDb;
  private final UpstreamWorflowsDb upstreamWorflowsDb;
  private final UpstreamFailuresDb upstreamFailuresDb;
  private final UpstreamService upstreamService;
  private final TestNamesDb testNamesDb;

  @Value("${butlerTrendDays: 30}")
  private int trendDaysToInclude;

  /** Creates the controller (Autowired by Spring). */
  @Autowired
  public UpstreamController(
      JobsDb jobsDb,
      BuildsDb buildsDb,
      UpstreamWorflowsDb upstreamWorflowsDb,
      UpstreamFailuresDb upstreamFailuresDb,
      UpstreamService upstreamService,
      TestNamesDb testNamesDb) {
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.upstreamWorflowsDb = upstreamWorflowsDb;
    this.upstreamFailuresDb = upstreamFailuresDb;
    this.upstreamService = upstreamService;
    this.testNamesDb = testNamesDb;
  }

  /** The list of Jenkins workflow that contains upstream branches. */
  @GetMapping("/workflows")
  public Set<WorkflowId> upstreamWorkflows() {
    return upstreamWorflowsDb.upstreamWorkflows().stream()
        .map(Workflow::workflowId)
        .collect(Collectors.toSet());
  }

  /** The list of Jenkins workflow that contains upstream branches. */
  @GetMapping("/workflows/all")
  public List<WorkflowId> allWorkflows() {
    return upstreamWorflowsDb.allWorkflows().stream()
        .map(Workflow::workflowId)
        .distinct()
        .sorted(WorkflowId::compareTo)
        .collect(Collectors.toList());
  }

  /** Sets the Jenkins workflows that contains upstream branches. */
  @PostMapping("/workflows/set")
  @RolesAllowed("ROLE_ADMIN")
  public void setWorkflows(@RequestBody Set<WorkflowId> workflows) {
    upstreamWorflowsDb.update(workflows);
  }

  /**
   * Provides chart data regarding the number of tests run and failures.
   *
   * @param requestedVersions the versions for which to return data on. This is optional and will
   *     default to the actively maintained upstream versions.
   * @return the requested data.
   */
  @GetMapping("/trends")
  public UpstreamTrends upstreamTrends(
      @RequestParam("versions") Optional<List<String>> requestedVersions) {
    List<UpstreamTrends.WorkflowVersionData> trendData = new ArrayList<>();
    // for all known upstream workflows
    for (Workflow workflow : upstreamWorflowsDb.upstreamWorkflows()) {
      var versions = requestedVersions.orElse(List.copyOf(workflow.upstreamBranches()));
      // for all requested versions OR upstream versions in each workflow
      for (String v : versions) {
        var jobId = workflow.workflowId().job(Branch.fromString(v));
        jobsDb
            .dbIdIfExists(jobId)
            .ifPresent(
                jobDbId ->
                    trendData.add(
                        new UpstreamTrends.WorkflowVersionData(
                            BranchVersion.fromString(v), workflow.name(), makeData(jobDbId))));
      }
    }
    return UpstreamTrends.build(trendData);
  }

  /**
   * Makes data for the job for the last ${butler.trend.days} days.
   *
   * @param jobDbId the job id to fetch
   */
  private UpstreamTrends.Data makeData(long jobDbId) {
    // plot data
    ChartDataBuilder failures = ChartData.builder();
    ChartDataBuilder runs = ChartData.builder();
    ChartDataBuilder durations = ChartData.builder();
    Instant since = Instant.now().minus(trendDaysToInclude, ChronoUnit.DAYS);
    List<StoredBuild> builds = buildsDb.usableSince(jobDbId, since);
    for (StoredBuild build : builds) {
      long timestamp = build.startTime().toEpochMilli();
      String buildStr = "#" + build.buildNumber();
      failures.point(new Point(timestamp, build.failedTests(), buildStr));
      runs.point(new Point(timestamp, build.ranTests(), buildStr));
      long durationMinutes = build.durationMs() / 1000 / 60;
      durations.point(new Point(timestamp, durationMinutes, buildStr));
    }
    // calculate summary
    long numBuilds = builds.size();
    long numBuildsFailed = builds.stream().filter(b -> b.failedTests() > 0).count();
    long numBuildsBroken = builds.stream().filter(b -> b.status().isBroken()).count();
    double avgNumFailuresPerBuild =
        builds.stream().mapToDouble(StoredBuild::failedTests).average().orElse(Double.NaN);
    double avgBuildDurationInMin =
        builds.stream()
            .mapToDouble(StoredBuild::durationMs)
            .map(d -> d / 1000.0 / 60.0)
            .average()
            .orElse(Double.NaN);
    long numRecent = Long.min(16, numBuilds);
    long skipToRecent = numBuilds > numRecent ? numBuilds - numRecent : 0;
    long numRecentFailed =
        builds.stream().skip(skipToRecent).filter(b -> b.failedTests() > 0).count();
    long numRecentBroken =
        builds.stream().skip(skipToRecent).filter(b -> b.status().isBroken()).count();
    double avgRecentDurationInMin =
        builds.stream()
            .skip(skipToRecent)
            .mapToDouble(StoredBuild::durationMs)
            .map(d -> d / 1000.0 / 60.0)
            .average()
            .orElse(Double.NaN);
    double p90AllMin =
        builds.stream()
            .mapToDouble(StoredBuild::durationMs)
            .map(d -> d / 1000.0 / 60.0)
            .sorted()
            .limit(Math.round(0.9 * numBuilds))
            .max()
            .orElse(Double.NaN);
    double p90RecentMin =
        builds.stream()
            .skip(skipToRecent)
            .mapToDouble(StoredBuild::durationMs)
            .map(d -> d / 1000.0 / 60.0)
            .sorted()
            .limit(Math.round(0.9 * numBuilds))
            .max()
            .orElse(Double.NaN);

    return new UpstreamTrends.Data(
        failures.build(),
        runs.build(),
        durations.build(),
        numBuilds,
        numBuildsFailed,
        numBuildsBroken,
        numRecent,
        numRecentFailed,
        numRecentBroken,
        avgNumFailuresPerBuild,
        avgBuildDurationInMin,
        avgRecentDurationInMin,
        p90AllMin,
        p90RecentMin);
  }

  /**
   * Return all failures for a single class.
   *
   * @param className the class to query
   * @return A list of failures for the class
   */
  @GetMapping("/failures/{className}")
  public UpstreamFailures upstreamFailures(@PathVariable String className) {
    return upstreamFailuresDb.getFailuresDetails(className);
  }

  /**
   * Return failures for a specific class within a specific job.
   *
   * @param workflowName The workflow of the job
   * @param jobName The job name
   * @param className The class name of the test
   * @param testName The actual test name
   */
  @GetMapping("/workflow/{workflowName}/job/{jobName}/failure/{className}/{testName}")
  public TestFailure getFailureDashboardForJob(
      @PathVariable String workflowName,
      @PathVariable String jobName,
      @PathVariable String className,
      @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(className, testName));
    return upstreamFailuresDb
        .getFailuresForJob(workflowName, jobName, Optional.of(test))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format(
                        "Unable to find failure details for %s in %s/%s",
                        test, workflowName, jobName)));
  }

  /**
   * Return "interesting" failures for a job, where "interesting" means it has a failure we know
   * about.
   *
   * @param workflowName the workflow name
   * @param jobName the job name
   * @return the list of failures
   */
  @GetMapping("/interesting/{workflowName}/{jobName}")
  public UpstreamFailures interestingTests(
      @PathVariable String workflowName, @PathVariable String jobName) {
    return upstreamFailuresDb.findInterestingFailures(workflowName, jobName);
  }

  /**
   * Compare the specific job to it's upstream job.
   *
   * @param workflowName the workflow of the branch job
   * @param job the job name
   * @return A list of a list of failures
   */
  @GetMapping("/compare/{workflowName}/{job}")
  public List<UpstreamFailures> compareJobs(
      @PathVariable String workflowName,
      @PathVariable String job,
      @RequestParam Optional<Integer> numBuilds) {
    // check if we know the workflow
    var workflow = upstreamWorflowsDb.getWorkflow(workflowName);
    if (workflow.isEmpty()) {
      throw badRequest("Workflow " + workflowName + " is not configured");
    }
    return upstreamFailuresDb
        .compareJobToUpstream(workflow.get(), job, numBuilds)
        .map(this::prepareCompareData)
        .orElseThrow(
            () ->
                badRequest(
                    String.format(
                        "Unable to find upstream for %s/%s. "
                            + "Possibly workflow unknown or no builds found in butler database. "
                            + "Fetch should help.",
                        workflow.get().name(), job)));
  }

  /**
   * Compare a job to a specific other job. This is useful if the upstream job can't be determined.
   *
   * @param workflowA Workflow of the branch job
   * @param jobA name of the branch job
   * @param workflowB workflow of the _upstream_ job
   * @param jobB name of the upstream job
   * @return A list of a list of failures
   */
  @GetMapping("/compare/{workflowA}/{jobA}/to/{workflowB}/{jobB}")
  public List<UpstreamFailures> compareJobs(
      @PathVariable String workflowA,
      @PathVariable String jobA,
      @PathVariable String workflowB,
      @PathVariable String jobB,
      @RequestParam Optional<Integer> numBuilds) {
    return prepareCompareData(
        upstreamFailuresDb.compareJobs(workflowA, jobA, workflowB, jobB, numBuilds));
  }

  /**
   * Returns all the failure details for a single test.
   *
   * @param className the class name of the test
   * @param testName the test name
   * @return the failure details for the test
   */
  @GetMapping("/failure/{className}/{testName}")
  public TestFailure getFailureDashboard(
      @PathVariable String className, @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(className, testName));
    return upstreamWorflowsDb.upstreamWorkflows().stream()
        .map(
            workflow ->
                upstreamFailuresDb.findAllWorkflowResultsForTest(workflow.workflowId(), test))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    String.format("Unable to find failure details for %s", test)));
  }

  /**
   * Returns all the failure details for a single test in a given workflow.
   *
   * @param className the class name of the test (without package)
   * @param testName the test name
   * @return the failure details for the test
   */
  @GetMapping("/workflow/{workflowName}/failure/{className}/{testName}")
  public TestFailure getFailureDashboardForWorkload(
      @PathVariable String workflowName,
      @PathVariable String className,
      @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(className, testName));
    WorkflowId workflowId = WorkflowId.of(workflowName);
    return getFailureDashboardForTestInWorkload(workflowId, test);
  }

  private TestName extractTestNameOrThrow(List<TestName> tests) {
    if (tests.isEmpty()) {
      throw badRequest("No tests found for given search criteria.");
    }
    if (tests.size() > 1) {
      logger.warn(
          "Found {} tests when expected exactly 1: {}",
          tests.size(),
          tests.stream().map(TestName::fullName).collect(Collectors.joining(", ")));
    }
    // we will return first one assuming it is properly sorted by with 0 being recently inserted one
    return tests.get(0);
  }

  /**
   * Returns all the failure details for a single test in a given workflow.
   *
   * @param workflowName workflow name from which we want failures to see
   * @param testPath the package name of the test
   * @param className the class name of the test
   * @param testName the test name
   * @return the failure details for the test
   */
  @GetMapping("/workflow/{workflowName}/failure/{testPath}/{className}/{testName}")
  public TestFailure getPackagedTestFailureForWorkload(
      @PathVariable String workflowName,
      @PathVariable String testPath,
      @PathVariable String className,
      @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(testPath, className, testName));
    WorkflowId workflowId = WorkflowId.of(workflowName);
    return getFailureDashboardForTestInWorkload(workflowId, test);
  }

  /**
   * Returns history of all failed test runs for given test (in all workflows).
   *
   * @param testPath the package name of the test
   * @param className the class name of the test
   * @param testName the test name
   * @return Failure objects containing organized list of all test run failures
   */
  @GetMapping("/failed_runs/{testPath}/{className}/{testName}")
  public TestFailure getFailedTestRuns(
      @PathVariable String testPath,
      @PathVariable String className,
      @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(testPath, className, testName));
    Optional<TestFailure> failure = upstreamFailuresDb.findAllFailuresForTest(test);
    return failure.orElseThrow(
        () ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, String.format("Unable to find failed runs for %s", test)));
  }

  /**
   * Returns history of all failed test runs for given test (in all workflows).
   *
   * @param className the class name of the test
   * @param testName the test name
   * @return Failure objects containing organized list of all test run failures
   */
  @GetMapping("/failed_runs/{className}/{testName}")
  public TestFailure getFailedTestRunsForTestWithoutPath(
      @PathVariable String className, @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(className, testName));
    Optional<TestFailure> failure = upstreamFailuresDb.findAllFailuresForTest(test);
    return failure.orElseThrow(
        () ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, String.format("Unable to find failed runs for %s", test)));
  }

  private TestFailure getFailureDashboardForTestInWorkload(WorkflowId workflowId, TestName test) {
    Optional<TestFailure> failure =
        upstreamFailuresDb.findAllWorkflowResultsForTest(workflowId, test);
    return failure.orElseThrow(
        () ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                String.format("Unable to find failure details for %s in %s", test, workflowId)));
  }

  /** Return main jira project name for given workflow. */
  @GetMapping("/workflow/{workflowName}/jira/main")
  public String getWorkflowMainJiraProject(@PathVariable String workflowName) {
    try {
      var workflow = getWorkflowOrThrow(workflowName);
      return mainJiraProjectKey(workflow);
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex.getMessage());
    }
  }

  /**
   * Creates a JIRA ticket for the provided test, creating an upstream_failure for it if necessary.
   *
   * <p>When "force" is false (default) it will try and search for matching existing issues in the
   * issue tracked and instead of linking it will suggest those. When force is "true" it will not
   * search and just create the ticket.
   *
   * @return a result indicating the name of the ticket created and an additional message with
   *     details (the latter, mostly for debugging).
   */
  @PostMapping("/failures/report")
  public Msg<IssueLink> createFailureTicket(
      @RequestBody List<TestName> testNames,
      @RequestParam String workflow,
      @RequestParam(required = false, defaultValue = "false") boolean force) {
    try {
      var workflowDef = getWorkflowOrThrow(workflow);
      var jiraKey = mainJiraProjectKey(workflowDef);
      if (!force) {
        var existingIssues = upstreamService.searchOpenIssuesForTests(jiraKey, testNames);
        if (!existingIssues.isEmpty()) {
          throw new IllegalArgumentException(
              "Found tickets that match selected tests, please link or use force: "
                  + StringUtils.join(existingIssues, ','));
        }
      }
      return upstreamService.reportFailureToJira(jiraKey, testNames);
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex.getMessage());
    }
  }

  /**
   * Return list of issues (jira tickets, gh issues) that could match any of the given test names.
   *
   * <p>Use case: check if there are any tickets already created before reporting a new one.
   */
  @GetMapping("/failures/search-issues")
  public List<IssueLink> searchIssuesForTests(
      @RequestBody List<TestName> testNames, @RequestParam String workflow) {
    try {
      var workflowDef = getWorkflowOrThrow(workflow);
      var jiraKey = mainJiraProjectKey(workflowDef);
      return new ArrayList<>(upstreamService.searchOpenIssuesForTests(jiraKey, testNames));
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex.getMessage());
    }
  }

  private String mainJiraProjectKey(Workflow workflowDef) {
    return workflowDef
        .mainJiraProject()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No main jira project defined for workflow " + workflowDef.name()));
  }

  /**
   * Link an existing jira ticket to the provided test(s), creating upstream failures if necessary.
   *
   * @param testNames the names of the tickets to link
   * @param ticket the actual ticket id (eg: PRJ-123) to link to
   * @return a result indicating the ticket created, and it's details
   */
  @PostMapping("/failures/link")
  public Msg<IssueLink> linkFailuresToTicket(
      @RequestBody List<TestName> testNames,
      @RequestParam String ticket,
      @RequestParam String workflow) {
    try {
      var workflowDef = getWorkflowOrThrow(workflow);
      var issueProject = IssueId.fromString(ticket).projectName();
      var projectAllowed = workflowDef.allJiraProjects().contains(issueProject);
      if (!projectAllowed)
        throw new IllegalArgumentException(
            String.format(
                "issue tracking project %s is not supported for workflow %s",
                issueProject, workflow));
      return upstreamService.linkFailuresToJira(testNames, ticket);
    } catch (IllegalArgumentException ex) {
      throw badRequest(ex.getMessage());
    }
  }

  /**
   * Return a list of all linked issues (jira,gh) for given test name.
   *
   * @param className the tests class name
   * @param testName the name of the test
   * @return a list of StoredUpstreamFailures
   */
  @GetMapping("/failures/linked_issues/{testPath}/{className}/{testName}")
  public List<IssueLink> getLinkedIssues(
      @PathVariable String testPath,
      @PathVariable String className,
      @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(testPath, className, testName));
    return upstreamService.getLinkedIssues(test);
  }

  /**
   * Return a list of all linked issues (jira,gh) for given test name.
   *
   * @param className the tests class name
   * @param testName the name of the test
   * @return a list of StoredUpstreamFailures
   */
  @GetMapping("/failures/linked_issues/{className}/{testName}")
  public List<IssueLink> getLinkedIssuesForTestWithoutPath(
      @PathVariable String className, @PathVariable String testName) {
    TestName test = extractTestNameOrThrow(testNamesDb.find(className, testName));
    return upstreamService.getLinkedIssues(test);
  }

  private ResponseStatusException badRequest(String msg) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
  }

  private Workflow getWorkflowOrThrow(String workflow) {
    return upstreamWorflowsDb
        .getWorkflow(workflow)
        .orElseThrow(
            () -> new IllegalArgumentException("Workflow " + workflow + " is not defined."));
  }

  private List<UpstreamFailures> prepareCompareData(List<UpstreamFailures> data) {
    data.forEach(
        uf -> uf.failures().forEach(failure -> failure.failureDetails().clearTestRunsOutput()));
    return data;
  }
}
