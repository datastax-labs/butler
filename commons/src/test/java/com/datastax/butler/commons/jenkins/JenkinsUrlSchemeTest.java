/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.dev.Branch;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

class JenkinsUrlSchemeTest {

  HttpUrl baseUrl = HttpUrl.parse("https://jenkins.example.com");
  JobId jobId = new JobId(WorkflowId.of("fast-ci"), Branch.fromString("trunk"));
  BuildId buildId = new BuildId(jobId, 13);

  @Test
  void testSimpleWorkflow() {
    var scheme = new JenkinsUrlScheme(baseUrl, false);
    assertEquals("https://jenkins.example.com/job/fast-ci", scheme.jobUrl(jobId).toString());
    assertEquals("https://jenkins.example.com/job/fast-ci/13", scheme.buildUrl(buildId).toString());
    assertEquals(
        "https://jenkins.example.com/job/fast-ci/13/testReport",
        scheme.buildTestReportUrl(buildId).toString());
    assertEquals(
        "https://jenkins.example.com/job/fast-ci/13/artifact/logs.xz",
        scheme.buildArtifactUrl(buildId, "logs.xz").toString());
  }

  @Test
  void testMultiBranchWorkflow() {
    var scheme = new JenkinsUrlScheme(baseUrl, true);
    assertEquals(
        "https://jenkins.example.com/job/fast-ci/job/trunk", scheme.jobUrl(jobId).toString());
    assertEquals(
        "https://jenkins.example.com/job/fast-ci/job/trunk/13",
        scheme.buildUrl(buildId).toString());
    assertEquals(
        "https://jenkins.example.com/job/fast-ci/job/trunk/13/testReport",
        scheme.buildTestReportUrl(buildId).toString());
    assertEquals(
        "https://jenkins.example.com/job/fast-ci/job/trunk/13/artifact/logs.xz",
        scheme.buildArtifactUrl(buildId, "logs.xz").toString());
  }
}
