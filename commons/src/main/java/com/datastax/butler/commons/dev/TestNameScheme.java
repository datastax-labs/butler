/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestId;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.TestVariant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Test category scheme such as DTEST, UNIT etc.
 *
 * <p>Test category can be defined differently for every project, or every workflow. It knows how to
 * extract TestId (including variant) from the test report information.
 */
public class TestNameScheme {

  private final String name;
  private final List<Pattern> patterns;
  private final IdExtract idExtract;

  public interface IdExtract {
    TestId create(String fullClass, String testName);
  }

  /** A constructor. */
  public TestNameScheme(String name, List<String> patterns, IdExtract idExtract) {
    this.name = name;
    this.patterns = patterns.stream().map(Pattern::compile).collect(Collectors.toList());
    this.idExtract = idExtract;
  }

  public boolean matchesTestName(TestName testName) {
    return name.equals(testName.category().toString());
  }

  /**
   * Creates TestId from given className and testName.
   *
   * @param suite full class name, including package
   * @param testName test name
   * @return some if this category can match (suite,testName) or empty if not
   */
  public Optional<TestId> createTestId(String suite, String testName) {
    if (suiteMatchesPattern(suite)) {
      return Optional.ofNullable(idExtract.create(suite, testName));
    } else {
      return Optional.empty();
    }
  }

  /** Checks if provided suite name should be handled by this naming scheme. */
  public boolean suiteMatchesPattern(String suite) {
    for (Pattern p : patterns) {
      if (p.matcher(suite).find()) return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return name;
  }

  public static TestNameScheme acceptAllAsOtherScheme() {
    return new TestNameScheme("OTHER", List.of(".*"), new SimpleExtract());
  }

  private static class SimpleExtract implements IdExtract {
    @Override
    public TestId create(String fullClass, String testName) {
      return new TestId(
          new TestName(TestCategory.UNKNOWN, null, fullClass, testName), TestVariant.DEFAULT);
    }
  }
}
