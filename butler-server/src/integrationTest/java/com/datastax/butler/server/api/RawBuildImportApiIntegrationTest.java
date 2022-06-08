/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.IntegrationTest;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.StoredBuild;
import com.datastax.butler.server.db.TestNamesDb;
import com.datastax.butler.server.db.UpstreamFailuresDb;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RawBuildImportApiIntegrationTest extends IntegrationTest {

  @Autowired private UpstreamWorflowsDb workflowsRegistry;
  @Autowired private BuildsDb buildsRepository;
  @Autowired private JobsDb jobsRepository;
  @Autowired private UpstreamFailuresDb upstreamFailuresRepository;
  @Autowired private TestNamesDb testNamesRepository;

  private final Workflow ciWorkflow = new Workflow("ci", true).setBranches("main", null);

  @BeforeAll
  void registerWorkflow() {
    workflowsRegistry.registerWorkflows(List.of(ciWorkflow));
  }

  @BeforeEach
  void cleanupJobs() {
    jobsRepository.dropAllJobsForWorkflow(WorkflowId.of("ci"));
  }

  @Test
  void fullEndToEndTest() throws IOException {
    // given
    importBuild("build-ci-main-1.json");
    importBuild("build-ci-main-2.json");
    importBuild("build-ci-main-3.json");
    importBuild("build-ci-change-1.json");
    // when
    var mainFailures = upstreamFailuresRepository.findInterestingFailures("ci", "main");
    var changeVsMain =
        upstreamFailuresRepository.compareJobs("ci", "change", "ci", "main", Optional.empty());
    // then
    assertEquals(3, mainFailures.numBuilds());
    var failedTests = mainFailures.failed();
    assertEquals(1, failedTests.size());
    var failedTest = failedTests.get(0);
    assertEquals("com.example.Suite1.testB", failedTest.test().fullName());
    // it was run 6 times (2 variants x 3 builds) with 2 failures
    assertEquals(2, failedTest.failedCount());
    assertEquals(6, failedTest.runs());
    // and comparison should show 2 failures
    assertEquals(2, changeVsMain.size());
    var changeFailures = changeVsMain.get(0);
    var upstreamFailures = changeVsMain.get(1);
    assertEquals(2, changeFailures.failed().size());
    assertEquals(2, changeFailures.failures().size());
    assertEquals(
        2,
        upstreamFailures
            .failures()
            .size()); // if test failed on change it should be in the upstream
    var failedTestCases =
        changeFailures.failed().stream()
            .map(TestFailure::test)
            .map(TestName::testName)
            .collect(Collectors.toSet());
    assertEquals(Set.of("testA", "testB"), failedTestCases);
  }

  @Test
  void shouldFailImportWithUnconfiguredWorkflow() {
    Map<String, String> reqBody =
        Map.of(
            "workflow", "not-existing-wflw",
            "branch", "main");
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(reqBody)
        .log()
        .all()
        .when()
        .post(apiCallUri("ci", "builds", "import", "raw"))
        .then()
        .log()
        .all()
        .assertThat()
        .statusCode(400)
        .body("message", containsString("Workflow not-existing-wflw is not configured"));
  }

  @Test
  void shouldOverrideWhenImportingSameBuildTwice() throws IOException {
    // given
    importBuild("build-ci-main-1.json");
    importBuild("build-ci-main-1.json");
    // when
    var build = getExactlyOneBuild("ci");
    // then
    assertEquals(4, build.ranTests());
    assertEquals(1, build.failedTests());
    assertEquals(1, build.skippedTests());
  }

  @Test
  void shouldJoinWhenImportingDifferentResultsIntoSameBuild() throws IOException {
    // given
    importBuild("build-ci-main-1.json");
    importBuild("build-ci-main-1-add-tests.json");
    // when
    var build = getExactlyOneBuild("ci");
    // then we should observe new stats
    assertEquals(6, build.ranTests());
    assertEquals(2, build.failedTests());
    assertEquals(2, build.skippedTests());
  }

  @Test
  void shouldImportTestCategory() throws IOException {
    // given
    importBuild("build-ci-main-1.json");
    // when
    var tests = testNamesRepository.find("com.example", "Suite1", "testS");
    // then
    assertEquals(1, tests.size());
    assertFalse(tests.get(0).category().isUnknown());
    assertEquals("PERF", tests.get(0).category().toString());
  }

  private void importBuild(String jsonDataPath) throws IOException {
    var reqBody = testResource(jsonDataPath);
    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(reqBody)
        .log()
        .all()
        .when()
        .post(apiCallUri("ci", "builds", "import", "raw"))
        .then()
        .log()
        .all()
        .assertThat()
        .statusCode(200);
  }

  private StoredBuild getExactlyOneBuild(String workflow) {
    var jobs = jobsRepository.getByWorkflow(WorkflowId.of(workflow));
    assertEquals(1, jobs.size());
    var jobId = jobs.get(0);
    var jobDbId = jobsRepository.dbId(jobId);
    var builds = buildsRepository.allOf(jobDbId);
    assertEquals(1, builds.size());
    return builds.get(0);
  }
}
