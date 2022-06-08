/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import com.datastax.butler.api.gate.JenkinsBuildApprovalRequest;
import com.datastax.butler.api.gate.JenkinsBuildApprovalResponse;
import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.dev.UpstreamFailures;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JenkinsClient;
import com.datastax.butler.commons.jenkins.JenkinsWorkflow;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.UpstreamFailuresDb;
import com.datastax.butler.server.service.BuildsService;
import com.datastax.butler.server.service.prgate.GateDecision;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Controller for REST API related to PR gating. */
@RestController
@RequestMapping("/api/gate")
public class GateController {
  private static final Logger logger = LogManager.getLogger();
  private static final int GATE_ANALYSIS_DEPTH = 16;

  private final JobsDb jobsDb;
  private final BuildsDb buildsDb;
  private final BuildsService buildsService;
  private final CiController ciController;
  private final UpstreamFailuresDb upstreamFailuresDb;

  @Value("${butlerAppUrl}")
  private String butlerAppUrl;

  /** Creates the controller (Autowired by Spring). */
  @Autowired
  public GateController(
      JobsDb jobsDb,
      BuildsDb buildsDb,
      BuildsService buildService,
      CiController ciController,
      UpstreamFailuresDb upstreamFailuresDb) {
    this.jobsDb = jobsDb;
    this.buildsDb = buildsDb;
    this.buildsService = buildService;
    this.ciController = ciController;
    this.upstreamFailuresDb = upstreamFailuresDb;
  }

  /** Approve or reject given build from the perspective of test failures. */
  @PostMapping("/builds/approve")
  public JenkinsBuildApprovalResponse approveBuild(
      @RequestBody JenkinsBuildApprovalRequest request) {
    logger.info("Calculating approval for build {}", request);
    // we need to know workflow and jenkins
    var workflowDef = ciController.getWorkflowDef(WorkflowId.of(request.pipeline()));
    var url = HttpUrl.parse(request.url());
    if (url == null) {
      logger.warn("Cannot parse url in {}", request);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid url: " + request.url());
    }
    if (request.buildNumber() <= 0) {
      logger.warn("Invalid request, build number should be > 0 in {}", request);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid request, build number should be > 0: " + request.url());
    }
    var jenkinsClient = JenkinsClient.createForUrl(url);
    // now we need to load build using provided URL
    var jobId = new JobId(WorkflowId.of(request.pipeline()), Branch.fromString(request.branch()));
    var buildId = new BuildId(jobId, request.buildNumber());
    var jenkinsWorkflow = new JenkinsWorkflow(jenkinsClient, workflowDef);
    var buildOpt = jenkinsWorkflow.getBuild(buildId, url);
    if (buildOpt.isEmpty()) {
      String msg =
          "Jenkins build with URL " + request.url() + "does not exist or butler cannot parse it.";
      logger.error(msg);
      throw new IllegalArgumentException(msg);
    }
    var build = buildOpt.get();
    // check if build should not be skipped => cannot be used for approval
    if (workflowDef.shouldSkipBuild(build)) {
      return rejectAsSkippedByWorkflow(build, request, workflowDef);
    }
    // store build in butler database
    var buildUsable = storeBuild(build, jobId, buildId);
    if (!buildUsable) {
      return rejectAsUnusable(build, request);
    }
    // compare build vs upstream
    // at this moment we assume that build is correctly stored
    var decision = makeDecision(request);
    // return approval
    if (decision.isApproved()) {
      return approval(build, request, decision.explanation());
    } else {
      return rejectWithFailures(build, request, decision.explanation(), decision.details());
    }
  }

  @VisibleForTesting
  JenkinsBuildApprovalResponse approval(
      JenkinsBuild build, JenkinsBuildApprovalRequest request, String explanation) {
    StringBuilder msg = new StringBuilder();
    msg.append("Approved by butler");
    msg.append("; ").append(build.infoString());
    msg.append("; ").append(explanation);
    logger.info(msg);
    return JenkinsBuildApprovalResponse.approved(msg.toString(), compareLink(request));
  }

  @VisibleForTesting
  JenkinsBuildApprovalResponse rejectAsUnusable(
      JenkinsBuild build, JenkinsBuildApprovalRequest request) {
    StringBuilder msg = new StringBuilder();
    msg.append(String.format("Build is not usable (status: %s).", build.status().toString()));
    msg.append("; ").append(build.infoString());
    var msgStr = msg.toString();
    logger.info(msgStr);
    return JenkinsBuildApprovalResponse.rejected(msgStr, compareLink(request));
  }

  @VisibleForTesting
  JenkinsBuildApprovalResponse rejectAsSkippedByWorkflow(
      JenkinsBuild build, JenkinsBuildApprovalRequest request, Workflow workflowDef) {
    StringBuilder msg = new StringBuilder();
    msg.append(
        String.format(
            "Build was skipped by workflow %s (status: %s).", workflowDef, build.status()));
    msg.append("; ").append(build.infoString());
    var msgStr = msg.toString();
    logger.info(msgStr);
    return JenkinsBuildApprovalResponse.rejected(msgStr, compareLink(request));
  }

