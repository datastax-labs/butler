/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.FailureDetails;
import com.datastax.butler.commons.dev.FailuresTestData;
import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.dev.UpstreamFailures;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.TestNamesDb;
import com.datastax.butler.server.db.UpstreamFailuresDb;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.UpstreamService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UpstreamControllerTest {

  private final JobsDb jobsDb = Mockito.mock(JobsDb.class);
  private final BuildsDb buildsDb = Mockito.mock(BuildsDb.class);
  private final UpstreamWorflowsDb workflowsDb = Mockito.mock(UpstreamWorflowsDb.class);
  private final UpstreamFailuresDb upstreamFailuresDb = Mockito.mock(UpstreamFailuresDb.class);
  private final UpstreamService upstreamService = Mockito.mock(UpstreamService.class);
  private final TestNamesDb testNamesDb = Mockito.mock(TestNamesDb.class);

  UpstreamController newController() {
    return new UpstreamController(
        jobsDb, buildsDb, workflowsDb, upstreamFailuresDb, upstreamService, testNamesDb);
  }

  @Test
  void shouldNotReturnTestRunDetailsForCompareTwoJobs() {
    // given
    var controller = newController();
    // when there are failed tests in the comparison with some output details
    var branchFailures = failures("ci", "branch");
    var mainFailures = failures("ci", "main");
    assertTrue(hasNonEmptyTestRunOutput(branchFailures));
    Mockito.when(upstreamFailuresDb.compareJobs("ci", "branch", "ci", "main", Optional.empty()))
        .thenReturn(List.of(branchFailures, mainFailures));
    // then it should not have any output details in the api call results
    var out = controller.compareJobs("ci", "branch", "ci", "main", Optional.empty());
    assertFalse(out.isEmpty());
    assertTrue(out.stream().noneMatch(this::hasNonEmptyTestRunOutput));
  }

  @Test
  void shouldNotReturnTestRunDetailsForUpstreamBranchHistory() {
    // given
    var controller = newController();
    var workflow = new Workflow("ci", true);
    Mockito.when(workflowsDb.getWorkflow("ci")).thenReturn(Optional.of(workflow));
    // when there are failed tests in the comparison with some output details
    var mainFailures = failures("ci", "main");
    assertTrue(hasNonEmptyTestRunOutput(mainFailures));
    Mockito.when(upstreamFailuresDb.compareJobToUpstream(workflow, "main", Optional.empty()))
        .thenReturn(Optional.of(List.of(mainFailures, mainFailures)));
    // then it should not have any output details in the api call results
    var out = controller.compareJobs("ci", "main", Optional.empty());
    assertFalse(out.isEmpty());
    assertTrue(out.stream().noneMatch(this::hasNonEmptyTestRunOutput));
  }

  private boolean hasNonEmptyTestRunOutput(UpstreamFailures upstreamFailures) {
    return upstreamFailures.failures().stream()
        .anyMatch(x -> hasNonEmptyTestRunOutput(x.failureDetails()));
  }

  private boolean hasNonEmptyTestRunOutput(FailureDetails failureDetails) {
    if (failureDetails.allRuns().stream().anyMatch(RunDetails::hasOutput)) return true;
    if (failureDetails.lastByVariants().values().stream().anyMatch(RunDetails::hasOutput))
      return true;
    if (failureDetails.lastByVersions().values().stream().anyMatch(RunDetails::hasOutput))
      return true;
    if (failureDetails.allByVariants().values().stream()
        .flatMap(List::stream)
        .anyMatch(RunDetails::hasOutput)) return true;
    if (failureDetails.allByVersions().values().stream()
        .flatMap(List::stream)
        .anyMatch(RunDetails::hasOutput)) return true;
    return false;
  }

  private UpstreamFailures failures(String workflow, String branch) {
    var jobId = new JobId(WorkflowId.of(workflow), Branch.fromString(branch));
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
}
