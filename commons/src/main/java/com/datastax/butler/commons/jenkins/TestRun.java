/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import java.time.Duration;

/** Represent the run of a test on Jenkins. */
public class TestRun {
  private final TestId id;
  private final TestResult result;
  private final Duration duration;
  private final TestRunOutput output;
  private final String jenkinsClassName; // original jenkins class name from test report
  private final String jenkinsTestName; // original jenkins test name from test report

  /**
   * Creates a new test run.
   *
   * @param id the id of the test having been run.
   * @param result the result of this test run.
   * @param duration the duration of this test run.
   */
  public TestRun(
      TestId id,
      TestResult result,
      Duration duration,
      TestRunOutput output,
      String reportClassName,
      String reportTestName) {
    this.id = id;
    this.result = result;
    this.duration = duration;
    this.output = output;
    this.jenkinsClassName = reportClassName;
    this.jenkinsTestName = reportTestName;
  }

  /** The identifier of the test this is a run of. */
  public TestId id() {
    return id;
  }

  /** Whether the test failed in this run. */
  public boolean failed() {
    return result == TestResult.FAILED;
  }

  /** Whether the test passed in this run. */
  public boolean passed() {
    return result == TestResult.PASSED;
  }

  /** Whether the test was skipped in this run. */
  public boolean skipped() {
    return result == TestResult.SKIPPED;
  }

  /** The result of this test run. */
  public TestResult result() {
    return result;
  }

  /** The duration of this test run. */
  public Duration duration() {
    return duration;
  }

  /** Output including error details, stack trace, stdout and stderr. */
  public TestRunOutput output() {
    return output;
  }

  public String jenkinsClassName() {
    return jenkinsClassName;
  }

  public String getJenkinsTestName() {
    return jenkinsTestName;
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", result, duration);
  }
}
