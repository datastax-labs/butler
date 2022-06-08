/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestFailureTest {

  @Test
  void shouldReturnLastFailedOutput() {
    var failure = FailuresTestData.createFailure("PPFPFFSP");
    assertTrue(failure.hasFailed());
    assertTrue(failure.lastFailedOutput().isPresent());
  }

  @Test
  void shouldNotReturnOutputIfNoFailedRuns() {
    var failure = FailuresTestData.createFailure("PPPPSP");
    assertFalse(failure.hasFailed());
    assertFalse(failure.lastFailedOutput().isPresent());
  }
}