  @VisibleForTesting
  JenkinsBuildApprovalResponse rejectWithFailures(
      JenkinsBuild build,
      JenkinsBuildApprovalRequest request,
      String failSummary,
      List<String> failDetails) {
    // lets build summary
    StringBuilder summaryMsg = new StringBuilder();
    summaryMsg
        .append("Build rejected: ")
        .append(failSummary)
        .append(", ")
        .append(build.infoString());
    // and details that will be put into separate field
    List<String> detailsMsg = new ArrayList<>();
    if (!failDetails.isEmpty()) {
      var analysisInfo =
          String.format(
              "Butler analysis done on %s/%s vs last %d runs of %s/%s.",
              request.pipeline(),
              request.branch(),
              GATE_ANALYSIS_DEPTH,
              request.upstreamWorkflow(),
              request.upstreamBranch());
      detailsMsg.add(analysisInfo);
      int testCountLimit = 13;
      if (failDetails.size() > testCountLimit) {
        detailsMsg.add(String.format("Showing only first %d NEW test failures", testCountLimit));
      }
      failDetails.stream().limit(testCountLimit).forEach(detailsMsg::add);
    }
    var msgStr = summaryMsg.toString();
    logger.info(msgStr);
    return JenkinsBuildApprovalResponse.rejected(msgStr, compareLink(request), detailsMsg);
  }

  /**
   * Store build in the database even if it is RUNNING. If it is running then we will store it but
   * also add it to the importer queue to update status later.
   *
   * @return true if build looks reasonable, false if not e.g. failed badly
   */
  @VisibleForTesting
  boolean storeBuild(JenkinsBuild build, JobId jobId, BuildId buildId) {
    long jobDbId = jobsDb.dbId(jobId);
    var ok = false;
    if (build.status().isRunning()) {
      logger.info("Build is RUNNING. Storing and adding to load queue for further status update.");
      ok = buildsService.saveNewBuild(jobDbId, build);
      ciController.queueJenkinsBuild(buildId, build, build.url());
    } else if (build.status().isCompleted()) {
      var storedBuild = buildsDb.getByBuildNumber(jobDbId, buildId.buildNumber());
      if (storedBuild.isPresent()) {
        logger.info(
            "Build is COMPLETED and already stored. Usable: {}", storedBuild.get().usable());
        return storedBuild.get().usable();
      } else {
        logger.info("Build is COMPLETED but not yet stored. Storing it.");
        buildsDb.deleteByBuildNumberIfExists(jobDbId, buildId.buildNumber());
        ok = buildsService.saveNewBuild(jobDbId, build);
      }
    } else {
      logger.info("Build is {}, unusable for butler", build.status());
    }
    return ok;
  }

  /** Build butler compare link using request branch and upstream info. */
  String compareLink(JenkinsBuildApprovalRequest request) {
    return String.format(
        "%s/#/ci/upstream/compare/%s/%s/to/%s/%s",
        butlerAppUrl,
        request.pipeline(),
        request.branch(),
        request.upstreamWorkflow(),
        request.upstreamBranch());
  }

  /**
   * Makes decision if approve or reject given build based on the history of upstream.
   *
   * @return decision with all the details
   */
  @VisibleForTesting
  GateDecision makeDecision(JenkinsBuildApprovalRequest request) {
    // Collect failures from both branch and upstream
    var failures =
        upstreamFailuresDb.compareJobs(
            request.pipeline(),
            request.branch(),
            request.upstreamWorkflow(),
            request.upstreamBranch(),
            Optional.of(GATE_ANALYSIS_DEPTH));
    // [0] includes any tests that were run on branch and failed on branch OR upstream
    var branchFailures = failures.get(0).failed();
    // [1] includes tests failing on upstream worflow+branch
    // if no upstream branch provided compare against all builds before the requested one
    var selfComparison = failures.size() == 1;
    var upstreamFailures =
        selfComparison ? failures.get(0).beforeBuild(request.buildNumber()) : failures.get(1);
    if (branchFailures.isEmpty()) {
      // if there are no failures we can approve
      return GateDecision.approved("All tests passed in all runs");
    } else {
      // keep only failures that were run in the recent build (not skipped or completely missing)
      // for example because of renaming the test
      var ranInThisBuild =
          branchFailures.stream()
              .filter(x -> x.failureDetails().last().id().buildNumber() == request.buildNumber())
              .filter(x -> !x.failureDetails().last().skipped())
              .collect(Collectors.toList());
      // in other case we check case by case if failure classifies as rejected
      var newFailures = findNewTestFailures(ranInThisBuild, upstreamFailures);
      if (newFailures.isEmpty()) {
        return GateDecision.approved("No NEW test failures");
      } else {
        var branchBuilds = failures.get(0).numBuilds();
        var summary =
            String.format("%d NEW test failure(s) in %d builds.", newFailures.size(), branchBuilds);
        return GateDecision.rejected(summary, newFailures);
      }
    }
  }

  private List<String> findNewTestFailures(
      List<TestFailure> failedTests, UpstreamFailures upstream) {
    List<String> result = new ArrayList<>();
    long startTime = System.nanoTime();

    for (TestFailure test : failedTests) {
      var decision = GateDecision.check(test, upstream);
      var testName = test.test().fullName();
      if (decision.isRejected()) {
        var msg = String.format("%s: %s [NEW]", testName, decision.explanation());
        result.add(msg);
        logger.debug(msg);
      } else {
        logger.debug("{} classified as acceptable with {}", testName, decision.explanation());
      }
    }

    var durMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
    logger.info("finding NEW test failures took {} ms", durMs);

    return result;
  }
}
