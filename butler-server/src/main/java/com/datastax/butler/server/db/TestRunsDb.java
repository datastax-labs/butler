/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.jenkins.TestId;
import com.datastax.butler.commons.jenkins.TestReport;
import com.datastax.butler.commons.jenkins.TestRun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Handles accesses to the test runs in the database (so mostly maintenance of the {@link
 * TestRunsDb#TABLE} table).
 */
@Repository
public class TestRunsDb extends DbTableService {

  // inserts are done using multivalue inserts
  // BATCH_SIZE is just a number of rows to be inserted in single INSERT stmt
  private static final int BATCH_SIZE = 1024;

  public static final String TABLE = "test_runs";

  private final TableMapper<StoredTestRun, StoredTestRun.Key> testRunsMapper;

  private final TestNamesDb testNamesDb;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public TestRunsDb(NamedParameterJdbcTemplate template, TestNamesDb testNamesDb) {
    super(template, TABLE);
    this.testNamesDb = testNamesDb;
    this.testRunsMapper = tableMapper(StoredTestRun.class, StoredTestRun.Key.class);
  }

  /** Return all stored test runs for given build. */
  public List<StoredTestRun> getTestRunsForBuild(long buildDbId) {
    String whereClause = "build_id=:build_id";
    return testRunsMapper.getWhere(whereClause, Map.of("build_id", buildDbId));
  }

  /**
   * Get a list of failed runs for a set of builds.
   *
   * @param buildDbIds a collection of BUILD::ID
   * @return A list of test runs that failed in those builds
   */
  public List<StoredTestRun> getFailuresForBuilds(Collection<Long> buildDbIds) {
    String whereClause =
        String.format("failed=true AND build_id IN (%s)", idsToInClause(buildDbIds));
    return testRunsMapper.getWhere(whereClause, Collections.emptyMap()).stream()
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Get all results for a set of tests, in a set of builds.
   *
   * @param testDbIDs a collection of the TESTS::ID
   * @param buildDbIds a collection of the BUILDS::ID
   * @return A list of all results for those tests in those builds
   */
  public List<StoredTestRun> getAllResultsForTestsInBuilds(
      Collection<Long> testDbIDs, Collection<Long> buildDbIds) {
    if (testDbIDs.isEmpty()) {
      return Collections.emptyList();
    }
    String whereClause =
        String.format(
            "test_id IN (%s) AND build_id IN (%s)",
            idsToInClause(testDbIDs), idsToInClause(buildDbIds));
    return testRunsMapper.getWhere(whereClause, Collections.emptyMap());
  }

  /**
   * Inserts all the test run of the provided report.
   *
   * @param buildDbId the BUILDS::ID of the build this is the report of.
   * @param report the report to save.
   */
  public void insert(long buildDbId, TestReport report) {
    logger.info("Saving {} tests for {}", report.summary().totalTests(), report.buildId());
    long start = System.nanoTime();
    List<StoredTestRun> toSave = new ArrayList<>(report.allTestRuns().size());
    for (TestRun run : report.allTestRuns()) {
      var storedRun = fromTestReportTestRun(run, report, buildDbId);
      toSave.add(storedRun);
    }
    insertRuns(toSave);
    logger.info(
        "Inserting test runs done in {} seconds",
        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
  }

  private StoredTestRun fromTestReportTestRun(TestRun run, TestReport testReport, long buildDbId) {
    TestId id = run.id();
    long testId = testNamesDb.dbId(id.name());
    StoredTestRun storedRun =
        new StoredTestRun(
            testId,
            id.variant(),
            buildDbId,
            testReport.blockNames(id),
            run.failed(),
            run.skipped(),
            run.duration().toMillis(),
            testReport.testRunUrl(run));
    if (run.failed()) {
      storedRun.addFailureDetails(
          run.output().errorDetails(),
          run.output().errorStackTrace(),
          run.output().stdout(),
          run.output().stderr());
    }
    return storedRun;
  }

  /**
   * Insert provided TEST_RUNS rows in mulitple size-limited batches.
   *
   * @param testRuns list of test runs to insert
   */
  public void insertRuns(Iterable<StoredTestRun> testRuns) {
    List<StoredTestRun> batch = new ArrayList<>(BATCH_SIZE);
    testRuns.forEach(
        run -> {
          batch.add(run);
          if (batch.size() >= BATCH_SIZE) {
            testRunsMapper.insert(batch);
            batch.clear();
          }
        });
    if (!batch.isEmpty()) {
      testRunsMapper.insert(batch);
    }
  }

  /** Delete stored test runs e.g. before re-importing them. */
  public void deleteRuns(Collection<StoredTestRun> testRuns) {
    var keys = testRuns.stream().map(StoredTestRun::key).collect(Collectors.toList());
    testRunsMapper.delete(keys);
  }
}
