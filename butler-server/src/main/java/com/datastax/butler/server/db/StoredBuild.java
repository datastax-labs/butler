/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.api.ci.BuildImportRequest;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import java.time.Instant;
import javax.annotation.Nullable;
import lombok.Value;

/** Represents a row in the {@link BuildsDb#TABLE} table. */
@Value
public class StoredBuild {
  long id;
  long jobId;
  int buildNumber;
  @Nullable String buildUrl;
  JenkinsBuild.Status status;
  Instant startTime;
  long durationMs;
  boolean usable;
  boolean fullyStored;
  long failedTests;
  long ranTests;
  long skippedTests;

  /**
   * Creates a {@link StoredBuild}} from a build retrieved on Jenkins.
   *
   * @param jobId the JOBS::ID of the job this is a build of.
   * @param build the Jenkins built for which to create the DTO.
   */
  public static StoredBuild from(long jobId, JenkinsBuild build) {
    return new StoredBuild(
        -1L,
        jobId,
        build.buildId().buildNumber(),
        build.url().toString(),
        build.status(),
        build.startTime(),
        build.duration().toMillis(),
        build.isUsable(),
        false,
        build.testSummary().failedTests(),
        build.testSummary().ranTests(),
        build.testSummary().skippedTests());
  }

  /**
   * Create new StoredBuild from raw build information.
   *
   * @param jobId db job id - should exist at this moment
   * @param build build data
   * @return stored build object, ready for db insert.
   */
  public static StoredBuild from(long jobId, BuildImportRequest build) {
    var status =
        build.numFailedTests() > 0 ? JenkinsBuild.Status.UNSTABLE : JenkinsBuild.Status.SUCCESS;
    return new StoredBuild(
        -1L,
        jobId,
        build.buildNumber(),
        build.url(),
        status,
        Instant.ofEpochSecond(build.startTime()),
        build.durationMs(),
        true,
        false,
        build.numFailedTests(),
        build.numTests(),
        build.numSkippedTests());
  }
}
