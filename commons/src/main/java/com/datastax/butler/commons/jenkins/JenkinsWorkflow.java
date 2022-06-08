/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.datastax.butler.commons.dev.Workflow;
import java.util.Optional;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

/**
 * Represents a Jenkins Workflow that can translate jenkins data to butler data using workflow
 * scheme.
 */
public class JenkinsWorkflow {
  private final JenkinsClient jenkins;
  private final Workflow workflow;

  public JenkinsWorkflow(JenkinsClient jenkins, Workflow workflow) {
    this.jenkins = jenkins;
    this.workflow = workflow;
  }

  /**
   * Creates new JenkinsWorkflow for given workflow, reading credentials etc.
   *
   * @return empty if for any reason jenkins workflow cannot be created.
   */
  public static Optional<JenkinsWorkflow> forWorkflow(Workflow workflow) {
    if (StringUtils.isBlank(workflow.getJenkinsUrl())) {
      return Optional.empty();
    } else {
      return Optional.ofNullable(HttpUrl.parse(workflow.getJenkinsUrl()))
          .map(url -> JenkinsClient.createForUrl(url, workflow.getJenkinsUrlPath()))
          .map(j -> new JenkinsWorkflow(j, workflow));
    }
  }

  /** Return JenkinsBuild object translated from raw build using workflow. */
  public Optional<JenkinsBuild> getBuild(BuildId buildId) {
    var buildUrl = jenkins.urlScheme(buildId.jobId().workflow()).buildUrl(buildId);
    return getBuild(buildId, buildUrl);
  }

  /** Return JenkinsBuild object translated from raw build using workflow. */
  public Optional<JenkinsBuild> getBuild(BuildId buildId, HttpUrl buildUrl) {
    return jenkins.getRawBuild(buildUrl).map(x -> x.toBuild(this, buildId, buildUrl));
  }

  /** Return TestReport object parsed for given build. */
  public Optional<TestReport> getTestReport(BuildId buildId) {
    var buildUrl = jenkins.urlScheme(buildId.jobId().workflow()).buildUrl(buildId);
    return getTestReport(buildId, buildUrl);
  }

  /** Return TestReport object parsed from given build URL. */
  public Optional<TestReport> getTestReport(BuildId buildId, HttpUrl buildUrl) {
    var testReportUrl = JenkinsUrlScheme.addTestReport(buildUrl);
    return jenkins
        .getRawTestReport(testReportUrl)
        .map(x -> x.toReport(this, buildId, testReportUrl));
  }

  /**
   * Retrieve a Jenkins job.
   *
   * @param jobId the identifier for the job to retrieve.
   * @return an optional with the fetched job if it can be found, or a empty optional otherwise.
   */
  public Optional<JenkinsJob> getJob(JobId jobId) {
    var buildIds = jenkins.getJobBuildIds(jobId);
    return Optional.of(new JenkinsJob(this, jobId, buildIds));
  }

  public JenkinsClient jenkins() {
    return jenkins;
  }

  public Workflow workflow() {
    return workflow;
  }
}
