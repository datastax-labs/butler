/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.prgate;

import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.dev.UpstreamFailures;
import java.util.Collections;
import java.util.List;

public class GateDecision {

  private final boolean approved;
  private final String explanation;
  private final List<String> details;

  GateDecision(boolean approved, String explanation, List<String> details) {
    this.approved = approved;
    this.explanation = explanation;
    this.details = details;
  }

  public boolean isRejected() {
    return !approved;
  }

  public boolean isApproved() {
    return approved;
  }

  public String explanation() {
    return explanation;
  }

  public List<String> details() {
    return details;
  }

  public static GateDecision approved(String explanation) {
    return new GateDecision(true, explanation, Collections.emptyList());
  }

  public static GateDecision rejected(String explanation) {
    return new GateDecision(false, explanation, Collections.emptyList());
  }

  public static GateDecision rejected(String explanation, List<String> details) {
    return new GateDecision(
        false, explanation, details != null ? details : Collections.emptyList());
  }

  /**
   * Make decision for a single test if it can be merged based on branch vs upstream history.
   *
   * @param result Failure information on the branch to merge (not necessarily failed)
   * @param upstream all failed tests on upstream branch
   * @return decision + explanation
   */
  public static GateDecision check(TestFailure result, UpstreamFailures upstream) {
    if (!result.hasFailed()) return GateDecision.approved("Test did not fail at all.");
    var branchStory = new TestRunsStory(encode(result));
    var testName = result.test();
    var upstreamFailure = upstream.findTest(testName);
    var upstreamStory =
        new TestRunsStory(
            upstreamFailure.stream().map(GateDecision::encode).findFirst().orElse(""));
    return checkStory(branchStory, upstreamStory);
  }

  private static String failureMsg(
      String msg, TestRunsStory branchStory, TestRunsStory upstreamStory) {
    return new StringBuilder()
        .append(msg)
        .append(". No ")
        .append(upstreamStory.noResults() ? "results" : "failures")
        .append(" on upstream;")
        .append("\n")
        .append(history(branchStory, upstreamStory))
        .append(";")
        .toString();
  }

  private static String history(TestRunsStory branchStory, TestRunsStory upstreamStory) {
    return String.format("branch story: [%s] vs upstream: [%s]", branchStory, upstreamStory);
  }

  static GateDecision checkStory(TestRunsStory branchStory, TestRunsStory upstreamStory) {
    // if after 4 recent runs it is all passing use those 4 recent runs
    if (branchStory.tail().alwaysPassing().orElse(false)) {
      return checkStory(branchStory.head(), upstreamStory);
    }
    var results = branchStory.results();
    // if no failures then we approve
    if (!branchStory.hasFailures()) {
      return approved("no failures in test run: " + branchStory.story());
    }
    // approve if not run in the last build
    if (branchStory.story().startsWith(" ")) {
      return approved("test removed or skipped?: " + branchStory.story());
    }
    // if upstream does not have any failures (all passed or it is a new test)
    if (!upstreamStory.hasFailures() || upstreamStory.noResults()) {
      if (branchStory.alwaysFailing().orElse(false))
        return rejected(failureMsg("test is constantly failing", branchStory, upstreamStory));
      if (results.startsWith("PFP") || results.startsWith("FPF"))
        return rejected(failureMsg("test looks flaky", branchStory, upstreamStory));
      if (results.startsWith("F"))
        return rejected(failureMsg("test failed in the recent build", branchStory, upstreamStory));
    }
    // in other case we approve but provide context
    return approved(history(branchStory, upstreamStory));
  }

  static String encode(TestFailure result) {
    StringBuilder out = new StringBuilder();
    result.failureDetails().allRuns().stream().map(GateDecision::encode).forEach(out::append);
    return out.toString();
  }

  /** Return one letter S(kipped) P(assed) or F(ailed) for given test run. */
  static Character encode(RunDetails runDetails) {
    if (runDetails.failed()) return 'F';
    if (runDetails.skipped()) return 'S';
    return 'P';
  }
}
