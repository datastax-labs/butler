/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.api.ci.BuildImportRequest;
import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.IntegrationTest;
import com.datastax.butler.server.TestData;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BuildsDbIntegrationTest extends IntegrationTest {

  @Autowired private BuildsDb buildsRepository;
  @Autowired private JobsDb jobsRepository;

  private static final WorkflowId ciWorkflow = WorkflowId.of("ci");

  @Test
  void shouldSaveBuild() {
    // given
    var jobId = new JobId(ciWorkflow, Branch.fromString(randomBranch()));
    long jobDbId = jobsRepository.dbId(jobId);
    JenkinsBuild jenkinsBuild = jenkinsBuild(jobId, 7);
    buildsRepository.insert(jobDbId, jenkinsBuild);

    // when
    var result = buildsRepository.getByBuildNumber(jobDbId, 7);

    // then
    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals(7, result.get().buildNumber());
  }

  @Test
  void shouldReturnBuildsOfJob() {
    // given
    var jobId = new JobId(ciWorkflow, Branch.fromString(randomBranch()));
    long jobDbId = jobsRepository.dbId(jobId);
    buildsRepository.insert(jobDbId, jenkinsBuild(jobId, 1));
    buildsRepository.insert(jobDbId, jenkinsBuild(jobId, 2));
    buildsRepository.insert(jobDbId, jenkinsBuild(jobId, 3));
    buildsRepository.deleteByBuildNumberIfExists(jobDbId, 8);
    buildsRepository.deleteByBuildNumberIfExists(jobDbId, 2);
    buildsRepository.insert(jobDbId, jenkinsBuild(jobId, 4));

    // when
    var all = buildsRepository.allOf(jobDbId);
    var limited = buildsRepository.recentOf(jobDbId, 2);

    // then
    Assertions.assertEquals(3, all.size());
    Assertions.assertEquals(
        Set.of(1, 3, 4), all.stream().map(StoredBuild::buildNumber).collect(Collectors.toSet()));
    Assertions.assertEquals(2, limited.size());
    Assertions.assertEquals(
        Set.of(3, 4), limited.stream().map(StoredBuild::buildNumber).collect(Collectors.toSet()));
  }

  @Test
  void shouldStoreRawBuild() {
    // given
    var branch = Branch.fromString(randomBranch());
    var jobId = new JobId(ciWorkflow, branch);
    long jobDbId = jobsRepository.dbId(jobId);
    var testRuns =
        List.of(
            TestData.rawTestRun("case1", false, false),
            TestData.rawTestRun("case2", true, false),
            TestData.rawTestRun("case3", false, true),
            TestData.rawTestRun("case4", true, false));
    var build =
        new BuildImportRequest(
            "ci",
            "main",
            13,
            Instant.now().getEpochSecond(),
            10000,
            "http://ci.example.com/13",
            testRuns);
    buildsRepository.insert(jobDbId, build);
    // when
    var result = buildsRepository.getByBuildNumber(jobDbId, 13);
    // then
    Assertions.assertTrue(result.isPresent());
    Assertions.assertEquals(13, result.get().buildNumber());
    Assertions.assertEquals(10000, result.get().durationMs());
    Assertions.assertEquals(4, result.get().ranTests());
    Assertions.assertEquals(2, result.get().failedTests());
    Assertions.assertEquals(1, result.get().skippedTests());
  }

  private JenkinsBuild jenkinsBuild(JobId jobId, int buildNumber) {
    return new JenkinsBuild(
        null,
        new BuildId(jobId, buildNumber),
        HttpUrl.parse("http://jenkins.example.com/some/build"),
        JenkinsBuild.Status.SUCCESS,
        Instant.now().plusSeconds(buildNumber),
        null,
        null,
        null,
        null);
  }
}
