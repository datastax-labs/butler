/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.tools;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JenkinsException;
import com.datastax.butler.commons.jenkins.JenkinsJob;
import com.datastax.butler.commons.jenkins.JenkinsWorkflow;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.StoredBuild;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.BuildsService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.FormatMethod;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.UuidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Loads all the builds (or a subset) for a number of Jenkins job.
 *
 * <p>The builds are loaded from Jenkins and stored in the database if they aren't already. Loader
 * uses executors to download multiple jobs concurrently, with the limitation that there can be only
 * one download per JobId in progress (single submitted LoadTask per JobId).
 *
 * <p>Loading tasks are identified by UUIDs. Status for every task is kept during loading and after
 * it when loading is already finished, so that a status api call can be done also for finished
 * tasks.
 */
@Component
public class BuildLoader {
  private static final Logger logger = LogManager.getLogger();

  private final JobsDb jobsDb;
  private final BuildsDb buildsDb;
  private final UpstreamWorflowsDb workflowsDb;
  private final BuildsService buildsService;
  private final ExecutorService executors;
  private final ConcurrentMap<JobId, UUID> inProgress = Maps.newConcurrentMap();
  private final ConcurrentMap<UUID, Status> statuses = Maps.newConcurrentMap();

  /** Creates a new {@link BuildLoader} instance (Autowired by Spring). */
  @Autowired
  public BuildLoader(
      JobsDb jobsDb,
      BuildsDb buildsDb,
      BuildsService buildsService,
      UpstreamWorflowsDb workflowsDb) {
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.buildsService = buildsService;
    this.workflowsDb = workflowsDb;
    this.executors = Executors.newFixedThreadPool(5);
  }

  /** Creates a new new {@link BuildLoader} instance. */
  @VisibleForTesting
  public BuildLoader(
      JobsDb jobsDb,
      BuildsDb buildsDb,
      BuildsService buildsService,
      UpstreamWorflowsDb workflowsDb,
      ExecutorService executors) {
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.workflowsDb = workflowsDb;
    this.buildsService = buildsService;
    this.executors = executors;
  }

  /**
   * Default max number of builds loaded per job.
   *
   * <p>32 is arbitrary number, but for PR builds usually this will be few, for FastCI it will cover
   * 8 days, for FullCI a month
   *
   * @return number of builds to load limit
   */
  public int defaultMaxBuildsPerJob() {
    return 32;
  }

  /**
   * Submits load for given jobId if such load is not in progress.
   *
   * @param jobToLoad JobId to load
   * @param maxBuilds max number of recent builds to load
   * @return loadTaskId (Instant)
   */
  public UUID submitLoad(JobId jobToLoad, int maxBuilds) {
    return inProgress.computeIfAbsent(
        jobToLoad,
        k -> {
          UUID taskId = UuidUtil.getTimeBasedUuid();
          statuses.put(taskId, Status.notStarted(taskId, jobToLoad));
          LoadTask task = new LoadTask(taskId, jobToLoad, maxBuilds);
          executors.submit(task);
          return taskId;
        });
  }

  /**
   * Periodically fetch builds for jobs considered UPSTREAM. with whatever jenkins knows about.
   *
   * <p>Based on the content of the JOBS table in the database.
   */
  @SuppressWarnings("unused")
  @Scheduled(fixedDelay = 1000 * 60 * 30) // 30 minutes
  public void fetchUpstreamJobs() {
    logger.info("Fetching upstream jobs");
    var toFetch = jobsDb.getConfiguredUpstreamJobs();
    for (JobId job : toFetch) {
      UUID id = submitLoad(job, defaultMaxBuildsPerJob());
      logger.info("Submitted fetch for {} with id {}", job, id);
    }
  }

  /**
   * Remove task statuses updated more than 1h ago
   *
   * <p>Statuses are inserted per load task started, then updated. We do not remove them as api call
   * may want to retrieve status after loading task is finished. It is then required to clean up
   * periodically.
   */
  @SuppressWarnings("unused")
  @Scheduled(fixedDelay = 1000 * 60 * 15) // every 15 minutes
  public void cleanupFinishedTaskStatuses() {
    Instant updateThreshold = Instant.now().minusSeconds(60 * 60L);
    try {
      logger.info("Cleaning up finished loading task statuses. Size before = {}", statuses.size());
      boolean removed =
          statuses
              .entrySet()
              .removeIf(
                  e ->
                      e.getValue().finished()
                          && e.getValue().updateTimestamp.isBefore(updateThreshold));
      logger.info(
          "Cleaning up finished loading task statuses result: {}. Size after = {}",
          removed,
          statuses.size());
    } catch (Exception ex) {
      logger.error(
          "Cleaning up old finished task statuses hit the problem: {}", ex.getCause().getMessage());
    }
  }

  /**
   * Information about loading status for recently submited load tasks
   *
   * <p>This will include all currently "kept" statuses, both finished and in progress. Statuses are
   * periodically pruned to not keep old ones.
   */
  public List<Status> allStatus() {
    return new ArrayList<>(statuses.values());
  }

