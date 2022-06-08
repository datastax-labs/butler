/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.FailureDetails;
import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.dev.UpstreamFailures;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.service.issues.IssueTrackersService;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Handles querying for so called "UpstreamFailures" so basically making complicated SQL query to
 * retrieve all data that needs to be then reported by various APIs.
 */
@Repository
public class UpstreamFailuresDb extends DbTableService {

  private final TestNamesDb testNamesDb;
  private final JobsDb jobsDb;
  private final BuildsDb buildsDb;
  private final TestRunsDb testRunsDb;
  private final UpstreamWorflowsDb upstreamWorkflowsDb;
  private final TestLinkedIssuesDb testLinkedIssuesDb;
  private final IssueTrackersService issuesService;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public UpstreamFailuresDb(
      NamedParameterJdbcTemplate template,
      TestNamesDb testNamesDb,
      JobsDb jobsDb,
      BuildsDb buildsDb,
      TestRunsDb testRunsDb,
      UpstreamWorflowsDb upstreamWorkflowsDb,
      TestLinkedIssuesDb testLinkedIssuesDb,
      IssueTrackersService jiraService) {
    super(template, TestRunsDb.TABLE);
    this.testNamesDb = testNamesDb;
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.testRunsDb = testRunsDb;
    this.upstreamWorkflowsDb = upstreamWorkflowsDb;
    this.testLinkedIssuesDb = testLinkedIssuesDb;
    this.issuesService = jiraService;
  }

  /**
   * Returns detailed failure information from an arbitrary build.
   *
   * @param workflowName the name of the workflow
   * @param jobName the name of the job
   * @return an UpstreamFailure
   */
  public Optional<TestFailure> getFailuresForJob(
      String workflowName, String jobName, Optional<TestName> testName) {

    QueryParameters qp = makeFailureDetailsQueryForJob(workflowName, jobName, testName);
    return template.query(qp.query(), qp.source(), this::extractUpstreamFailureDetails);
  }

  /**
   * Compare the specified job to it's upstream job.
   *
   * <p>Expected behavior depends on the workflow and the job.
   *
   * <p>If the job is upstream job then - it will return history (not comparison) if comparable
   * workflows are not defined for workflow or it will return comparison vs best-mach among
   * comparable workflows;
   *
   * <p>If the job is not an upstream job it will return comparison vs best-match among defined
   * comparable workflows; or comparison vs best-match upstream-branch build in the same workflow;
   *
   * @param workflow the workflow name
   * @param job the job name
   * @return a list of UpstreamFailure objects where the first is this job, and the 2nd is upstream
   */
  public Optional<List<UpstreamFailures>> compareJobToUpstream(
      Workflow workflow, String job, Optional<Integer> numBuilds) {
    var workflowsToCompare = workflow.workflowsToCompareBuildWith(job);
    if (workflowsToCompare.isEmpty()) {
      logger.info("No comparable workflows / CI build {}/{}, returning history", workflow, job);
      return Optional.of(jobFailuresHistory(workflow, job, numBuilds));
    } else {
      logger.info("{}/{} is comparable with workflows: {}", workflow, job, workflowsToCompare);
      return compareJobVsBestMatchInComparableWorkflows(
          workflow, job, numBuilds, workflowsToCompare);
    }
  }

  /** Compare given job vs best-matching job from provided list of comparable workflows. */
  private Optional<List<UpstreamFailures>> compareJobVsBestMatchInComparableWorkflows(
      Workflow workflow, String job, Optional<Integer> numBuilds, Set<String> comparableWorkflows) {
    // find best job to compare against
    var upstreamJob = matchUpstreamJobFor(workflow.name(), job, comparableWorkflows);
    if (upstreamJob.isPresent()) {
      var upstreamWorkflow = upstreamJob.get().workflow().name();
      var upstreamVersion = upstreamJob.get().jobName().toString();
      logger.info(
          "Matched upstream {}/{} job for {}/{}", upstreamWorkflow, upstreamJob, workflow, job);
      return Optional.of(
          compareJobs(workflow.name(), job, upstreamWorkflow, upstreamVersion, numBuilds));
    } else {
      // upstream job not found -> showing history
      logger.info("Cannot match upstream job for {}/{}, returning history", workflow, job);
      return Optional.of(jobFailuresHistory(workflow, job, numBuilds));
    }
  }

