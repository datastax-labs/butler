/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Represents an immutable view of a specific Job on Jenkins (one that contains builds). */
public class JenkinsJob {
  private final JenkinsWorkflow jenkinsWorkflow;
  private final JobId jobId;
  /** All the build ids for this job, in "decreasing" order (most recent build first). */
  private final List<BuildId> buildIds;

  private final JenkinsBuild[] cachedBuilds;

  JenkinsJob(JenkinsWorkflow jenkinsWorkflow, JobId jobId, List<BuildId> buildIds) {
    this.jenkinsWorkflow = jenkinsWorkflow;
    this.jobId = jobId;
    List<BuildId> sortedIds = new ArrayList<>(buildIds);
    sortedIds.sort(Comparator.<BuildId>naturalOrder().reversed());
    this.buildIds = Collections.unmodifiableList(sortedIds);
    this.cachedBuilds = new JenkinsBuild[this.buildIds.size()];
  }

  /** The ID (workflow and job name) of this job. */
  public JobId jobId() {
    return jobId;
  }

  /**
   * Get a build by it's internal index into {@link #buildIds} and {@link #cachedBuilds} (and so
   * <b>not</b> its external build number.
   *
   * @return the build whose build number is {@code buildNumbers[i]}. This "can" be {@code null} if
   *     the corresponding build cannot be found, indicating that the build has been removed from
   *     Jenkins since the time at which we retrieved the build numbers, which should be rare but
   *     cannot be entirely excluded.
   */
  private @Nullable JenkinsBuild getBuildByIdx(int i) {
    JenkinsBuild build = cachedBuilds[i];
    if (build == null) {
      // Fetching builds is a bit costly, so avoid doing it twice if this is called by threads
      // concurrently.
      synchronized (this) {
        build = cachedBuilds[i];
        if (build == null) {
          build = jenkinsWorkflow.getBuild(buildIds.get(i)).orElse(null);
          cachedBuilds[i] = build;
        }
      }
    }
    return build;
  }

  /**
   * The last build of this job that matches the provided predicate.
   *
   * @param predicate predicate selecting builds.
   * @return an optional with the last (most recent) build on this job that matches {@code
   *     predicate} or an empty optional if there is not such build.
   */
  public Optional<JenkinsBuild> lastBuild(Predicate<JenkinsBuild> predicate) {
    for (int i = 0; i < buildIds.size(); i++) {
      JenkinsBuild build = getBuildByIdx(i);
      if (build != null && predicate.test(build)) {
        return Optional.of(build);
      }
    }
    return Optional.empty();
  }

  /**
   * The list of all the builds (only their IDs) for this job in decreasing order (from the most
   * recent to the least one).
   */
  public List<BuildId> allBuildIds() {
    return buildIds;
  }

  @Override
  public String toString() {
    return jobId.toString();
  }
}
