/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.jenkins.TestRunOutput;
import com.datastax.butler.commons.jenkins.TestVariant;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@AllArgsConstructor
public class StoredTestRun {
  long testId;
  TestVariant variant;
  long buildId;
  String runBlocks;
  boolean failed;
  boolean skipped;
  long durationMs;
  @Nullable String runUrl;
  // special set of fields that are set for failed tests only
  @Nullable @NonFinal String errorDetails;
  @Nullable @NonFinal String errorStackTrace;
  @Nullable @NonFinal String stdout;
  @Nullable @NonFinal String stderr;

  public StoredTestRun(
      long testId,
      TestVariant variant,
      long buildId,
      String runBlocks,
      boolean failed,
      boolean skipped,
      long durationMs,
      String runUrl) {
    this(
        testId,
        variant,
        buildId,
        runBlocks,
        failed,
        skipped,
        durationMs,
        runUrl,
        null,
        null,
        null,
        null);
  }

  public void addFailureDetails(String details, String stackTrace, String stdout, String stderr) {
    this.errorDetails = nullifyBlank(details);
    this.errorStackTrace = nullifyBlank(stackTrace);
    this.stdout = nullifyBlank(stdout);
    this.stderr = nullifyBlank(stderr);
  }

  TestRunOutput output() {
    if (errorDetails == null && errorStackTrace == null && stdout == null && stderr == null) {

      return TestRunOutput.EMPTY_OUTPUT;
    } else {
      return new TestRunOutput(errorDetails, errorStackTrace, stdout, stderr);
    }
  }

  void clearOutput() {
    addFailureDetails(null, null, null, null);
  }

  private String nullifyBlank(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  @Value
  static class Key {
    long testId;
    TestVariant variant;
    long buildId;
  }

  Key key() {
    return new Key(testId, variant, buildId);
  }
}
