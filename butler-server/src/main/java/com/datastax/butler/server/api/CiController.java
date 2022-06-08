/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import static java.lang.String.format;

import com.datastax.butler.api.ci.BuildImportRequest;
import com.datastax.butler.api.ci.BulkLoadRequest;
import com.datastax.butler.api.ci.BulkLoadStatus;
import com.datastax.butler.api.ci.JenkinsBuildLoadRequest;
import com.datastax.butler.api.ci.LoadResult;
import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JenkinsClient;
import com.datastax.butler.commons.jenkins.JenkinsWorkflow;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.StoredBuild;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.BuildsService;
import com.datastax.butler.server.tools.BuildLoader;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Controller for Rest API related to CI/Jenkins. */
@RestController
@RequestMapping("/api/ci")
public class CiController {
  private final Logger logger = LogManager.getLogger();
  private final JobsDb jobsDb;
  private final BuildsDb buildsDb;
  private final UpstreamWorflowsDb workflowsDb;
  private final BuildsService buildsService;
  private final BuildLoader buildLoader;
  private final Queue<JenkinsLoadRequest> jenkinsLoadRequestQueue;

  /** Creates the controller (Autowired by Spring). */
  @Autowired
  public CiController(
      JobsDb jobsDb,
      BuildsDb buildsDb,
      UpstreamWorflowsDb workflowsDb,
      BuildsService buildsService,
      BuildLoader buildLoader) {
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.workflowsDb = workflowsDb;
    this.buildsService = buildsService;
    this.buildLoader = buildLoader;
    this.jenkinsLoadRequestQueue = new ConcurrentLinkedQueue<>();
  }

  /** Lists all the workflows on Jenkins. */
  @GetMapping("/workflow")
  public List<WorkflowId> listWorkflow() {
    return workflowsDb.allWorkflows().stream()
        .map(Workflow::workflowId)
        .sorted()
        .collect(Collectors.toList());
  }

  /** Lists all known jobs with workflows and categories. */
  @GetMapping("/known_jobs")
  public List<JobInfo> knownJobs() {
    return jobsDb.getAll().stream().sorted().map(this::jobInfo).collect(Collectors.toList());
  }

  /** List all upstream jobs with workflows and categories. */
  @GetMapping("/jobs/upstream")
  public List<JobInfo> upstreamJobs() {
    return jobsDb.getConfiguredUpstreamJobs().stream()
        .sorted()
        .map(this::jobInfo)
        .collect(Collectors.toList());
  }

  /** List all jobs for given branch. */
  @GetMapping("/jobs/branch/{branch}")
  public List<JobInfo> branchJobs(@PathVariable String branch) {
    return jobsDb.getByBranch(Branch.fromString(branch)).stream()
        .sorted()
        .map(this::jobInfo)
        .collect(Collectors.toList());
  }

  /** Return list of N recently run jobs in given workflow. */
  @GetMapping("/workflow/{workflowName}/jobs/recent/{limit}")
  public List<JobInfo> recentlyRunWorkflowJobs(
      @PathVariable String workflowName, @PathVariable int limit) {
    List<JobId> jobs = jobsDb.getByWorkflow(WorkflowId.of(workflowName));
    if (jobs.isEmpty()) return Collections.emptyList();
    // for each job take 1 recent usable build and sort descending(by id) limit 32
    // then get back jobId and map into JobInfo
    // Note: this is bit suboptimal, as we map JobId -> long (db) -> JobId
    Stream<StoredBuild> builds =
        jobs.stream()
            .map(jobsDb::dbId)
            .map(id -> buildsDb.recentUsableOf(id, 1))
            .filter(x -> !x.isEmpty())
            .map(x -> x.get(0))
            .sorted(Comparator.comparingLong(StoredBuild::id).reversed())
            .limit(limit);

    return builds
        .map(StoredBuild::jobId)
        .map(x -> jobsDb.getById(x))
        .flatMap(Optional::stream)
        .map(this::jobInfo)
        .collect(Collectors.toList());
  }

