/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.api.ci.BuildImportRequest;
import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.IntegrationTest;
import com.datastax.butler.server.TestData;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.TestNamesDb;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BuildsServiceIntegrationTest extends IntegrationTest {

  @Autowired private BuildsService buildsService;
  @Autowired private JobsDb jobsRepository;
  @Autowired private BuildsDb buildsRepository;
  @Autowired private TestNamesDb testNamesRepository;

  private static final WorkflowId ciWorkflow = WorkflowId.of("ci");

  @Test
  void shouldProperlyImportRawBuild() {
    // given
    var branch = Branch.fromString(randomBranch());
    var jobId = new JobId(ciWorkflow, branch);
    long jobDbId = jobsRepository.dbId(jobId);
    var testRuns =
        List.of(
            TestData.rawTestRun("case1", false, false),
            TestData.rawTestRun("case2", true, false),
            TestData.rawTestRun("case3", false, true),
            TestData.rawTestRun("case4", true, false));
    var buildData =
        new BuildImportRequest(
            "ci",
            "main",
            7,
            Instant.now().getEpochSecond(),
            10000,
            "http://ci.example.com/13",
            testRuns);
    // when
    buildsService.importRawBuildForJob(jobDbId, buildData);
    var storedBuild = buildsRepository.getByBuildNumber(jobDbId, 7);
    // then build should be stored
    assertTrue(storedBuild.isPresent());
    assertEquals(4, storedBuild.get().ranTests());
    assertEquals(2, storedBuild.get().failedTests());
    // and test runs should be added
    var failedBuildsCase2 = buildsRepository.getAllFailedBuildsForTestId(testId("case2"));
    assertFalse(failedBuildsCase2.isEmpty());
    var failedBuildsCase1 = buildsRepository.getAllFailedBuildsForTestId(testId("case1"));
    assertTrue(failedBuildsCase1.isEmpty());
  }

  private long testId(String testCase) {
    var testName = TestName.ofSuiteAndTest(TestData.TEST_SUITE, testCase);
    return testNamesRepository.dbId(testName);
  }
}
