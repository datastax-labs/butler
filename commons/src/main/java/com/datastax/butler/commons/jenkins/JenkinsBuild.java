/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.jenkins.TestReport.Summary;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;

/** Represents an immutable view of a Jenkins build. */
public class JenkinsBuild {

  /** The status a build can be in. */
  public enum Status {
    /** The build has not been "built" and no results will be available. */
    NOT_BUILD,
    /** The build is still running and no results will be yet available. */
    RUNNING,
    /** The build was aborted and no results will be available. */
    ABORTED,
    /** The build finished successfully and has no test failure. */
    SUCCESS,
    /** The build finished without encountering any task error but has test failures. */
    UNSTABLE,
    /** The build finished but encountered some task failures (some tests haven't ran). */
    FAILURE;

    /**
     * Whether the test ran, completed and executed all tests (did not encounter an unexpected error
     * during a task execution).
     *
     * <p>This basically return whether the build has test results available and those are
     * meaningful.
     */
    public boolean ranAllTests() {
      return this == SUCCESS || this == UNSTABLE;
    }

    /** Whether the test ran and completed. */
    public boolean isCompleted() {
      return this != NOT_BUILD && this != RUNNING && this != ABORTED;
    }

    /** Return true if build is still in progress. */
    public boolean isRunning() {
      return this == RUNNING;
    }

    public boolean isAborted() {
      return this == ABORTED;
    }

    public boolean isBroken() {
      return this == ABORTED || this == FAILURE;
    }
  }

  private final JenkinsWorkflow jenkinsWorkflow;
  private final BuildId buildId;
  private final HttpUrl buildUrl;
  private final Status status;
  private final Instant startTime;
  private final @Nullable Duration duration;
  private final @Nullable Duration estimatedDuration;
  private final @Nullable TestReport.Summary testSummary;
  private final ImmutableMap<String, BuildData> buildData;

  /** Create jenkins build with description and build data details. */
  public JenkinsBuild(
      JenkinsWorkflow jenkinsWorkflow,
      BuildId buildId,
      HttpUrl buildUrl,
      Status status,
      Instant startTime,
      @Nullable Duration duration,
      @Nullable Duration estimatedDuration,
      @Nullable TestReport.Summary testSummary,
      ImmutableMap<String, BuildData> buildData) {
    this.jenkinsWorkflow = jenkinsWorkflow;
    this.buildId = buildId;
    this.buildUrl = buildUrl;
    this.status = status;
    this.startTime = startTime;
    this.duration = duration;
    this.estimatedDuration = estimatedDuration;
    this.testSummary = testSummary;
    this.buildData = buildData;
  }

  /** The ID (workflow, job name and build number) of this build. */
  public BuildId buildId() {
    return buildId;
  }

  /** The status of the build at the time this was fetched. */
  public Status status() {
    return status;
  }

  public HttpUrl url() {
    return buildUrl;
  }

  public JenkinsWorkflow jenkinsWorkflow() {
    return jenkinsWorkflow;
  }

  /**
   * The time the build took if it is finished, or the duration it will take as estimated by
   * Jenkins.
   *
   * @return the duration of the build if finished, or it's estimated duration if it is running and
   *     that estimate is available. If neither is true, {@link Duration#ZERO}.
   */
  public Duration duration() {
    if (duration != null) {
      return duration;
    }
    return estimatedDuration == null ? Duration.ZERO : estimatedDuration;
  }

  /** The instant at which the build was started. */
  public Instant startTime() {
    return startTime;
  }

  /**
   * If the run completed and some tests results are available, a summary of those results.
   *
   * @return a summary of the tests if available, or {@link TestReport.Summary#EMPTY} otherwise.
   */
  public TestReport.Summary testSummary() {
    return testSummary == null ? Summary.EMPTY : testSummary;
  }

  public Optional<TestReport> getTestReport() {
    if (buildUrl != null) return jenkinsWorkflow.getTestReport(buildId, buildUrl);
    else return jenkinsWorkflow.getTestReport(buildId);
  }

  /**
   * The raw build data information on all the software used during this build.
   *
   * @return a map keyed by software names and whose values is the corresponding {@link BuildData}.
   */
  public Map<String, BuildData> buildData() {
    return buildData;
  }

  // FIXME: this should be the Workflow decision
  /**
   * Whether the build has completed and has run all the tests it was configured to (meaning, this
   * excludes builds where too many Jenkins sub-tasks failed).
   */
  public boolean isUsable() {
    if (testSummary() != null && testSummary().totalTests() > 0) {
      return ((double) testSummary().ranTests() / testSummary().totalTests()) >= 0.60;
    } else {
      return false;
    }
  }

  public String infoString() {
    return String.format("Build %d: ", buildId.buildNumber()) + testSummary().fullInfoString();
  }

  @Override
  public String toString() {
    return String.format("%s: %s", buildId, status);
  }

  /**
   * Data on a software that was used for that build, that is essentially which exact commit of that
   * software was used for this build.
   */
  public static class BuildData {
    private final String name;
    private final Branch branch;
    private final String repository;
    private final String sha1;

    BuildData(String name, Branch branch, String repository, String sha1) {
      this.name = name;
      this.branch = branch;
      this.repository = repository;
      this.sha1 = sha1;
    }

    /** The (github) name of the software this is the build data of. */
    public String name() {
      return name;
    }

    /** The branch of {@link #name} that was used for the build this is the data of. */
    public Branch branch() {
      return branch;
    }

    /**
     * The name of the (github) repository for {@link #name} that was used for the build this is the
     * data of.
     */
    public String repository() {
      return repository;
    }

    /** The SHA1 of the commit of {@link #name} that was used for the build this is the data of. */
    public String commitSha1() {
      return sha1;
    }
  }
}
