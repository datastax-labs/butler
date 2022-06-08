/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static java.lang.String.format;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RawTestReport {
  // We have to limit because of
  // (a) database size and
  // (b) jira reporting where description size is limited
  private static final int MAX_TEXT_LENGTH = 10000;
  private static final Logger logger = LogManager.getLogger();
  private static final JsonConverter<RawTestReport> parser =
      new JsonConverter<>(RawTestReport.class, "Jenkins test report");

  private final long passCount;
  private final long failCount;
  private final long skipCount;
  private final List<RawTestSuite> suites;

  private RawTestReport(long failCount, long passCount, long skipCount, List<RawTestSuite> suites) {
    this.passCount = passCount;
    this.failCount = failCount;
    this.skipCount = skipCount;
    this.suites = suites;
  }

  static RawTestReport parse(String json) {
    return parser.parse(json);
  }

  static HttpUrl.Builder getUrl(HttpUrl.Builder testReportUrl) {
    String suitesFields = "enclosingBlockNames";
    String casesFields = "className,name,duration,status";
    String outputFields = "errorDetails,errorStackTrace,stdout,stderr";
    String summaryFields = "passCount,failCount,skipCount";
    String tree =
        format(
            "%s,suites[%s,cases[%s,%s]]", summaryFields, suitesFields, casesFields, outputFields);
    return testReportUrl.addPathSegments("api/json").addEncodedQueryParameter("tree", tree);
  }

  TestReport toReport(JenkinsWorkflow jenkinsWorkflow, BuildId buildId, HttpUrl testReportUrl) {
    TestReport.Summary summary = new TestReport.Summary(passCount, failCount, skipCount);

    Map<TestId, TestRun> allTests = new HashMap<>();
    SortedSet<TestId> failedTests = new TreeSet<>();
    Map<TestId, String> blockNames = new HashMap<>();
    var workflow = jenkinsWorkflow.workflow();

    for (RawTestSuite suite : suites) {
      List<String> rawBlocks = suite.enclosingBlockNames;
      // We reverse to have the upper-level block first, as that's the order we'll use when
      // building urls, which is the main reason to keep the blocks around.
      Collections.reverse(rawBlocks);
      String blocks = String.join(".", rawBlocks);

      for (RawTestCase testCase : suite.cases) {
        var testNameScheme = workflow.matchTestNameScheme(testCase.className);
        var testId = testNameScheme.createTestId(testCase.className, testCase.name);
        if (testId.isEmpty()) {
          logger.error(
              "Cannot create TestID for {}::{}. Test run will be ignored.",
              testCase.className,
              testCase.name);
          continue;
        }
        TestId id = testId.get();
        blockNames.put(id, blocks);
        TestResult result = TestResult.fromJenkinsStatus(testCase.status);
        Duration duration = Duration.ofMillis(Math.round(testCase.duration * 1000));
        TestRunOutput output = TestRunOutput.EMPTY_OUTPUT;
        if (result == TestResult.FAILED) {
          output =
              new TestRunOutput(
                  StringUtils.left(testCase.errorDetails, MAX_TEXT_LENGTH),
                  StringUtils.left(testCase.errorStackTrace, MAX_TEXT_LENGTH),
                  StringUtils.left(testCase.stdout, MAX_TEXT_LENGTH),
                  StringUtils.left(testCase.stderr, MAX_TEXT_LENGTH));
        }
        TestRun run = new TestRun(id, result, duration, output, testCase.className, testCase.name);
        allTests.put(id, run);
        if (result == TestResult.FAILED) {
          failedTests.add(id);
        }
      }
    }
    return new TestReport(
        jenkinsWorkflow, buildId, summary, testReportUrl, allTests, failedTests, blockNames);
  }

  private static class RawTestSuite {
    private final List<RawTestCase> cases;
    private final List<String> enclosingBlockNames;

    private RawTestSuite(List<RawTestCase> cases, List<String> enclosingBlockNames) {
      this.cases = cases;
      this.enclosingBlockNames = enclosingBlockNames;
    }
  }

  private static class RawTestCase {
    private final String className;
    private final String name;
    private final double duration;
    private final String status;
    private final String errorDetails;
    private final String errorStackTrace;
    private final String stdout;
    private final String stderr;

    private RawTestCase(
        String className,
        String name,
        double duration,
        String status,
        String errorDetails,
        String errorStackTrace,
        String stdout,
        String stderr) {
      this.className = className;
      this.name = name;
      this.duration = duration;
      this.status = status;
      this.errorDetails = errorDetails;
      this.errorStackTrace = errorStackTrace;
      this.stdout = stdout;
      this.stderr = stderr;
    }

    @Override
    public String toString() {
      return "RawTestCase{"
          + "className='"
          + className
          + '\''
          + ", name='"
          + name
          + '\''
          + ", duration="
          + duration
          + ", status='"
          + status
          + '\''
          + '}';
    }
  }
}