  /**
   * Guess upstream version, if it is not possible to extract from job name.
   *
   * <p>Algorithm: 1. find the build for this workflow and job, get number of test run -> N 2. for
   * every upstream builds get number of test run => B = {b} 3. select such b in B that
   * |num_test_run(b) - N| is minimal
   *
   * @param workflowName name of the registered workflow
   * @param job job name e.g. PR-11223
   * @param comparableWorkflows workflows to compare against
   * @return string with upstream version or null if it cannot be guessed
   */
  private Optional<JobId> matchUpstreamJobFor(
      String workflowName, String job, Set<String> comparableWorkflows) {
    var jobId = new JobId(WorkflowId.of(workflowName), Branch.fromString(job));
    // if no workflows provided -> return empty to build history
    if (comparableWorkflows.isEmpty()) {
      logger.info("No comparable workflows for {}: returning null as matched upstream job", jobId);
      return Optional.empty();
    }
    // calculate max number of test runs in compared job
    int limitBuildsTo = 5;
    var jobTestsN = this.maxTestRunsForJob(jobId, limitBuildsTo);
    if (jobTestsN.isEmpty()) return Optional.empty();
    // get list of upstream workflows x maintained branches => jobs
    var upstreamWorkflows =
        comparableWorkflows.stream()
            .map(upstreamWorkflowsDb::getWorkflow)
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
    // if no upstreamWorkflows => something is wrong, lets return history
    if (comparableWorkflows.isEmpty()) {
      String requestedWfs = StringUtils.join(comparableWorkflows, ",");
      logger.warn(
          "No upstream workflow defs for {}: returning null as matched upstream job."
              + "Requested workflows: {}",
          jobId,
          requestedWfs);
      return Optional.empty();
    }
    // find potentially matching upstream jobs
    List<JobId> upstreamJobs = Lists.newArrayList();
    for (Workflow workflow : upstreamWorkflows) {
      var matchingBranches = workflow.upstreamBranchesForBranch(job);
      upstreamJobs.addAll(
          jobsDb.getByWorkflow(workflow.workflowId()).stream()
              .filter(x -> workflow.jobCategory(x.jobName().toString()) == JobId.Category.UPSTREAM)
              .filter(x -> matchingBranches.contains(x.jobName().toString()))
              .collect(Collectors.toSet()));
    }
    // select the best one based on |n_tests(b) - n_tests(this)|
    // note: it could be done in a functional way, but with debug logging it would look bad
    Optional<JobId> bestMatch = Optional.empty();
    long bestScore = Long.MAX_VALUE;
    for (JobId j : upstreamJobs) {
      var upstreamTestsN = maxTestRunsForJob(j, limitBuildsTo).orElse(Long.MAX_VALUE);
      long nTestsOnlyOnBranch = Math.max(jobTestsN.get() - upstreamTestsN, 0);
      long nTestsOnlyOnUpstream = Math.max(upstreamTestsN - jobTestsN.get(), 0);
      long score = 4 * nTestsOnlyOnBranch + nTestsOnlyOnUpstream;
      logger.info(
          "Score of {} is {} with {} tests run vs {} for goal.",
          j,
          score,
          upstreamTestsN,
          jobTestsN.get());
      if (score <= bestScore) {
        bestScore = score;
        bestMatch = Optional.of(j);
      }
    }
    logger.info("Best upstream match for {} was {} with score of {}", jobId, bestMatch, bestScore);
    return bestMatch;
  }

