/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import okhttp3.HttpUrl;

public class JenkinsUrlScheme {

  private final HttpUrl baseUrl;
  private final boolean isMultiBranch;

  public JenkinsUrlScheme(HttpUrl baseUrl, boolean isMultiBranch) {
    this.baseUrl = baseUrl;
    this.isMultiBranch = isMultiBranch;
  }

  /** Return pipeline/workflow url in jenkins. */
  public HttpUrl workflowUrl(WorkflowId workflowId) {
    return baseUrl.newBuilder().addPathSegment("job").addPathSegment(workflowId.name()).build();
  }

  /** Return job url, depending if it is multibranch or not. */
  public HttpUrl jobUrl(JobId jobId) {
    var url = workflowUrl(jobId.workflow());
    if (isMultiBranch) {
      return url.newBuilder()
          .addPathSegment("job")
          .addPathSegment(jobId.jobName().toString())
          .build();
    } else {
      return url;
    }
  }

  /** Return build url for given job and build number, multi-branch aware. */
  public HttpUrl buildUrl(BuildId buildId) {
    return jobUrl(buildId.jobId())
        .newBuilder()
        .addPathSegment(Integer.toString(buildId.buildNumber()))
        .build();
  }

  /** Return build test report url. */
  public HttpUrl buildTestReportUrl(BuildId buildId) {
    return addTestReport(buildUrl(buildId));
  }

  public static HttpUrl addTestReport(HttpUrl buildUrl) {
    return buildUrl.newBuilder().addPathSegment("testReport").build();
  }

  /** Return url for the build artifact of given name. */
  public HttpUrl buildArtifactUrl(BuildId buildId, String artifactName) {
    return buildUrl(buildId)
        .newBuilder()
        .addPathSegment("artifact")
        .addPathSegment(artifactName)
        .build();
  }
}
