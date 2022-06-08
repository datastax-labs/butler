/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static com.datastax.butler.commons.dev.JenkinsTestHelper.jenkinsBuild;
import static com.datastax.butler.commons.dev.JenkinsTestHelper.testReportSummary;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkflowChecksTest {

  @Test
  public void shouldSkipIfNotEnoughTests() {
    var skipCheck = new WorkflowChecks.SkipJenkinsBuildWithNotEnoughTests(3);
    var build = jenkinsBuild(testReportSummary(0, 0, 2));
    assertTrue(skipCheck.skip(build));
  }

  @Test
  public void shouldNotSkipIfEnoughTests() {
    var skipCheck = new WorkflowChecks.SkipJenkinsBuildWithNotEnoughTests(3);
    var build = jenkinsBuild(testReportSummary(0, 0, 3));
    assertFalse(skipCheck.skip(build));
  }
}