  public Optional<Status> taskStatus(UUID taskId) {
    return Optional.ofNullable(statuses.get(taskId));
  }

  private void updateStatus(UUID taskId, Status status) {
    statuses.put(taskId, status);
  }

  /** Status of a single loading task. */
  @Accessors(fluent = true)
  @Data
  public static class Status {
    private final UUID taskId;
    private final JobId jobId;
    private final boolean started;
    private final boolean finished;
    private final int progressPercentage;
    private final Duration duration;
    private final @Nullable String error;
    private final List<String> messages;
    private final Instant updateTimestamp;

    private Status(
        UUID taskId,
        JobId jobId,
        boolean started,
        boolean finished,
        int progressPercentage,
        Duration duration,
        @Nullable String error,
        List<String> messages,
        Instant updateTimestamp) {
      this.taskId = taskId;
      this.jobId = jobId;
      this.started = started;
      this.finished = finished;
      this.progressPercentage = progressPercentage;
      this.duration = duration;
      this.error = error;
      this.messages = messages;
      this.updateTimestamp = updateTimestamp;
    }

    /** Return status denoting that load task is scheduled but not yet started. */
    public static Status notStarted(UUID taskId, JobId jobId) {
      return new Status(
          taskId,
          jobId,
          false,
          false,
          0,
          Duration.ZERO,
          null,
          Collections.emptyList(),
          Instant.now());
    }
  }

  /**
   * LoadTask represents task of loading single Job.
   *
   * <p>Objects of this class represents work that is expected to be done by single thread.* Work
   * schema: - retrieve builds to load - update status (with information about what needs to be
   * done) - start loading builds one by one, updating status after each downloaded build - update
   * status with final information
   */
  @NotThreadSafe
  private class LoadTask implements Runnable {
    private final UUID taskId;
    private final JobId jobToLoad;
    private final int maxBuilds;
    private long startTimeNanos;

    private final Set<BuildId> recentBuilds = Sets.newHashSet();
    private int totalBuildsCount = 0;
    private int loadedBuildsCount = 0;

    private volatile boolean finished = false;
    private volatile String error;
    private final List<String> messages = Lists.newArrayList();

    private LoadTask(UUID taskId, JobId jobToLoad, int maxBuilds) {
      this.taskId = taskId;
      this.jobToLoad = jobToLoad;
      this.maxBuilds = maxBuilds;
    }

    private boolean isStarted() {
      return startTimeNanos > 0;
    }

    private void finishWithNoBuilds() {
      logger.info("No builds found for loading job {}", jobToLoad);
      addMessage("No builds found");
      finished = true;
      updateStatus(taskId, currentStatus());
    }

    private void finishWithError(String error) {
      logger.error("Problem when loading job {}: {}", jobToLoad, error);
      addMessage("Problem when loading: %s", error);
      this.error = error;
      finished = true;
      updateStatus(taskId, currentStatus());
    }

    private void finishWithSuccess() {
      finished = true;
      updateStatus(taskId, currentStatus());
    }

    @Override
    public void run() {
      try {
        startTimeNanos = System.nanoTime();
        // retrieve list of builds to fetch, if empty exist with status NOOP
        List<BuildId> buildIds = retrieveBuildsToFetch();
        if (buildIds.isEmpty()) {
          finishWithNoBuilds();
          return;
        }
        logger.debug("Recent builds for job {}: {}", jobToLoad, buildIds);

        // make sure that job is already created in the database
        long jobDbId = jobsDb.dbId(jobToLoad);
        if (jobDbId <= 0) {
          finishWithError("Cannot find job db id for jenkins job");
          return;
        }
        // start work
        totalBuildsCount = buildIds.size();
        for (BuildId build : buildIds) {
          long startTime = System.currentTimeMillis();
          addMessage("downloading build #%s", build);
          updateStatus(taskId, currentStatus());
          loadBuild(jobDbId, build);
          long duration = System.currentTimeMillis() - startTime;
          addMessage(
              "downlading build #%s finished in %d seconds",
              build, TimeUnit.MILLISECONDS.toSeconds(duration));
        }
        finishWithSuccess();
      } catch (JenkinsException e) {
        if (e.getCause() instanceof UnknownHostException) {
          finishWithError("Cannot find Jenkins host. Are you within the DataStax VPN?");
        } else {
          finishWithError(
              String.format("Problem connecting to Jenkins: %s", e.getCause().getMessage()));
        }
      } catch (Exception e) {
        finishWithError(
            String.format(
                "Unexpected uncaught error during load task execution: %s",
                e.getCause().getMessage()));
      } finally {
        logger.info("Loading job {} with id {} finished.", jobToLoad, taskId);
        inProgress.remove(jobToLoad);
      }
    }

    @FormatMethod
    private void addMessage(String fmt, Object... args) {
      String msg = String.format(fmt, args);
      this.messages.add(msg);
    }

