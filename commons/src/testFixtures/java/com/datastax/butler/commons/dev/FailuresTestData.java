/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.TestRunOutput;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.List;
import java.util.stream.Collectors;

public class FailuresTestData {

  static final JobId jobId = new JobId(WorkflowId.of("nightly"), Branch.fromString("main"));
  static final BuildId buildId = new BuildId(jobId, 1);

  static final RunDetails failed = runDetails(buildId, 'F');
  static final RunDetails passed = runDetails(buildId, 'P');
  static final RunDetails skipped = runDetails(buildId, 'S');

  /** Create new TestName object for com.datastax.TestClass.{testCase}. */
  public static TestName testName(String testCase) {
    return new TestName(TestCategory.UNKNOWN, "com.example", "TestClass", testCase);
  }

  /**
   * Create UpstreamFailures test object for given history of runs.
   *
   * @param story [PFS]+, P==passed, F==failed, S==skipped
   * @return UpstreamFailures history for com.datastax.TestClass.testCase;
   */
  public static TestFailure createFailure(String story) {
    var storyRuns =
        story.chars().mapToObj(c -> runDetails(buildId, (char) c)).collect(Collectors.toList());
    return testFailure("testCase", storyRuns);
  }

  /**
   * Create a failure for com.datastax.TestClass.{testCase} with given runs history.
   *
   * @param testCase test case name (just a method);
   * @param runs history of runs
   * @return UpstreamFailures.Failure object
   */
  public static TestFailure testFailure(String testCase, List<RunDetails> runs) {
    FailureDetails details = FailureDetails.build(runs);
    var workflowId =
        runs.stream()
            .findFirst()
            .map(RunDetails::id)
            .map(BuildId::jobId)
            .map(JobId::workflow)
            .orElse(WorkflowId.of("nightly"));
    return new TestFailure(
        testName(testCase), null, details, workflowId, runs.size(), runs.size(), 2L * runs.size());
  }

  /** Create test run details with given buildId and result {Passed, Failed, Skipped}. */
  public static RunDetails runDetails(BuildId buildId, Character c) {
    switch (c) {
      case 'P':
        return new RunDetails(buildId, TestVariant.DEFAULT, null, 0, false, false, null);
      case 'S':
        return new RunDetails(buildId, TestVariant.DEFAULT, null, 0, false, true, null);
      case 'F':
        var testRunOutput = new TestRunOutput("error details", "stack trace", "stdout", "stderr");
        return new RunDetails(buildId, TestVariant.DEFAULT, null, 0, true, false, testRunOutput);
      default:
        throw new IllegalArgumentException("Unrecognized " + c);
    }
  }
}
