/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JenkinsClient;
import com.datastax.butler.commons.jenkins.TestReport;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.commons.web.WebClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class JenkinsTestHelper {

  /** Create test report summary with requested number of tests. */
  public static TestReport.Summary testReportSummary(long failed, long skipped, long total) {
    return TestReport.Summary.fromTotalCount(failed, skipped, total);
  }

  /** Create jenkins build with test report summary. */
  public static JenkinsBuild jenkinsBuild(TestReport.Summary testReport) {
    return new JenkinsBuild(null, null, null, null, null, null, null, testReport, null);
  }

  /** Get content of test resource. */
  public static String testResource(String path) throws IOException {
    var workingDir = Path.of("", "src/test/resources");
    Path file = workingDir.resolve(path);
    return Files.readString(file);
  }

  /**
   * Create test jenkins client SPY that uses provided webclient mock.
   *
   * @param webClient webClient to sue, potentially mock
   * @param multiBranchAnswer what to return for a "isMultiBranch" call
   * @return mockito.spy object on a test jenkins client
   */
  // Creates test jenkins client that uses provided webclient mock.
  public static JenkinsClient createTestJenkins(WebClient webClient, boolean multiBranchAnswer) {
    var host = "jenkins.example.com";
    String url = "http://" + host;
    var jenkinsClient = new JenkinsClient(url, webClient);
    var spy = Mockito.spy(jenkinsClient);
    Mockito.doReturn(multiBranchAnswer)
        .when(spy)
        .isMultiBranchPipeline(ArgumentMatchers.any(WorkflowId.class));
    return spy;
  }
}
