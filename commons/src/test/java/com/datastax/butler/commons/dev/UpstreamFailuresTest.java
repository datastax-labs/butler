/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpstreamFailuresTest {

  UpstreamFailures failures() {
    var jobId = new JobId(WorkflowId.of("workflow"), Branch.fromString("main"));
    var build1 = new BuildId(jobId, 1);
    var build2 = new BuildId(jobId, 2);
    var build3 = new BuildId(jobId, 3);
    var failureA =
        FailuresTestData.testFailure(
            "testA",
            List.of(
                FailuresTestData.runDetails(build2, 'P'),
                FailuresTestData.runDetails(build1, 'F')));
    var failureB =
        FailuresTestData.testFailure(
            "testB",
            List.of(
                FailuresTestData.runDetails(build3, 'F'),
                FailuresTestData.runDetails(build1, 'P')));
    return new UpstreamFailures(List.of(failureA, failureB));
  }

  @Test
  void shouldCalculateNumberOfBuilds() {
    var failures = new ArrayList<TestFailure>();
    var empty = new UpstreamFailures(failures);
    assertEquals(0, empty.numBuilds());
    assertEquals(3, failures().numBuilds());
  }

  @Test
  void shouldFilterBuildsByNumber() {
    var f = failures();
    assertEquals(3, f.beforeBuild(99).numBuilds());
    assertEquals(2, f.beforeBuild(3).numBuilds());
    assertEquals(0, f.beforeBuild(1).numBuilds());
  }

  @Test
  void shouldFindFailed() {
    var f = failures();
    assertEquals(2, f.failed().size());
  }

  @Test
  void shouldFindTestByName() {
    var f = failures();
    assertEquals(1, f.findTest(FailuresTestData.testName("testA")).size());
    assertEquals(1, f.findTest(FailuresTestData.testName("testB")).size());
    assertEquals(0, f.findTest(FailuresTestData.testName("notExistingTestCase")).size());
  }
}