    private List<BuildId> retrieveBuildsToFetch() {
      var workflowDef = workflowsDb.getWorkflow(jobToLoad.workflow());
      if (workflowDef.isEmpty()) {
        addMessage("Not loading job %s (unknown workflow %s)", jobToLoad, jobToLoad.workflow());
        return Collections.emptyList();
      }
      if (StringUtils.isBlank(workflowDef.get().getJenkinsUrl())) {
        addMessage(
            "Not loading job %s (no jenkins in workflow %s)", jobToLoad, jobToLoad.workflow());
        return Collections.emptyList();
      }

      var jenkinsWorkflow = JenkinsWorkflow.forWorkflow(workflowDef.get());
      // Fetch the job information from Jenkins
      Optional<JenkinsJob> jobOpt = jenkinsWorkflow.flatMap(x -> x.getJob(jobToLoad));
      if (jobOpt.isEmpty()) {
        addMessage("Not loading job %s: it cannot be found", jobToLoad);
        return Collections.emptyList();
      }

      JenkinsJob job = jobOpt.get();
      job.lastBuild(JenkinsBuild::isUsable).ifPresent(b -> recentBuilds.add(b.buildId()));
      List<BuildId> buildIds =
          job.allBuildIds().stream().limit(maxBuilds).collect(Collectors.toList());

      // check if we should create new job in the database (in case it was not yet there)
      // please notice it is safe, as it impacts what will be loaded (all vs select builds)
      boolean wasCreated = jobsDb.dbIdIfExists(jobToLoad).isEmpty();
      long jobDbId = jobsDb.dbId(jobToLoad);

      // build list of builds to fetch from jenkins
      List<BuildId> toFetch;
      if (wasCreated) {
        // this is new, not yet loaded job -> we will fetch all builds
        toFetch = buildIds;
      } else {
        // do not download builds that are already downloaded from jenkins
        Set<BuildId> loadedBuilds = filterLoadedAndPurgePartial(jobToLoad, jobDbId);
        toFetch =
            buildIds.stream()
                .filter(b -> !loadedBuilds.contains(b))
                .collect(Collectors.toUnmodifiableList());
      }
      if (toFetch.isEmpty()) {
        addMessage("Skipping job, all builds to load are already loaded.");
      } else {
        addMessage(
            "Loading %d builds for job %s (skipping %d build already loaded)",
            toFetch.size(), jobToLoad, buildIds.size() - toFetch.size());
      }
      return toFetch;
    }

    private Set<BuildId> filterLoadedAndPurgePartial(JobId jobId, long jobDbId) {
      Set<BuildId> loadedBuilds = Sets.newHashSet();
      for (StoredBuild build : buildsDb.allOf(jobDbId)) {
        if (build.fullyStored()) {
          // The build is complete, we add it to the loaded builds so it isn't fetch again.
          loadedBuilds.add(jobId.build(build.buildNumber()));
        } else {
          // The build has been stored, but incompletely. We remove it so it can be fetched again.
          buildsDb.delete(build.id());
        }
      }
      return loadedBuilds;
    }

    private void loadBuild(long jobDbId, BuildId buildId) {
      try {
        // Grab a workload definition from the database
        var workflowDef = workflowsDb.getWorkflow(buildId.jobId().workflow());
        if (workflowDef.isEmpty()) {
          addMessage(
              "Not loading build %s as workflow %s cannot be found",
              buildId, buildId.jobId().workflow());
          logger.warn(
              "Not loading build {} as workflow {} cannot be found",
              buildId,
              buildId.jobId().workflow());
          return;
        }
        // JenkinsWorkflow creates a JenkinsClient which is used to fetch the build information
        var jenkinsWorkflow = JenkinsWorkflow.forWorkflow(workflowDef.get());

        // fetch the build from jenkins and then save it to the database
        jenkinsWorkflow
            .flatMap(x -> x.getBuild(buildId))
            .ifPresentOrElse(
                build -> {
                  if (shouldSaveBuild(build, workflowDef.get())) {
                    buildsService.saveNewBuild(jobDbId, build);
                  }
                },
                () -> logger.warn("Ignoring build {}: cannot find it", buildId));
      } finally {
        // Doing this here, because that's only use for progress reporting and even if we
        // failed, we still want to eventually get to 100% progress.
        loadedBuildsCount++;
      }
    }

    private boolean shouldSaveBuild(JenkinsBuild build, Workflow workflowDef) {
      if (!build.status().isCompleted()) {
        addMessage("Ignoring incomplete build %s: it is %s.", build.buildId(), build.status());
        return false;
      }
      if (workflowDef.shouldSkipBuild(build)) {
        addMessage("Skipping build %s by workflow %s skip checks.", build.buildId(), workflowDef);
        return false;
      }
      return true;
    }

    private Status currentStatus() {
      int progress = 0;
      if (isStarted()) {
        if (finished || totalBuildsCount == 0) {
          progress = 100;
        } else {
          progress = (int) Math.round((100.0 * loadedBuildsCount) / totalBuildsCount);
        }
      }
      return new Status(
          taskId,
          jobToLoad,
          isStarted(),
          finished,
          progress,
          Duration.ofNanos(System.nanoTime() - startTimeNanos),
          error,
          List.copyOf(messages),
          Instant.now());
    }
  }
}
