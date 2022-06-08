/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.ci;

import com.datastax.butler.commons.jenkins.TestRunOutput;
import java.util.List;
import lombok.NonNull;
import lombok.Value;

/**
 * Request to import a RAW build using butler entities.
 *
 * <p>Special API that is bypassing workflow definitions (for translating jenkins to butler) and
 * instead accepts raw build information about tests, almost 1-1 using butler data model.
 *
 * <p>Rationale: to be able to implement external importers e.g. from github actions results.
 */
@Value
public class BuildImportRequest {
  @NonNull String workflow;
  @NonNull String branch;
  int buildNumber;
  long startTime;
  int durationMs;
  String url;
  List<TestRun> tests;

  @Value
  public static class TestRun {
    @NonNull String testSuite;
    @NonNull String testCase;
    String variant;
    String category;
    boolean failed;
    boolean skipped;
    int durationMs;
    String url;
    TestRunOutput output;
  }

  public long numTests() {
    return tests.size();
  }

  public long numFailedTests() {
    return tests.stream().filter(TestRun::failed).count();
  }

  public long numSkippedTests() {
    return tests.stream().filter(TestRun::skipped).count();
  }
}
