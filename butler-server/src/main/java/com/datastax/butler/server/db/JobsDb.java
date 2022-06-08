/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.google.common.annotations.VisibleForTesting;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles accesses to the Jenkins jobs known of the database (so mostly maintenance of the {@link
 * JobsDb#JOBS_TABLE} table).
 */
@Repository
public class JobsDb extends DbTableService {
  public static final String JOBS_TABLE = "jobs";

  private final Mapper<JobId> jobIdMapper =
      Mapper.builder(JobId.class)
          .column("workflow", WorkflowId.class, WorkflowId::toString, WorkflowId::of)
          .column("job_name", Branch.class, Branch::toString, Branch::fromString)
          .build();

  /** Use this to cache jobs we look up by ID during results processing. */
  private final Map<Long, Optional<JobId>> jobsCache;

  private final TableMapper<JobIdDto, Long> jobsMapper;

  private final UpstreamWorflowsDb upstreamWorflowsDb;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public JobsDb(NamedParameterJdbcTemplate template, UpstreamWorflowsDb upstreamWorflowsDb) {
    super(template, JOBS_TABLE);
    this.upstreamWorflowsDb = upstreamWorflowsDb;
    this.jobsMapper = tableMapper(JobIdDto.class, Long.class);
    this.jobsCache = new ConcurrentHashMap<>();
  }

  /**
   * Gets the database ID of the provided job if it is stored, or store it if it is unknown and
   * return the newly assigned ID.
   *
   * @param job the job for which to get the database ID (storing it if not already stored).
   * @return the database ID of {@code job}, which will be stored once this method return (either
   *     because it already was, or because if was inserted).
   */
  @Transactional
  public long dbId(JobId job) {
    return dbIdIfExists(job)
        .orElseGet(
            () -> {
              var workflow = upstreamWorflowsDb.getWorkflow(job.workflow());
              var category = workflow.map(x -> x.jobCategory(job.jobName().toString()));
              return jobsMapper.insert(
                  new JobIdDto(-1L, job, category.map(JobId.Category::toString).orElse(null)));
            });
  }

  /**
   * Return a list of all jobs that have been loaded to the db.
   *
   * @return a list of jobs
   */
  public List<JobId> getAll() {
    return jobsMapper.getAll().stream().map(JobIdDto::value).collect(Collectors.toList());
  }

  /**
   * Find a job by it's db id.
   *
   * @param jobId the db id
   * @return an optional actual jobid object
   */
  public Optional<JobId> getById(long jobId) {
    return jobsCache.computeIfAbsent(
        jobId,
        j -> {
          Optional<JobIdDto> item = jobsMapper.get(jobId);
          return item.map(Id::value);
        });
  }

  /**
   * Return list of JobID in the database for given workflow.
   *
   * @param workflowId workflow id
   * @return list of job ids for given workflow
   */
  public List<JobId> getByWorkflow(WorkflowId workflowId) {
    return jobsMapper.getWhere("workflow=:workflow", Map.of("workflow", workflowId.name())).stream()
        .map(JobIdDto::value)
        .collect(Collectors.toList());
  }

  /** Return all database jobs with given category e.g. UPSTREAM. */
  public List<JobId> getByCategory(JobId.Category category) {
    return jobsMapper.getWhere("category=:category", Map.of("category", category.toString()))
        .stream()
        .map(JobIdDto::value)
        .collect(Collectors.toList());
  }

  /** Return all known jobs we have in db for given branch (job_name). */
  public List<JobId> getByBranch(Branch branch) {
    return jobsMapper.getWhere("job_name=:branch", Map.of("branch", branch.toString())).stream()
        .map(JobIdDto::value)
        .collect(Collectors.toList());
  }

  /** Return "potential" upstream jobs based on the configured workflows. */
  public Set<JobId> getConfiguredUpstreamJobs() {
    Set<JobId> res = new HashSet<>();
    for (Workflow w : upstreamWorflowsDb.upstreamWorkflows()) {
      w.upstreamBranches().stream()
          .map(b -> w.workflowId().job(Branch.fromString(b)))
          .forEach(res::add);
    }
    return res;
  }

  /**
   * Gets the database ID of the provided job, if it is stored.
   *
   * @param job the job for which to retrieve the database ID.
   * @return an optional containing the database ID of {@code job} if it is already stored, or is
   *     empty otherwise.
   */
  public OptionalLong dbIdIfExists(JobId job) {
    return jobsMapper
        .getUnique(job, jobIdMapper)
        .map(j -> OptionalLong.of(j.id()))
        .orElse(OptionalLong.empty());
  }

  @VisibleForTesting
  public void dropAllJobsForWorkflow(WorkflowId workflowId) {
    var params = Map.of("workflow", workflowId.name());
    template.update(q("DELETE FROM %s WHERE workflow=:workflow", JOBS_TABLE), params);
  }

  /**
   * Simple class to represent a row in the {@link #JOBS_TABLE} table, which is essentially just a
   * {@link JobId} plus the database id and {@link JobId.Category} (the later being derived from the
   * {@link JobId} itself). SimpleFlatMapper needs this to work properly (and needs the additional
   * ctor and getter added here).
   *
   * <p>Also note that this is public because SimplFlatMapper is unhappy otherwise, but this class
   * is not meant to be used outside this class.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @SuppressWarnings("MissingOverride") // Apparently, lombok don't add them properly
  public static class JobIdDto extends Id<JobId> {
    String category;

    private JobIdDto(long id, JobId value, String category) {
      super(id, value);
      this.category = category;
    }

    public JobIdDto(long id, String workflow, String jobName, String category) {
      this(id, WorkflowId.of(workflow).job(Branch.fromString(jobName)), category);
    }

    public String workflow() {
      return value().workflow().toString();
    }

    public String jobName() {
      return value().jobName().toString();
    }
  }
}