  /**
   * Return max number of test run for given jobId in recent N builds.
   *
   * @param jobId job id
   * @param limitBuilds N of builds
   * @return max number of test run or empty if job not found or no builds in db
   */
  private Optional<Long> maxTestRunsForJob(JobId jobId, int limitBuilds) {
    var jobDbId = jobsDb.dbIdIfExists(jobId);
    if (jobDbId.isEmpty()) {
      logger.warn("no job found in the db for {}", jobId);
      return Optional.empty();
    }
    var jobTestN =
        buildsDb.recentUsableOf(jobDbId.getAsLong(), limitBuilds).stream()
            .map(StoredBuild::ranTests)
            .max(Long::compare);
    if (jobTestN.isEmpty()) {
      logger.warn("no usable builds found in the db for {}", jobId);
      return Optional.empty();
    }
    return jobTestN;
  }

  private int numBuildsToCompare(JobId branch, JobId upstream) {
    boolean comparison = !branch.equals(upstream);
    return comparison ? 32 : 16;
  }

  /**
   * History of failures on the specific job.
   *
   * @param workflow workflow
   * @param jobName job name, e.g. branch name
   * @param numBuilds number of builds to compare, if <=0 then defaults are used
   * @return upstream failures
   */
  public List<UpstreamFailures> jobFailuresHistory(
      Workflow workflow, String jobName, Optional<Integer> numBuilds) {
    return compareJobs(workflow.name(), jobName, workflow.name(), jobName, numBuilds);
  }

  /**
   * Compare the specific jobs.
   *
   * @param workflowName the branch job workflow
   * @param jobName the branch job name
   * @param upstreamWorkflow the upstream workflow
   * @param upstreamJob the upstream job name
   * @param numBuilds number of builds to compare, if <=0 then defaults are used
   * @return a list of UpstreamFailures
   */
  public List<UpstreamFailures> compareJobs(
      String workflowName,
      String jobName,
      String upstreamWorkflow,
      String upstreamJob,
      Optional<Integer> numBuilds) {
    JobId branch = WorkflowId.of(workflowName).job(Branch.fromString(jobName));
    JobId upstream = WorkflowId.of(upstreamWorkflow).job(Branch.fromString(upstreamJob));
    int numBuildsToShow = numBuilds.orElse(numBuildsToCompare(branch, upstream));
    logger.info("Comparing {} to {} with {} builds", branch, upstream, numBuildsToShow);

    InterestingTests branchFailures =
        new InterestingTests(jobsDb, buildsDb, testRunsDb, branch, numBuildsToShow);
    InterestingTests upstreamFailures =
        new InterestingTests(jobsDb, buildsDb, testRunsDb, upstream, numBuildsToShow);

    List<Long> branchFailedTestIds = branchFailures.testIds();
    Set<Long> allInterestingTestIds = Sets.newHashSet(branchFailedTestIds);
    logger.info("Found {} interesting branch failed tests", branchFailedTestIds.size());

    // no need to load the data twice if we comparing run with itself, which
    // is a convention for providing "nightly only" or "daily fastci" views
    // that actually show only upstream workflow
    boolean branchVsUpstreamComparison = (branch.compareTo(upstream) != 0);

    if (branchVsUpstreamComparison) {
      List<Long> upstreamFailedTestIds = upstreamFailures.testIds();
      logger.info("Found {} interesting upstream failed tests", upstreamFailedTestIds.size());
      allInterestingTestIds.addAll(upstreamFailedTestIds);
    }

    List<UpstreamFailures> uFailures = new ArrayList<>();
    List<Long> testIdsToLoad = Lists.newArrayList(allInterestingTestIds);

    uFailures.add(findAllResults(branchFailures.builds(), testIdsToLoad));
    if (branchVsUpstreamComparison) {
      uFailures.add(findAllResults(upstreamFailures.builds(), testIdsToLoad));
    }

    return uFailures;
  }

