/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server;

import com.datastax.butler.api.ci.BuildImportRequest;

public class TestData {

  public static String TEST_SUITE = "some.Suite";

  public static BuildImportRequest.TestRun rawTestRun(
      String testCase, boolean failed, boolean skipped) {
    return new BuildImportRequest.TestRun(
        TEST_SUITE, testCase, null, null, failed, skipped, 10, null, null);
  }
}
