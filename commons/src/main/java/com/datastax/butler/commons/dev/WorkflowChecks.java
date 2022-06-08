/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.JenkinsBuild;

public class WorkflowChecks {

  /**
   * Interface taking JenkinsBuild and making decision if it should be skipped. Some workflows may
   * want to skip builds that are FAILURE or builds that are not having enough runs or taking too
   * long etc.
   */
  public interface SkipJenkinsBuildCheck {
    boolean skip(JenkinsBuild jenkinsBuild);
  }

  /** Skips builds with not enough test runs. */
  public static class SkipJenkinsBuildWithNotEnoughTests implements SkipJenkinsBuildCheck {

    private final long minTestRuns;

    public SkipJenkinsBuildWithNotEnoughTests(long minTestRuns) {
      this.minTestRuns = minTestRuns;
    }

    @Override
    public boolean skip(JenkinsBuild jenkinsBuild) {
      return jenkinsBuild.testSummary().ranTests() < minTestRuns;
    }
  }
}