  /**
   * Find the recent set of results for a specific test. This looks at all the "NIGHTLY" type jobs,
   * a maximum of 10 back.
   *
   * @param test the TestName object describing the test
   * @param workflowId the id of the workflow e.g. nightly or fastCI
   * @return an optional failure object
   */
  public Optional<TestFailure> findAllWorkflowResultsForTest(WorkflowId workflowId, TestName test) {

    long startTime = System.nanoTime();

    List<JobId> jobs = jobsDb.getByWorkflow(workflowId);

    int numBuildsToShow = 32;
    List<StoredBuild> latestBuilds = new ArrayList<>();
    for (JobId job : jobs) {
      latestBuilds.addAll(
          new InterestingTests(jobsDb, buildsDb, testRunsDb, job, numBuildsToShow).builds());
    }

    UpstreamFailures failures =
        findAllResults(latestBuilds, Collections.singletonList(testNamesDb.dbId(test)));

    logger.info(
        "UpstreamFailuresDb::findAllWorkflowResultsForTest found {} in {} ms ",
        failures.failures().size(),
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));

    return extractFailureOrThrow(failures);
  }

  private Optional<TestFailure> extractFailureOrThrow(UpstreamFailures failures) {
    if (failures.failures().size() == 1) {
      return Optional.of(failures.failures().get(0));
    } else if (failures.failures().isEmpty()) {
      return Optional.empty();
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Some problem with db logic, found %s failures, expected 0 or 1",
              failures.failures().size()));
    }
  }

  /**
   * Find any interesting failures, where interesting means "has failed".
   *
   * @param workflowName the workflow of the jobn
   * @param jobName the name of the job
   * @return an UpstreamFailure object
   */
  public UpstreamFailures findInterestingFailures(String workflowName, String jobName) {
    WorkflowId workflowId = WorkflowId.of(workflowName);
    JobId jobId = workflowId.job(Branch.fromString(jobName));
    InterestingTests interesting = new InterestingTests(jobsDb, buildsDb, testRunsDb, jobId);
    return findAllResults(interesting.builds(), interesting.testIds());
  }

  /**
   * Find all failures for given test on all workflows and branches.
   *
   * <p>Note: this may return not only upstream branches, but also PR builds failures
   *
   * @param testName package/class/test
   * @return UpstreamFailures object, potentially empty
   */
  public Optional<TestFailure> findAllFailuresForTest(TestName testName) {
    logger.info("Collecting all failures for test {}", testName);
    long testId = testNamesDb.dbId(testName);
    List<StoredBuild> failedBuilds = buildsDb.getAllFailedBuildsForTestId(testId);
    return extractFailureOrThrow(findAllResults(failedBuilds, Collections.singletonList(testId)));
  }

  private UpstreamFailures findAllResults(List<StoredBuild> builds, List<Long> testIds) {
    if (builds.isEmpty()) {
      return new UpstreamFailures(Collections.emptyList());
    }

    long start = System.nanoTime();

    // we will use first build workflow id as upstream workflow used
    // assuming that it is uniform, even if not true it does a little harm
    Optional<JobId> firstJobId = this.jobsDb.getById(builds.get(0).jobId());
    WorkflowId workflowId = firstJobId.map(JobId::workflow).orElse(null);

    Map<Long, StoredBuild> buildCache =
        builds.stream().collect(Collectors.toMap(StoredBuild::id, Function.identity()));

    Map<Long, TestName> testNameCache = testNamesDb.find(testIds);

    final List<TestFailure> failures = new ArrayList<>();

    testRunsDb.getAllResultsForTestsInBuilds(testIds, buildCache.keySet()).stream()
        .collect(Collectors.groupingBy(StoredTestRun::testId))
        .forEach(
            (testId, testResults) -> {
              if (failures.isEmpty()) {
                logger.info(
                    "UpstreamFailuresDb::findAllResults got all results for analysis after {} ms",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
              }
              TestName testName = testNameCache.get(testId);
              if (testName == null) {
                logger.warn("Something went wrong, TestName not found for id: {}", testId);
                testName = testNamesDb.find(testId).orElseThrow();
              }
              List<RunDetails> details = buildRunDetailsFromRuns(buildCache, testResults);
              var failureDetails = FailureDetails.build(details);
              String recentLinkedIssue =
                  testLinkedIssuesDb
                      .recentLinkedIssue(testId)
                      .map(StoredTestLinkedIssue::linkedIssue)
                      .orElse(null);
              failures.add(
                  new TestFailure(
                      testName,
                      jiraLink(recentLinkedIssue),
                      failureDetails,
                      workflowId,
                      testResults.size(),
                      failureDetails.lastWeekRunsCount(),
                      failureDetails.lastMonthRunsCount()));
            });

    logger.info(
        "UpstreamFailuresDb::findAllResults found {} failures in {} ms",
        failures.size(),
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    return new UpstreamFailures(failures);
  }

  private IssueLink jiraLink(String issueId) {
    if (issueId != null) return issuesService.issueLink(IssueId.fromString(issueId));
    else return null;
  }

  /**
   * Transforms StoredTestRuns into RunDetails using provided builds information.
   *
   * <p>Note: we can assume that all testRuns are for exactly same test.
   */
  private List<RunDetails> buildRunDetailsFromRuns(
      Map<Long, StoredBuild> buildCache, List<StoredTestRun> testRuns) {
    return testRuns.stream()
        .map(
            run -> {
              StoredBuild build = buildCache.get(run.buildId());
              BuildId buildId =
                  jobsDb.getById(build.jobId()).orElseThrow().build(build.buildNumber());
              return new RunDetails(
                  buildId,
                  run.variant(),
                  run.runUrl(),
                  build.startTime().getEpochSecond(),
                  run.failed(),
                  run.skipped(),
                  run.output());
            })
        .collect(Collectors.toList());
  }

  /**
   * So this class slightly adjusts the overall "upstream failures" query to be scoped by a
   * particular job.
   */
  private QueryParameters makeFailureDetailsQueryForJob(
      String workflowName, String jobName, Optional<TestName> testName) {
    final UpstreamFailuresSummaryQuery query = new UpstreamFailuresSummaryQuery(false);

    for (String j : new ArrayList<>(query.joins())) {
      if (j.contains(JobsDb.JOBS_TABLE)) {
        query.replace(
            "join",
            j,
            j.replace("STRAIGHT_", "INNER ")
                + " AND j.workflow = :workflowName AND j.job_name = :jobName");
      }
    }

    Map<String, Object> params = new HashMap<>();
    params.put("workflowName", workflowName);
    params.put("jobName", jobName);
    AtomicReference<SqlParameterSource> source = new AtomicReference<>();

    testName.ifPresentOrElse(
        tstName -> {
          // Scope to just the desired test
          query.add("where", testNamesDb.testNameMapper().whereClause());
          source.set(
              testNamesDb
                  .testNameMapper()
                  .source(upstreamFailureDetailsParameterSource(params), tstName));
        },
        // No test, only get failures
        () -> source.set(upstreamFailureDetailsParameterSource(params)));

    String strQuery = query.toString();
    if (testName.isEmpty()) {
      // Return us any tests that failed that we know about
      strQuery = strQuery + " HAVING failed_count > 0";
    }
    logger.trace(strQuery);
    return new QueryParameters(strQuery, source.get());
  }

  /**
   * Get a details list of upstream failures for the specific test class name.
   *
   * @param className the test class name
   * @return a {@link UpstreamFailures} object scoped to the desired {@code className}
   */
  public UpstreamFailures getFailuresDetails(String className) {
    SqlParameterSource params =
        upstreamFailureDetailsParameterSource(Map.of("className", className));
    return template.query(
        makeUpstreamFailuresQuery("t.class_name = :className"),
        params,
        this::extractUpstreamFailures);
  }

  public Optional<TestFailure> getFailureDetails(TestName testName) {
    return getFailureDetails(testName, true);
  }

  /**
   * Retrieve detailed information on a specific known upstream failure.
   *
   * @param testName the name of the known failure for which to retrieve details.
   * @return an optional with details on the known failure {@code testName}, or an empty optional if
   *     it is not a recorded upstream failure. Please note that last failed run output (error msg,
   *     stacktrace, stderr, stdout) is missing as it is not included in the query.
   */
  Optional<TestFailure> getFailureDetails(TestName testName, boolean limitToFailed) {
    Mapper<TestName> mapper = testNamesDb.testNameMapper();
    String select = makeUpstreamFailuresQuery(mapper.whereClause(), limitToFailed);
    logger.debug(select);
    return template.query(
        select,
        mapper.source(upstreamFailureDetailsParameterSource(), testName),
        this::extractUpstreamFailureDetails);
  }

  private String makeUpstreamFailuresQuery(String additionalWhere) {
    return makeUpstreamFailuresQuery(additionalWhere, true);
  }

  private String makeUpstreamFailuresQuery(String additionalWhere, boolean limitToFailed) {
    UpstreamFailuresSummaryQuery query = new UpstreamFailuresSummaryQuery(limitToFailed);
    // Note: without the STRAIGHT_JOIN, that goes from less than 100ms to about 1 minute.
    if (additionalWhere != null && !additionalWhere.isEmpty()) {
      query.add("where", additionalWhere);
    }

    if (logger.isTraceEnabled()) {
      logger.trace("Upstream failures query: {}", query);
    }
    return query.toString();
  }

  private UpstreamFailures extractUpstreamFailures(ResultSet rs) throws SQLException {
    List<TestFailure> failures = new ArrayList<>();
    while (rs.next()) {
      failures.add(parseUpstreamBoardFailure(rs, true));
    }
    return new UpstreamFailures(failures);
  }

  private TestFailure parseUpstreamBoardFailure(ResultSet rs, boolean keepOnlyLastFailures)
      throws SQLException {
    TestName testName =
        new TestName(
            TestCategory.valueOf(rs.getString("category")),
            rs.getString("path"),
            rs.getString("class_name"),
            rs.getString("test_name"));
    long ranCount = rs.getLong("ran_count");
    long lastWeekRanCount = rs.getLong("week_ran_count");
    long lastMonthRanCount = rs.getLong("month_ran_count");
    var parser = new FailureDetailsParser();
    FailureDetails failureDetails = parser.parseFailureData(rs.getString("builds"));
    if (keepOnlyLastFailures) {
      // This is potentially a *lot* of data, so only keep it if the caller asks
      failureDetails.allByVariants().clear();
      failureDetails.allByVersions().clear();
    }

    // we do not set this issue link here as it should be set by service if needed
    IssueLink issueLink = null;

    return new TestFailure(
        testName, issueLink, failureDetails, null, ranCount, lastWeekRanCount, lastMonthRanCount);
  }

  private SqlParameterSource upstreamFailureDetailsParameterSource() {
    return upstreamFailureDetailsParameterSource(Collections.emptyMap());
  }

  private SqlParameterSource upstreamFailureDetailsParameterSource(
      Map<String, Object> extraParams) {
    Timestamp oneWeekAgo = Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS));
    Timestamp oneMonthAgo = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
    MapSqlParameterSource source =
        new MapSqlParameterSource()
            .addValue("aWeekAgo", oneWeekAgo)
            .addValue("aMonthAgo", oneMonthAgo);
    extraParams.forEach(source::addValue);
    return source;
  }

  private Optional<TestFailure> extractUpstreamFailureDetails(ResultSet rs) throws SQLException {
    if (!rs.first()) return Optional.empty();
    return Optional.of(parseUpstreamBoardFailure(rs, false));
  }
}
