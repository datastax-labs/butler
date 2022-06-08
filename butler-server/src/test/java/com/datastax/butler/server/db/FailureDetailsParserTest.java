/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.jenkins.TestVariant;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

class FailureDetailsParserTest {

  static final FailureDetailsParser parser = new FailureDetailsParser();

  @Test
  void shouldParseEmptyVariant() {
    assertEquals(TestVariant.DEFAULT, parser.parseVariant(""));
  }

  @Test
  void shouldParseNonEmptyVariant() {
    assertEquals("some-variant", parser.parseVariant("some-variant").toString());
  }

  @Test
  void shouldParseFailedBuildFailureData() {
    // given
    var data =
        "ci-build|main|1199|1640169241|variant-x|worker-3|1|0|https://jenkins.example.com/job/ci-build/job/main/1199/testReport/ttl_test/TestDistributedTTL";
    var runDetails = parser.parseBuildFailureData(data);
    // then
    assertTrue(runDetails.failed());
    assertFalse(runDetails.skipped());
    assertEquals(1199, runDetails.id().buildNumber());
    assertEquals("main", runDetails.id().jobId().jobName().toString());
    assertEquals("ci-build", runDetails.id().jobId().workflow().name());
    assertEquals("variant-x", runDetails.variant().toString());
    assertFalse(runDetails.url().isEmpty());
  }

  @Test
  void shouldParseSkippedBuildFailureData() {
    // given
    var data =
        "ci-build|main|1199|1640169241|variant-x|worker-3|0|1|https://jenkins.example.com/job/ci-build/job/main/1199/testReport/ttl_test/TestDistributedTTL";
    var runDetails = parser.parseBuildFailureData(data);
    // then
    assertFalse(runDetails.failed());
    assertTrue(runDetails.skipped());
  }

  @Test
  void shouldParseBuildWithoutUrl() {
    // given
    var data = "ci-build|main|1199|1640169241|variant-x|worker-3|1|0";
    var runDetails = parser.parseBuildFailureData(data);
    // then
    assertTrue(runDetails.failed());
    assertFalse(runDetails.skipped());
    assertEquals(1199, runDetails.id().buildNumber());
    assertEquals("main", runDetails.id().jobId().jobName().toString());
    assertEquals("ci-build", runDetails.id().jobId().workflow().name());
    assertEquals("variant-x", runDetails.variant().toString());
    assertNull(runDetails.url());
  }

  @Test
  void shouldNotParseTooShortData() {
    // given
    var data = "ci-build|main|1199|1640169241|variant-x|worker-3|1";
    var runDetails = parser.parseBuildFailureData(data);
    // then
    assertNull(runDetails);
  }

  @Test
  void shouldParseListOfBuilds() {
    // given
    var rows =
        List.of(
            "ci-build|main|1199|1640169241|variant-x|worker-3|1|0",
            "ci-build|main|1199|1640169242|variant-y|worker-4|0|1");
    var data = StringUtils.join(rows, ",");
    var details = parser.parseFailureData(data);
    // then
    assertEquals(1, details.failures()); // only one failed
    assertEquals(2, details.allRuns().size());
  }
}
