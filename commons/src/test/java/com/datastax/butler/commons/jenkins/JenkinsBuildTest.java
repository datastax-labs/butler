/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.dev.JenkinsTestHelper.jenkinsBuild;
import static com.datastax.butler.commons.dev.JenkinsTestHelper.testReportSummary;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JenkinsBuildTest {

  @Test
  void isUsableShouldBeFalseForNullSummary() {
    assertFalse(jenkinsBuild(null).isUsable());
  }

  @Test
  void isUsableShouldUsePercentage() {
    // 60% of all tests need to run (either pass or fail) for a build to be usable
    assertFalse(jenkinsBuild(testReportSummary(0, 0, 0)).isUsable());
    assertTrue(jenkinsBuild(testReportSummary(1, 0, 10)).isUsable());
    assertTrue(jenkinsBuild(testReportSummary(10, 0, 10)).isUsable());
    assertFalse(jenkinsBuild(testReportSummary(0, 10, 10)).isUsable());
    assertFalse(jenkinsBuild(testReportSummary(0, 5, 10)).isUsable());
    assertTrue(jenkinsBuild(testReportSummary(0, 4, 10)).isUsable());
  }
}
