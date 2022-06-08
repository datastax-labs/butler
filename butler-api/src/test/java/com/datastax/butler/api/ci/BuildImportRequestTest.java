/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.ci;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildImportRequestTest {

  @Test
  void shouldCorrectlyCountTests() {
    // given
    var testRuns =
        List.of(
            testRun("case1", false, false),
            testRun("case2", true, false),
            testRun("case3", false, true),
            testRun("case4", true, false));
    // when
    var build = new BuildImportRequest("ci", "main", 1, 0, 40, null, testRuns);
    // then
    assertEquals(4, build.numTests());
    assertEquals(2, build.numFailedTests());
    assertEquals(1, build.numSkippedTests());
  }

  private BuildImportRequest.TestRun testRun(String testCase, boolean failed, boolean skipped) {
    return new BuildImportRequest.TestRun(
        "suite", testCase, null, null, failed, skipped, 10, null, null);
  }
}
