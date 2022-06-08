/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.api.ci.BuildImportRequest;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles accesses to the Jenkins jobs known of the database (so mostly maintenance of the {@link
 * JobsDb#TABLE} table).
 */
@Repository
public class BuildsDb extends DbTableService {
  public static final String TABLE = "builds";

  private final Mapper<BuildDbId> buildDbIdMapper =
      Mapper.create(BuildDbId.class, "job_id", "build_number");

  private final TableMapper<StoredBuild, Long> buildsMapper;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public BuildsDb(NamedParameterJdbcTemplate template) {
    super(template, TABLE);
    this.buildsMapper = tableMapper(StoredBuild.class, Long.class);
  }

  /**
   * Inserts the provided build from Jenkins.
   *
   * @param jobDbId the JOBS::ID of the job this is a build of (which imply said job row exists).
   * @param build the build to insert.
   * @return the BUILDS::ID of the newly inserted build.
   */
  public long insert(long jobDbId, JenkinsBuild build) {
    return buildsMapper.insert(StoredBuild.from(jobDbId, build));
  }

  public long insert(long jobDbId, BuildImportRequest build) {
    return buildsMapper.insert(StoredBuild.from(jobDbId, build));
  }

  /**
   * Sets the 'fully_stored' column to true for the provided build.
   *
   * @param buildId the database ID of the build to update (that build must exists, which is usually
   *     implied by the fact that we have it's database ID, but it shouldn't have been deleted).
   */
  public void markBuildStored(long buildId) {
    template.update(
        String.format("UPDATE %s SET fully_stored=true WHERE id=:id", TABLE),
        Map.of("id", buildId));
  }

  /**
   * Retrieves all the builds stored for the provided job.
   *
   * @param jobDbId the database ID of the job for which to retrieve builds.
   * @return a list of the all the builds stored for {@code jobId}.
   */
  public List<StoredBuild> allOf(long jobDbId) {
    return buildsMapper.getWhere("job_id=:id", Map.of("id", jobDbId));
  }

  /**
   * Return a list of recent builds for a job.
   *
   * @param jobDbId the JOBS::ID of the job
   * @param limit how many to return
   */
  public List<StoredBuild> recentOf(long jobDbId, int limit) {
    String whereClause = "job_id=:id ORDER BY start_time DESC LIMIT " + limit;
    return buildsMapper.getWhere(whereClause, Map.of("id", jobDbId));
  }

  /**
   * Return a list of recent builds for a job.
   *
   * @param jobDbId the JOBS::ID of the job
   * @param limit how many to return
   * @return list of stored builds
   */
  public List<StoredBuild> recentUsableOf(long jobDbId, int limit) {
    String whereClause = "job_id=:id AND usable=1 ORDER BY start_time DESC LIMIT " + limit;
    return buildsMapper.getWhere(whereClause, Map.of("id", jobDbId));
  }

  /**
   * Return a list of USABLE builds for a job since given timestamp.
   *
   * @param jobDbId the JOBS::ID of the job
   * @param start timestamp from which to search for
   * @return list of stored builds that have USABLE=1
   */
  public List<StoredBuild> usableSince(long jobDbId, Instant start) {
    String whereClause =
        "job_id=:id AND usable=1 "
            + "AND unix_timestamp(start_time)>:start_time "
            + "ORDER BY start_time";
    return buildsMapper.getWhere(
        whereClause, Map.of("id", jobDbId, "start_time", start.toEpochMilli() / 1000));
  }

  /**
   * Retrieve a stored build given it's job and build number.
   *
   * @param jobDbId the database ID of the job of the build to retrieve.
   * @param buildNumber the number of the build to retrieve.
   * @return an optional with the information stored on the build if it is store, or an empty
   *     otherwise if the build is not in the database.
   */
  public Optional<StoredBuild> getByBuildNumber(long jobDbId, int buildNumber) {
    return buildsMapper.getUnique(new BuildDbId(jobDbId, buildNumber), buildDbIdMapper);
  }

  /** Retrieve using db key. */
  public Optional<StoredBuild> get(long dbId) {
    return buildsMapper.get(dbId);
  }

  /**
   * Returns list of all builds in which given testId failed.
   *
   * @param testId test ID from stored tests
   * @return list of stored builds
   */
  public List<StoredBuild> getAllFailedBuildsForTestId(long testId) {
    String whereClause =
        String.format(
            "id IN (select build_id from %s where test_id=:test_id and failed=true)",
            TestRunsDb.TABLE);
    return buildsMapper.getWhere(whereClause, Map.of("test_id", testId));
  }

  @Transactional
  public void deleteByBuildNumberIfExists(long jobId, int buildNumber) {
    getByBuildNumber(jobId, buildNumber).ifPresent(b -> delete(b.id()));
  }

  /** Deletes a build given it's database ID. */
  public void delete(long buildDbId) {
    template.update(q("DELETE FROM %s WHERE id=%d", TABLE, buildDbId), Map.of());
  }

  /** Update summary fields for ran, failed, skipped tests in the stored build. */
  public boolean updateBuildSummary(
      long buildDbId, long ranCount, long failedCount, long skippedCount) {
    int res =
        template.update(
            q(
                "UPDATE %s SET ran_tests=:ran, "
                    + "failed_tests=:failed, skipped_tests=:skipped WHERE id=:id",
                TABLE),
            Map.of(
                "id", buildDbId, "ran", ranCount, "failed", failedCount, "skipped", skippedCount));
    return res > 0;
  }

  /** Key uniquely identifying a build in the database. */
  @Value
  public static class BuildDbId {
    long jobId;
    int buildNumber;
  }
}
