/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FailureDetailsTest {

  @Test
  void shouldBuildFromListOfRunDetails() {
    // given
    var now = Instant.now().toEpochMilli();
    var jobMain = new JobId(WorkflowId.of("ci"), Branch.fromString("main"));
    var job10 = new JobId(WorkflowId.of("ci"), Branch.fromString("1.0"));
    var runs =
        List.of(
            new RunDetails(
                new BuildId(jobMain, 1),
                TestVariant.DEFAULT,
                "http://jenkins.example.com/job/ci/main/1/test",
                now,
                true,
                false,
                null),
            new RunDetails(
                new BuildId(jobMain, 2),
                TestVariant.DEFAULT,
                "http://jenkins.example.com/job/ci/main/2/test",
                now + 100,
                false,
                false,
                null),
            new RunDetails(
                new BuildId(job10, 11),
                TestVariant.fromString("special"),
                "http://jenkins.example.com/job/ci/1.0/11/test",
                now + 2,
                true,
                false,
                null),
            new RunDetails(
                new BuildId(job10, 12),
                TestVariant.DEFAULT,
                "http://jenkins.example.com/job/ci/1.0/12/test",
                now + 10,
                false,
                true,
                null));
    // when
    var details = FailureDetails.build(runs);
    // then
    assertEquals(4, details.allRuns().size());
    assertEquals(2, details.failures());
    assertEquals(11, details.lastFailed().id().buildNumber());
    assertEquals(2, details.allByVariants().keySet().size());
    assertTrue(
        details
            .allByVariants()
            .keySet()
            .containsAll(List.of("special", TestVariant.DEFAULT.toString())));
    assertEquals(2, details.allByVersions().keySet().size());
    assertTrue(
        details
            .allByVersions()
            .keySet()
            .containsAll(
                List.of(BranchVersion.fromString("main"), BranchVersion.fromString("1.0"))));
  }
}
