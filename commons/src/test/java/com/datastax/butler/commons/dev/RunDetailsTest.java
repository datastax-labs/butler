/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunDetailsTest {

  @Test
  void shouldClearOutput() {
    var run = FailuresTestData.failed;
    assertTrue(run.hasOutput());
    run.clearOutput();
    assertFalse(run.hasOutput());
  }

  @Test
  void shouldChooseMinMaxByTimestamp() {
    // given
    long t1 = Instant.now().toEpochMilli();
    long t2 = Instant.now().plusMillis(1000).toEpochMilli();
    // when
    var run1 = new RunDetails(FailuresTestData.buildId, null, null, t1, true, false, null);
    var run2 = new RunDetails(FailuresTestData.buildId, null, null, t2, true, false, null);
    // then
    assertEquals(run1, RunDetails.min(run1, run2));
    assertEquals(run1, RunDetails.min(run2, run1));
    assertEquals(run2, RunDetails.max(run1, run2));
    assertEquals(run2, RunDetails.max(run2, run1));
  }

  @Test
  void shouldExtractVersion() {
    // given
    var buildId = new BuildId(new JobId(WorkflowId.of("ci"), Branch.fromString("main")), 12);
    // when
    var run = new RunDetails(buildId, null, null, 0, true, false, null);
    // then
    assertTrue(run.extractVersion().isPresent());
    assertEquals("main", run.extractVersion().get().branch().toString());
  }
}
