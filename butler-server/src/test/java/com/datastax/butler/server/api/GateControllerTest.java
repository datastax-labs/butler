/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.butler.api.gate.JenkinsBuildApprovalRequest;
import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.FailuresTestData;
import com.datastax.butler.commons.dev.UpstreamFailures;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.StoredBuild;
import com.datastax.butler.server.db.UpstreamFailuresDb;
import com.datastax.butler.server.service.BuildsService;
import com.datastax.butler.server.service.prgate.GateDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GateControllerTest {

  private final JobsDb jobsDb = Mockito.mock(JobsDb.class);
  private final BuildsDb buildsDb = Mockito.mock(BuildsDb.class);
  private final BuildsService buildService = Mockito.mock(BuildsService.class);
  private final CiController ciController = Mockito.mock(CiController.class);
  private final UpstreamFailuresDb upstreamFailuresDb = Mockito.mock(UpstreamFailuresDb.class);

  GateController gate() {
    return new GateController(jobsDb, buildsDb, buildService, ciController, upstreamFailuresDb);
  }

  JenkinsBuild jenkinsBuild(BuildId buildId, JenkinsBuild.Status status) {
    return new JenkinsBuild(
        null,
        buildId,
        HttpUrl.parse("http://jenkins.example.com"),
        status,
        Instant.now(),
        Duration.ofMinutes(10),
        Duration.ofMinutes(8),
        null,
        null);
  }

  BuildId buildId() {
    return new BuildId(new JobId(WorkflowId.of("nightly"), Branch.fromString("main")), 1);
  }

  JenkinsBuildApprovalRequest approvalRequest() {
    return new JenkinsBuildApprovalRequest(
        "http://jenkins.example.com/ci/branch", "ci", "branch", 1, "nightly", "main");
  }

  @Test
  void shouldGenerateRejectedAsUnusable() {
    var resp =
        gate()
            .rejectAsUnusable(
                jenkinsBuild(buildId(), JenkinsBuild.Status.ABORTED), approvalRequest());
    assertFalse(resp.approval());
    assertTrue(resp.summary().contains("Build is not usable"));
    assertTrue(resp.summary().contains("ABORTED"));
  }

  @Test
  void shouldGenerateRejectedDueFailures() {
    var resp =
        gate()
            .rejectWithFailures(
                jenkinsBuild(buildId(), JenkinsBuild.Status.UNSTABLE),
                approvalRequest(),
                "FAILED SUMMARY",
                List.of("test X failed", "test Y failed"));
    assertFalse(resp.approval());
    assertTrue(resp.summary().contains("FAILED SUMMARY"));
    assertEquals(3, resp.details().size());
    assertTrue(resp.details().get(0).contains("ci/branch"));
    assertTrue(resp.details().get(0).contains("nightly/main"));
  }

  @Test
  void shouldGenerateRejectedAsSkippedByWorkflow() {
    var resp =
        gate()
            .rejectAsSkippedByWorkflow(
                jenkinsBuild(buildId(), JenkinsBuild.Status.UNSTABLE),
                approvalRequest(),
                new Workflow("workflow-name", false));
    assertFalse(resp.approval());
    assertTrue(resp.summary().contains("skipped"));
    assertTrue(resp.summary().contains("workflow-name"));
  }

  @Test
  void shouldLimitNumberOfTestsInRejectionDetails() {
    var resp =
        gate()
            .rejectWithFailures(
                jenkinsBuild(buildId(), JenkinsBuild.Status.UNSTABLE),
                approvalRequest(),
                "FAILED SUMMARY",
                Collections.nCopies(23, "test failed"));
    assertFalse(resp.approval());
    assertTrue(resp.summary().contains("FAILED SUMMARY"));
    assertEquals(15, resp.details().size());
    assertTrue(resp.details().get(0).contains("ci/branch"));
    assertTrue(resp.details().get(0).contains("nightly/main"));
    assertTrue(resp.details().get(1).contains("Showing only first 13 NEW test failures"));
  }

  @Test
  void shouldCreateApprovalResponse() {
    var resp =
        gate()
            .approval(
                jenkinsBuild(buildId(), JenkinsBuild.Status.UNSTABLE),
                approvalRequest(),
                "SOME EXPLANATION");
    assertTrue(resp.approval());
    assertTrue(resp.summary().contains("SOME EXPLANATION"));
  }

  @Test
  void shouldStoreAndQueueForUpdateRunningBuild() {
    var buildId = buildId();
    var jenkinsBuild = jenkinsBuild(buildId, JenkinsBuild.Status.RUNNING);
    when(jobsDb.dbId(buildId.jobId())).thenReturn(7L);
    when(buildService.saveNewBuild(7L, jenkinsBuild)).thenReturn(true);
    var ok = gate().storeBuild(jenkinsBuild, buildId.jobId(), buildId());
    assertTrue(ok);
    verify(buildService).saveNewBuild(7L, jenkinsBuild);
    verify(ciController).queueJenkinsBuild(buildId, jenkinsBuild, jenkinsBuild.url());
  }

  @Test
  void shouldDeleteAndStoreFinishedBuildIfNotAlreadyStored() {
    var buildId = buildId();
    var jenkinsBuild = jenkinsBuild(buildId, JenkinsBuild.Status.UNSTABLE);
    when(jobsDb.dbId(buildId.jobId())).thenReturn(7L);
    when(buildsDb.getByBuildNumber(7L, 1)).thenReturn(Optional.empty());
    when(buildService.saveNewBuild(7L, jenkinsBuild)).thenReturn(true);
    var ok = gate().storeBuild(jenkinsBuild, buildId.jobId(), buildId());
    assertTrue(ok);
    verify(buildsDb).deleteByBuildNumberIfExists(7L, 1);
    verify(buildService).saveNewBuild(7L, jenkinsBuild);
    verify(ciController, times(0)).queueJenkinsBuild(buildId, jenkinsBuild, jenkinsBuild.url());
  }

  @Test
  void shouldUseFinishedBuildIfAlreadyStored() {
    testUseAlreadyStoredBuild(true);
  }

  @Test
  void shouldNotUseFinishedAlreadyStoredButUnusableBuild() {
    testUseAlreadyStoredBuild(false);
  }

  private void testUseAlreadyStoredBuild(boolean buildUsable) {
    var buildId = buildId();
    var jenkinsBuild = jenkinsBuild(buildId, JenkinsBuild.Status.UNSTABLE);
    var storedBuild =
        new StoredBuild(
            123,
            7L,
            1,
            null,
            JenkinsBuild.Status.SUCCESS,
            Instant.now(),
            1000,
            buildUsable,
            true,
            0,
            100,
            10);
    when(jobsDb.dbId(buildId.jobId())).thenReturn(7L);
    when(buildsDb.getByBuildNumber(7L, 1)).thenReturn(Optional.of(storedBuild));
    when(buildService.saveNewBuild(7L, jenkinsBuild)).thenReturn(true);
    var ok = gate().storeBuild(jenkinsBuild, buildId.jobId(), buildId());
    assertEquals(buildUsable, ok);
    verify(buildsDb, times(0)).deleteByBuildNumberIfExists(7L, 1);
    verify(buildService, times(0)).saveNewBuild(7L, jenkinsBuild);
    verify(ciController, times(0)).queueJenkinsBuild(buildId, jenkinsBuild, jenkinsBuild.url());
  }

  @Test
  void shouldConsiderAbortedAsUnusable() {
    var buildId = buildId();
    var jenkinsBuild = jenkinsBuild(buildId, JenkinsBuild.Status.ABORTED);
    when(jobsDb.dbId(buildId.jobId())).thenReturn(7L);
    when(buildService.saveNewBuild(7L, jenkinsBuild)).thenReturn(true);
    var ok = gate().storeBuild(jenkinsBuild, buildId.jobId(), buildId());
    assertFalse(ok);
    verify(buildsDb, times(0)).deleteByBuildNumberIfExists(7L, 1);
    verify(buildService, times(0)).saveNewBuild(7L, jenkinsBuild);
    verify(ciController, times(0)).queueJenkinsBuild(buildId, jenkinsBuild, jenkinsBuild.url());
  }

  @Test
  void shouldApproveIfNoFailuresAtAll() {
    var decision = gateDecision("PP", "PPFPPFPPP");
    assertTrue(decision.isApproved());
    assertFalse(decision.isRejected());
  }

  @Test
  void shouldApproveIfNoNewFailures() {
    var decision = gateDecision("PFP", "PPFPPFPPP");
    assertTrue(decision.isApproved());
    assertFalse(decision.isRejected());
  }

  @Test
  void shouldRejectIfNewFailures() {
    var decision = gateDecision("F", "PPPPPP");
    assertFalse(decision.isApproved());
    assertTrue(decision.isRejected());
  }

  @Test
  void shouldApproveIfFailingTestWasSkipped() {
    var decision = gateDecision("SF", "PPPPPP");
    assertTrue(decision.isApproved());
    assertFalse(decision.isRejected());
  }

  private GateDecision gateDecision(String branchStory, String upstreamStory) {
    var onBranch = new UpstreamFailures(List.of(FailuresTestData.createFailure(branchStory)));
    var onUpstream = new UpstreamFailures(List.of(FailuresTestData.createFailure(upstreamStory)));
    List<UpstreamFailures> failures = List.of(onBranch, onUpstream);
    var req = approvalRequest();
    when(upstreamFailuresDb.compareJobs(
            req.pipeline(),
            req.branch(),
            req.upstreamWorkflow(),
            req.upstreamBranch(),
            Optional.of(16)))
        .thenReturn(failures);
    return gate().makeDecision(req);
  }
}
