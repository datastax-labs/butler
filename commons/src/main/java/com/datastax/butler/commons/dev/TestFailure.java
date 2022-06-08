/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.issues.IssueLink;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.TestRunOutput;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.Value;

@Value
public class TestFailure {
  TestName test;
  IssueLink issueLink;
  @NonNull FailureDetails failureDetails;
  WorkflowId workflowId;
  long runs;
  long lastWeekRuns;
  long lastMonthRuns;

  /** Return workflow name for failures based on last build. */
  public Optional<String> lastRunWorkflowName() {
    BuildId lastRunBuild = failureDetails().last().id();
    return Optional.of(lastRunBuild.jobId().workflow().name());
  }

  /**
   * Return recent failed test output information (error details, stdout etc.).
   *
   * @return test run output
   */
  public Optional<TestRunOutput> lastFailedOutput() {
    return failureDetails().lastFailed() == null
        ? Optional.empty()
        : Optional.ofNullable(failureDetails.lastFailed().output());
  }

  /** Return list of versions on which failure happened. */
  public Set<BranchVersion> affectedVersions() {
    return failureDetails().lastByVersions().keySet();
  }

  public boolean hasFailed() {
    return failedCount() > 0;
  }

  public long failedCount() {
    return failureDetails.allRuns().stream().filter(RunDetails::failed).count();
  }

  public Set<Integer> buildNumbers() {
    return failureDetails.allRuns().stream()
        .map(x -> x.id().buildNumber())
        .collect(Collectors.toSet());
  }

  /** Return test failure with builds earlier than given buildNum. */
  public TestFailure beforeBuild(int buildNum) {
    var matchedRuns =
        failureDetails.allRuns().stream()
            .filter(x -> x.id().buildNumber() < buildNum)
            .collect(Collectors.toList());
    return new TestFailure(
        this.test,
        this.issueLink,
        FailureDetails.build(matchedRuns),
        this.workflowId,
        failureDetails.allRuns().size(),
        failureDetails.lastWeekRunsCount(),
        failureDetails.lastMonthRunsCount());
  }
}
