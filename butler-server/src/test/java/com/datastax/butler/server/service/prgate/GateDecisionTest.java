/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.prgate;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.FailureDetails;
import com.datastax.butler.commons.dev.FailuresTestData;
import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.dev.UpstreamFailures;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

class GateDecisionTest {

  private static final Random random = new Random(System.currentTimeMillis());

  final JobId jobId = new JobId(WorkflowId.of("nightly"), Branch.fromString("trunk"));
  final BuildId buildId = new BuildId(jobId, 1);

  final RunDetails failed = runDetails(true, false);
  final RunDetails passed = runDetails(false, false);
  final RunDetails skipped = runDetails(false, true);

  RunDetails runDetails(boolean failed, boolean skipped) {
    return new RunDetails(buildId, TestVariant.DEFAULT, null, 0, failed, skipped, null);
  }

  @Test
  void testEncodeRunDetails() {
    assertEquals('F', GateDecision.encode(failed));
    assertEquals('P', GateDecision.encode(passed));
    assertEquals('S', GateDecision.encode(skipped));
  }

  @Test
  void testEncodeFailure() {
    var runs = List.of(passed, passed, failed, skipped, failed);
    var details = new FailureDetails(runs, null, null, null, null, null, null, null, null, 2, 0, 0);
    var failure = new TestFailure(null, null, details, null, runs.size(), 0, 0);
    assertEquals("PPFSF", GateDecision.encode(failure));
  }

  @Test
  void alwaysPassingShouldBeApproved() {
    for (int i = 0; i < 1024; i++) {
      var storyLen = 1 + random.nextInt(6);
      var story = RandomStringUtils.random(storyLen, 'S', ' ', 'P');
      expectApprove(story, whatever().story());
    }
  }

  @Test
  void alwaysSkippedShouldBeApproved() {
    for (int i = 0; i < 1024; i++) {
      var storyLen = 1 + random.nextInt(6);
      var story = RandomStringUtils.random(storyLen, 'S', ' ');
      expectApprove(story, whatever().story());
    }
  }

  @Test
  void shouldRejectFailuresIfUpstreamGreen() {
    expectReject("F", passing().story());
    expectReject("FP", passing().story());
    expectReject("PFP", passing().story());
    expectReject("FPPPPPP", passing().story());
    expectReject("FFP", passing().story());
    expectReject("FFFP", passing().story());
    expectReject("FFFFP", passing().story());
    expectReject("FFFFPFP", passing().story());
    expectReject("FFFFPPP", passing().story());
    expectReject("FFPFFPP", passing().story());
    expectReject("FFPPFFPP", passing().story());
  }

  @Test
  void shouldApproveFixedButRejectFlaky() {
    expectApprove("PFF", passing().story());
    expectApprove("PPFF", passing().story());
    expectApprove("PPF", passing().story());
    expectApprove("PF", passing().story());
    expectReject("FPF", passing().story());
    expectReject("PFP", passing().story());
    expectReject("PFPP", passing().story());
    expectApprove("PPFPP", passing().story());
    expectApprove("PPFP", passing().story());
  }

  @Test
  void shouldApproveIfUpstreamHasFailures() {
    for (int i = 0; i < 1024; i++) {
      var story = randomStory(1 + random.nextInt(6));
      var upstreamStory = randomStory(8);
      if (upstreamStory.hasFailures()) {
        expectApprove(story.story(), upstreamStory.story());
      }
    }
  }

  @Test
  void shouldRejectIfNewTestHasFailures() {
    var noRuns = "     S  S";
    expectReject("F", noRuns);
    expectReject("FF", noRuns);
    expectReject("FFF", noRuns);
    expectReject("FPF", noRuns);
    expectReject("FPFP", noRuns);
    expectReject("PFP", noRuns);
    expectReject("PFPF", noRuns);
  }

  @Test
  void shouldApproveIfRecentlyNotRunAtAll() {
    expectApprove("  FF", passing().story());
    expectApprove("  FF", "");
    expectApprove(" FF", whatever().story());
  }

  @Test
  void shouldCreateRejectedWithDetails() {
    var decision = GateDecision.rejected("some explanation", List.of("Ala", "ma", "kota"));
    assertTrue(decision.isRejected());
    assertEquals(3, decision.details().size());
  }

  @Test
  void shouldCreateRejectedWithNullDetails() {
    var decision = GateDecision.rejected("some explanation", null);
    assertTrue(decision.isRejected());
    assertEquals(0, decision.details().size());
  }

  @Test
  void shouldApproveRunWithoutFailures() {
    var f = FailuresTestData.createFailure("PP");
    UpstreamFailures upstream = new UpstreamFailures(List.of(f));
    var decision = GateDecision.check(f, upstream);
    assertTrue(decision.isApproved());
  }

  @Test
  void shouldApproveRunWithSameFailureUpstream() {
    TestFailure f = FailuresTestData.createFailure("FF");
    UpstreamFailures upstream = new UpstreamFailures(List.of(f));
    var decision = GateDecision.check(f, upstream);
    assertTrue(decision.isApproved());
    assertFalse(decision.isRejected());
  }

  @Test
  void shouldRejectNewFailure() {
    var onBranch = FailuresTestData.createFailure("F");
    var onUpstream = FailuresTestData.createFailure("PPP");
    UpstreamFailures upstream = new UpstreamFailures(List.of(onUpstream));
    var decision = GateDecision.check(onBranch, upstream);
    assertTrue(decision.isRejected());
  }

  private void expectApprove(String story, String upstream) {
    var decision = GateDecision.checkStory(new TestRunsStory(story), new TestRunsStory(upstream));
    assertTrue(decision.isApproved(), "Expected approve: " + decision.explanation());
  }

  private void expectReject(String story, String upstream) {
    var decision = GateDecision.checkStory(new TestRunsStory(story), new TestRunsStory(upstream));
    assertFalse(decision.isApproved(), "Expected reject: " + decision.explanation());
  }

  private TestRunsStory whatever() {
    return randomStory(8);
  }

  private TestRunsStory passing() {
    return new TestRunsStory("PPPPPPPP");
  }

  private TestRunsStory randomStory(int len) {
    var story = RandomStringUtils.random(len, 'S', 'P', 'F', ' ');
    return new TestRunsStory(story);
  }
}
