/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

/**
 * Test report for a Jenkins build.
 *
 * <p>This contains all ran tests and their result.
 */
public class TestReport {
  private final JenkinsWorkflow jenkinsWorkflow;
  private final BuildId buildId;
  private final Summary summary;
  private final HttpUrl testReportUrl;

  @SuppressWarnings("unused")
  private final Map<TestId, TestRun> allTests;

  private final SortedSet<TestId> failedTests;
  private final NavigableMap<TestName, SortedSet<TestId>> failedTestsByNames;

  // For each test, the "blocks" (separated by a '.') within which each test was run on Jenkins. We
  // need to preserve those so we can generate the URL to the test on Jenkins.
  private final Map<TestId, String> blockNames;

  TestReport(
      JenkinsWorkflow jenkinsWorkflow,
      BuildId buildId,
      Summary summary,
      HttpUrl fromUrl,
      Map<TestId, TestRun> allTests,
      SortedSet<TestId> failedTests,
      Map<TestId, String> blockNames) {
    this.jenkinsWorkflow = jenkinsWorkflow;
    this.buildId = buildId;
    this.summary = summary;
    this.testReportUrl = fromUrl;
    this.allTests = allTests;
    // Note: we know our argument will not be mutated behind our back; that why this ctor is package
    // private.
    this.failedTests = Collections.unmodifiableSortedSet(failedTests);
    this.failedTestsByNames = groupFailures(failedTests);
    this.blockNames = blockNames;
  }

  private static NavigableMap<TestName, SortedSet<TestId>> groupFailures(
      SortedSet<TestId> failedTestIds) {

    if (failedTestIds.isEmpty()) {
      return Collections.emptyNavigableMap();
    }

    NavigableMap<TestName, SortedSet<TestId>> byNames = new TreeMap<>();
    TestName currentTest = null;
    SortedSet<TestId> currentTestVariants = null;
    for (TestId testId : failedTestIds) {
      if (!testId.name().equals(currentTest)) {
        if (currentTest != null) {
          byNames.put(currentTest, Collections.unmodifiableSortedSet(currentTestVariants));
        }
        currentTest = testId.name();
        currentTestVariants = new TreeSet<>();
      }
      currentTestVariants.add(testId);
    }
    byNames.put(currentTest, Collections.unmodifiableSortedSet(currentTestVariants));

    return Collections.unmodifiableNavigableMap(byNames);
  }

  /** The identifier of the build this is a report of. */
  public BuildId buildId() {
    return buildId;
  }

  /** An unmodifiable (sorted) set of all the tests that are failed in this report. */
  public SortedSet<TestId> failedTestIds() {
    return failedTests;
  }

  /**
   * An unmodifiable (sorted) set of all the tests IDs that failed in this report for the provided
   * test name.
   *
   * @param testName the test name for which to return failed test IDs.
   * @return a set of all the test IDs that failed in this report and whose {@link TestId#name()} is
   *     {@code testName}. Note that if {@code testName} has been returned {@link
   *     #failedTestNames()}, then it is guaranteed to return a test with at least 1 element.
   */
  public SortedSet<TestId> failedTestIds(TestName testName) {
    return failedTestsByNames.get(testName);
  }

  /** An unmodifiable (sorted) set of all the tests that are failed in this report. */
  public SortedSet<TestName> failedTestNames() {
    return failedTestsByNames.navigableKeySet();
  }

  /**
   * An unmodifiable (sorted) set of all the test names that failed for the provided category.
   *
   * @param category the category of tests for which to return failures.
   * @return a (potentially empty) set of the names of all the test that failed in category {@code
   *     category}.
   */
  public SortedSet<TestName> failedTestNames(TestCategory category) {
    return failedTestNames().stream()
        .filter(t -> t.category().equals(category))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** Find all test runs matching given class name and test name. */
  public List<TestRun> findByTestCase(String className, String testName) {
    return allTests.values().stream()
        .filter(x -> x.id().name().matchesClassAndTestName(className, testName))
        .collect(Collectors.toList());
  }

  public Collection<TestRun> allTestRuns() {
    return allTests.values();
  }

  /** Returns full test run link. */
  public String testRunUrl(TestRun run) {
    var fullClass = run.jenkinsClassName();
    if (!fullClass.contains(".")) fullClass = "(root)." + fullClass;
    var clazz = StringUtils.substringAfterLast(fullClass, ".");
    var path = StringUtils.substringBeforeLast(fullClass, ".");
    return testReportUrl.newBuilder().addPathSegment(path).addPathSegment(clazz).build().toString();
  }

  /** A summary of the test results in this report. */
  public Summary summary() {
    return summary;
  }

  public @Nullable String blockNames(TestId testId) {
    return blockNames.get(testId);
  }

  public JenkinsWorkflow jenkinsWorkflow() {
    return jenkinsWorkflow;
  }

  /** Summary of test results (count of total tests run, and how many failed or were skipped). */
  public static class Summary {
    /**
     * Place-holder summary used when a build has not results whatsoever, typically because it
     * hasn't been run or hasn't completed.
     */
    public static final Summary EMPTY = new Summary(0, 0, 0);

    private final long passed;
    private final long failed;
    private final long skipped;

    Summary(long passed, long failed, long skipped) {
      this.passed = passed;
      this.failed = failed;
      this.skipped = skipped;
    }

    public static Summary fromTotalCount(long failed, long skipped, long total) {
      return new Summary(total - failed - skipped, failed, skipped);
    }

    public static Summary fromRanCount(long failed, long skipped, long ran) {
      return new Summary(ran - failed, failed, skipped);
    }

    /** How many tests were successful in this build. */
    public long successfulTests() {
      return passed;
    }

    /** How many tests failed in this build. */
    public long failedTests() {
      return failed;
    }

    /** How many tests were skipped in this build. */
    public long skippedTests() {
      return skipped;
    }

    /** How many tests where ran in this build (so total minus skipped). */
    public long ranTests() {
      return passed + failed;
    }

    /** How many total tests were "considered" in this build. */
    public long totalTests() {
      return ranTests() + skipped;
    }

    /** Build a comprehensive string with all important numbers. */
    String fullInfoString() {
      return String.format(
          "ran %d tests with %d failures and %d skipped.",
          ranTests(), failedTests(), skippedTests());
    }

    @Override
    public String toString() {
      return String.format("%d/%d failures", failed, ranTests());
    }
  }
}
