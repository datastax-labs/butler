/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.jenkins.JobId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A class to find interesting test ids.
 *
 * <p>Interesting tests are: * in the last 10 builds * part of build with less than 10% failure rate
 * * failed at least once in those builds
 */
@Value
public class InterestingTests {
  JobsDb jobsDb;
  BuildsDb buildsDb;
  TestRunsDb testRunsDb;
  JobId jobId;
  int numBuilds;
  List<StoredBuild> builds = new ArrayList<>();
  List<Long> testIds = new ArrayList<>();

  private static final Logger logger = LogManager.getLogger();

  InterestingTests(
      JobsDb jobsDb, BuildsDb buildsDb, TestRunsDb testRunsDb, JobId jobId, int numBuilds) {
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.testRunsDb = testRunsDb;
    this.jobId = jobId;
    this.numBuilds = numBuilds;
  }

  InterestingTests(JobsDb jobsDb, BuildsDb buildsDb, TestRunsDb testRunsDb, JobId jobId) {
    this(jobsDb, buildsDb, testRunsDb, jobId, 16);
  }

  /**
   * Return (and get, if not already loaded) the builds that will be used. Filters out builds in
   * which more than 10% of tests failed (potentially build errors)
   *
   * @return a list of builds
   */
  public List<StoredBuild> builds() {
    int numBuildsToReturn = this.numBuilds;
    if (builds.isEmpty()) {
      this.builds.addAll(
          buildsDb.recentUsableOf(jobsDb.dbId(jobId), 2 * numBuildsToReturn).stream()
              .limit(numBuildsToReturn)
              .collect(Collectors.toList()));
    }

    return Collections.unmodifiableList(builds);
  }

  /**
   * Return (and get, if not loaded) the ids of the iteresting tests.
   *
   * @return a list of test ids
   */
  public List<Long> testIds() {
    if (builds().isEmpty()) {
      logger.debug("builds is empty, no testIds.");
      return Collections.unmodifiableList(Collections.emptyList());
    }

    if (testIds.isEmpty()) {
      logger.info("populating testIds from builds: {}", builds);
      this.testIds.addAll(
          testRunsDb
              .getFailuresForBuilds(
                  builds().stream().map(StoredBuild::id).collect(Collectors.toList()))
              .stream()
              .map(StoredTestRun::testId)
              .collect(Collectors.toUnmodifiableList()));
    }
    return testIds;
  }
}
