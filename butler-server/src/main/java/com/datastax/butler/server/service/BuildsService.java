/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service;

import com.datastax.butler.api.ci.BuildImportRequest;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JenkinsBuild.Status;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.TestReport;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.StoredBuild;
import com.datastax.butler.server.db.StoredTestRun;
import com.datastax.butler.server.db.TestNamesDb;
import com.datastax.butler.server.db.TestRunsDb;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BuildsService {
  private final Logger logger = LogManager.getLogger();
  private final BuildsDb buildsDb;
  private final TestNamesDb testNamesDb;
  private final TestRunsDb testRunsDb;

  /** Creates the service (Auto-wired by Spring). */
  @Autowired
  public BuildsService(BuildsDb buildsDb, TestNamesDb testNamesDb, TestRunsDb testRunsDb) {
    this.buildsDb = buildsDb;
    this.testNamesDb = testNamesDb;
    this.testRunsDb = testRunsDb;
  }

  /**
   * Record a <b>completed</b> and not yet recorded build within butler.
   *
   * @param jobDbId the id (in the SQL ci_jobs table) of the job of the build (which much thus be
   *     recorded).
   * @param build the build to save. It must validate {@link Status#isCompleted()}.
   * @return whether tests runs have been recorded (they haven't if either they cannot be retrieved
   *     (rare hopefully) or if the build had "pipeline failures").
   */
  public boolean saveNewBuild(long jobDbId, JenkinsBuild build) {
    assert build.status().isCompleted();

    // If the run "failed" (in the jenkins sense), we don't save the test report. This avoids having
    // to deal with broken test report that contain "bucket failures" reports. Same if the report
    // cannot be retrieved, though that should happen less often.
    TestReport report = build.isUsable() ? build.getTestReport().orElse(null) : null;

    long buildId = buildsDb.insert(jobDbId, build);

    if (report == null) {
      logger.info("Build {} did not run all the tests and tests results are skipped", build);
      buildsDb.markBuildStored(buildId);
      return false;
    }

    testRunsDb.insert(buildId, report);
    buildsDb.markBuildStored(buildId);
    return true;
  }

  /**
   * Import raw build data into butler database.
   *
   * <p>Acceptance criteria: - if build with this job id and number exists it will be used - if it
   * does not it will be created - if it already existed then no test runs is removed - fields such
   * as number of failed tests, skipped etc for build are updated to reflect added and preexisting
   * tests - will be set as "usable" and "fully stored" at the end
   *
   * @param jobDbId database job id (for JOBS) table.
   * @param buildData build data, including information about test runs.
   * @return database id of created or updated build.
   */
  public long importRawBuildForJob(long jobDbId, BuildImportRequest buildData) {
    // create build id and see if it does exist, create if it does not
    StoredBuild storedBuild = getOrCreateBuild(jobDbId, buildData);
    // add test results
    var testRunsToStore =
        buildData.tests().stream()
            .map(r -> fromRawBuildTestRun(storedBuild.id(), r))
            .collect(Collectors.toList());
    testRunsDb.deleteRuns(testRunsToStore);
    testRunsDb.insertRuns(testRunsToStore);
    // update summary and mark it as stored
    updateBuildSummary(storedBuild.id());
    buildsDb.markBuildStored(storedBuild.id());
    return storedBuild.id();
  }

  /**
   * Update fields such as failed_tests, ran_tests, skipped_tests so that they reflect linked test
   * runs.
   *
   * <p>This call is required because we can import build incrementally from multiple test reports.
   *
   * <p>Implementation could be more efficient, but let's optimize when it will become a problem.
   */
  public StoredBuild updateBuildSummary(long buildDbId) {
    var testRuns = testRunsDb.getTestRunsForBuild(buildDbId);
    var ranCount = testRuns.stream().count();
    var failedCount = testRuns.stream().filter(StoredTestRun::failed).count();
    var skippedCount = testRuns.stream().filter(StoredTestRun::skipped).count();
    buildsDb.updateBuildSummary(buildDbId, ranCount, failedCount, skippedCount);
    return buildsDb.get(buildDbId).orElseThrow();
  }

  private StoredBuild getOrCreateBuild(long jobDbId, BuildImportRequest buildData) {
    var found = buildsDb.getByBuildNumber(jobDbId, buildData.buildNumber());
    if (found.isEmpty()) {
      buildsDb.insert(jobDbId, buildData);
      found = buildsDb.getByBuildNumber(jobDbId, buildData.buildNumber());
    }
    return found.orElseThrow();
  }

  private StoredTestRun fromRawBuildTestRun(long buildDbId, BuildImportRequest.TestRun testRun) {
    // find test name
    var testName =
        TestName.ofSuiteAndTestWithCategory(
            testRun.testSuite(), testRun.testCase(), testRun.category());
    long testDbId = testNamesDb.dbId(testName);
    // and create object
    StoredTestRun storedRun =
        new StoredTestRun(
            testDbId,
            TestVariant.fromString(testRun.variant()),
            buildDbId,
            null,
            testRun.failed(),
            testRun.skipped(),
            testRun.durationMs(),
            testRun.url());
    // add output if provided
    if (testRun.output() != null) {
      storedRun.addFailureDetails(
          testRun.output().errorDetails(),
          testRun.output().errorStackTrace(),
          testRun.output().stdout(),
          testRun.output().stderr());
    }
    return storedRun;
  }
}