  /** Lists all the jobs for a given workflow in Jenkins. */
  @GetMapping("/workflow/{name}/job")
  public List<JobId> listAllJobs(@PathVariable String name, @RequestParam Optional<String> color) {
    var workflowDef = workflowsDb.getWorkflow(name).orElseThrow();
    var jenkins = JenkinsWorkflow.forWorkflow(workflowDef).orElseThrow().jenkins();
    List<JobId> fetched = jenkins.listJobs(WorkflowId.of(name), color);
    List<JobId> jobs = new ArrayList<>(fetched);
    jobs.sort(Comparator.naturalOrder());
    return jobs;
  }

  /** Lists all the builds for a given workflow/job in Jenkins. */
  @GetMapping("/workflow/{workflowName}/job/{jobName}")
  public List<BuildId> listJobs(@PathVariable String workflowName, @PathVariable String jobName) {
    JobId jobId = WorkflowId.of(workflowName).job(Branch.fromString(jobName));
    var workflowDef = getWorkflowDef(jobId.workflow());
    return JenkinsWorkflow.forWorkflow(workflowDef)
        .orElseThrow()
        .getJob(jobId)
        .orElseThrow(
            () -> {
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, format("Job %s does not exists", jobId));
            })
        .allBuildIds();
  }

  /**
   * Get the last known (by us) build for the specific job.
   *
   * @param workflowName the workflow
   * @param jobName the job name
   * @return recent database StoredBuild for given workflow and job or empty
   */
  @GetMapping("/workflow/{workflowName}/job/{jobName}/last")
  public Optional<StoredBuild> listKnownBuilds(
      @PathVariable String workflowName, @PathVariable String jobName) {
    OptionalLong jobDbId =
        jobsDb.dbIdIfExists(WorkflowId.of(workflowName).job(Branch.fromString(jobName)));
    if (jobDbId.isEmpty()) {
      return Optional.empty();
    }

    return buildsDb.recentOf(jobDbId.getAsLong(), 1).stream().findFirst();
  }

  /**
   * Loads a single build into the butler database.
   *
   * <p>If the build already exists, it is deleted before being re-inserted.
   *
   * @param buildId the build to load.
   * @return the result of the loading, indicating whether the build was inserted (it won't be if
   *     the build doesn't exists, or if the build hasn't completed).
   */
  @PostMapping("/builds/load")
  public LoadResult load(@RequestBody BuildId buildId) {
    // Note: we fetch the build from jenkins first as we'll need it anyway, and this ensure both
    // the build and job it is part of actually exists.
    var workflowDef = getWorkflowDef(buildId.jobId().workflow());
    Optional<JenkinsBuild> buildOpt =
        JenkinsWorkflow.forWorkflow(workflowDef).orElseThrow().getBuild(buildId);

    if (buildOpt.isEmpty()) {
      return new LoadResult(false, "Build %s does not exists", buildId);
    }
    if (workflowDef.shouldSkipBuild(buildOpt.get())) {
      return new LoadResult(false, "Build %s is skipped by workflow %s", buildId, workflowDef);
    }
    return storeJenkinsBuild(buildId, buildOpt.get());
  }

  /** if build is completed (not RUNNING) store it, else put it in the queue. */
  private LoadResult requestStoreJenkinsBuild(
      BuildId buildId, JenkinsBuild build, HttpUrl buildUrl) {
    if (build.status().isCompleted()) {
      return storeJenkinsBuild(buildId, build);
    } else if (build.status().isAborted()) {
      return new LoadResult(false, "Build %s will not be imported: %s", buildId, build.status());
    } else {
      return queueJenkinsBuild(buildId, build, buildUrl);
    }
  }

  LoadResult queueJenkinsBuild(BuildId buildId, JenkinsBuild build, HttpUrl buildUrl) {
    var request = new JenkinsLoadRequest(buildId, buildUrl, build.jenkinsWorkflow());
    jenkinsLoadRequestQueue.add(request);
    logger.info("Queued build {} because it is {}", buildId, build.status());
    return new LoadResult(false, "Queued build %s because it is %s", buildId, build.status());
  }

  private LoadResult storeJenkinsBuild(BuildId buildId, JenkinsBuild build) {
    if (!build.status().isCompleted()) {
      return new LoadResult(false, "Ignored build %s because it is %s", buildId, build.status());
    }
    // Check if the job is already stored, and if not, creates it.
    long jobDbId = jobsDb.dbId(buildId.jobId());

    // Do we already have the build we're being asked to create? If we do, delete it before we add
    // it back.
    buildsDb.deleteByBuildNumberIfExists(jobDbId, buildId.buildNumber());

    return buildsService.saveNewBuild(jobDbId, build)
        ? new LoadResult(
            true, "Loaded build %s[%s] (%s)", buildId, build.status(), build.testSummary())
        : new LoadResult(true, "Loaded build %s[%s] with no test report", buildId, build.status());
  }

  /** Return workflow definition or throw bad request. */
  public Workflow getWorkflowDef(WorkflowId id) {
    var workflowDef = workflowsDb.getWorkflow(id);
    if (workflowDef.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, format("Workflow %s is not configured", id));
    }
    return workflowDef.get();
  }

  /**
   * Load jenkins build with given link. Uses provided metadata to store in butler db.
   *
   * @return the result of the loading, indicating whether the build was inserted (it won't be if
   *     the build doesn't exists, or if the build hasn't completed).
   */
  @PostMapping("/builds/loadjenkins")
  public LoadResult loadJenkinsBuild(@RequestBody JenkinsBuildLoadRequest request) {
    // we need to know workflow and jenkins
    var workflowDef = getWorkflowDef(WorkflowId.of(request.workflow()));
    var url = HttpUrl.parse(request.url());
    if (url == null) {
      logger.warn("Cannot parse url: {} requested in {}", request.url(), request);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid url: " + request.url());
    }
    var jenkinsClient = JenkinsClient.createForUrl(url);
    // now we need to load build using provided URL
    var jobId = new JobId(WorkflowId.of(request.workflow()), Branch.fromString(request.branch()));
    var buildId = new BuildId(jobId, request.buildNumber());
    var jenkinsWorkflow = new JenkinsWorkflow(jenkinsClient, workflowDef);
    var buildOpt = jenkinsWorkflow.getBuild(buildId, url);
    // and store it in the database (using provided link as base for urls)
    return buildOpt
        .map(b -> requestStoreJenkinsBuild(buildId, b, url))
        .orElse(new LoadResult(false, "Build %s does not exists", buildId));
  }

  /**
   * Bulk load all the not-yet-stored builds of the provided jobs in the butler database.
   *
   * <p>Note that unlike single-build loading, this does not re-load builds that are already (fully)
   * stored.
   *
   * <p>This API is asynchronous: it returns as soon as the loading has been successfully submitted.
   * Once should then use the {@link #bulkLoadStatusTask(UUID)} API to watch the progress of the
   * loading (and be informed of a possible error).
   *
   * <p>Only one bulk-loading operation can be performed at any given time, so this method will
   * return and error if it is submitted while a previous bulk-loading is still in progress.
   *
   * @param request the bulk-load request, that mainly lists the jobs to be loaded (it also provide
   *     the option of limiting how many builds-per-job to load, though this is optional and all
   *     builds are loaded by default).
   */
  @PostMapping("/builds/bulkload")
  public List<UUID> bulkLoad(@RequestBody BulkLoadRequest request) {
    if (request.jobs().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "You should specify at least one job; none provided");
    }
    int maxPerJob =
        request.maxBuildsPerJob() <= 0
            ? buildLoader.defaultMaxBuildsPerJob()
            : request.maxBuildsPerJob();

    List<UUID> submittedTasks = Lists.newArrayList();
    for (JobId job : request.jobs()) {
      try {
        submittedTasks.add(buildLoader.submitLoad(job, maxPerJob));
      } catch (Exception e) {
        String msg = String.format("Problem when submitting loading for job %s: %s", job, e);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
      }
    }
    return submittedTasks;
  }

  /** Import raw data build, without translating names - import them as provided. */
  @PostMapping("/builds/import/raw")
  public BuildId importRawBuild(@RequestBody BuildImportRequest buildData) {
    var workflowDef = getWorkflowDef(WorkflowId.of(buildData.workflow()));
    try {
      var jobId = JobId.forWorkflowAndBranch(buildData.workflow(), buildData.branch());
      long jobDbId = jobsDb.dbId(jobId);
      long buildDbId = buildsService.importRawBuildForJob(jobDbId, buildData);
      logger.info("Imported new data for build with id {} in workflow {}", buildDbId, workflowDef);
      return jobId.build(buildData.buildNumber());
    } catch (Exception e) {
      String msg = String.format("Error when importing build: %s: %s", buildData, e);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
  }

  /** Retrieves the status of a loading task (through {@link #bulkLoad}). */
  @GetMapping("/builds/bulkload/status/task/{taskId}")
  public BulkLoadStatus bulkLoadStatusTask(@PathVariable UUID taskId) {
    Optional<BuildLoader.Status> status = buildLoader.taskStatus(taskId);
    return status.map(this::createBulkLoadStatus).orElse(BulkLoadStatus.NONE);
  }

  /** Return information about all statuses of loading tasks. */
  @GetMapping("/builds/bulkload/status/all")
  public List<BulkLoadStatus> bulkLoadStatusAll() {
    List<BuildLoader.Status> all = buildLoader.allStatus();
    return all.stream().map(this::createBulkLoadStatus).collect(Collectors.toList());
  }

  /** Return information about statuses of currently in progress loading tasks. */
  @GetMapping("/builds/bulkload/status/current")
  public List<BulkLoadStatus> bulkLoadStatusCurrent() {
    List<BuildLoader.Status> all = buildLoader.allStatus();
    return all.stream()
        .filter(s -> !s.finished())
        .map(this::createBulkLoadStatus)
        .collect(Collectors.toList());
  }

  /**
   * Create API BulkLoadStatus from internal BuildLoader status.
   *
   * @param status build loader task status
   * @return api status object
   */
  private BulkLoadStatus createBulkLoadStatus(BuildLoader.Status status) {
    return new BulkLoadStatus(
        status.taskId(),
        status.jobId(),
        status.finished(),
        status.progressPercentage(),
        status.duration().toSeconds(),
        status.error(),
        status.messages(),
        status.updateTimestamp().toString());
  }

  private JobInfo jobInfo(JobId job) {
    var jobCategory =
        workflowsDb.getWorkflow(job.workflow()).map(w -> w.jobCategory(job.jobName())).orElse(null);
    return new JobInfo(job.workflow().name(), job.jobName(), jobCategory);
  }

  @Value
  static class JenkinsLoadRequest {
    BuildId buildId;
    HttpUrl buildUrl;
    JenkinsWorkflow jenkinsWorkflow;
  }

  @SuppressWarnings("unused")
  @Scheduled(fixedDelay = 1000 * 60) // every minute
  private void processJenkinsLoadRequestsQueue() {
    logger.debug("checking jenkins load requests queue");
    try {
      var task = jenkinsLoadRequestQueue.remove();
      logger.debug("processing {} popped from the queue", task.buildId);
      Optional<JenkinsBuild> build = task.jenkinsWorkflow.getBuild(task.buildId, task.buildUrl);
      if (build.isPresent()) {
        requestStoreJenkinsBuild(task.buildId, build.get(), task.buildUrl);
      } else {
        logger.warn(
            "build {} with url {} was not found in jenkins and will not be imported",
            build,
            task.buildUrl);
      }
    } catch (NoSuchElementException ex) {
      logger.debug("queue is empty, noting to do here");
    }
  }
}
