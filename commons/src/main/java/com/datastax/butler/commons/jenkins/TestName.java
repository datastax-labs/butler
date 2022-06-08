/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Value
public class TestName implements Comparable<TestName> {
  /** The category of the test. */
  TestCategory category;
  /**
   * The path of the test, that is anything leading up to the classname, if anything (meaning that
   * this can be an empty string technically).
   */
  String path;
  /** The name of the class for this test. */
  String className;
  /** The name of the test itself. */
  String testName;

  /** This is a constructor Mr. Checkstyle. */
  public TestName(TestCategory category, String path, String className, String testName) {
    this.category = category;
    this.path = (path != null) ? path : StringUtils.EMPTY;
    this.className = className;
    this.testName = testName;
  }

  /** We need this constructor for json bindings. */
  public TestName() {
    this(TestCategory.UNKNOWN, null, StringUtils.EMPTY, StringUtils.EMPTY);
  }

  /**
   * Returns test {@link TestId} correspond to the provided variant of this test name.
   *
   * @param variant the variant of the test.
   * @return the {@link TestId} whose name will be this test name and whose variant will be {@code
   *     variant}.
   */
  public TestId testId(TestVariant variant) {
    return new TestId(this, variant);
  }

  public static TestName ofSuiteAndTest(String suite, String testCase) {
    return ofSuiteAndTestWithCategory(suite, testCase, null);
  }

  public static TestName ofSuiteAndTestWithCategory(
      String suite, String testCase, String category) {
    var pathAndClass = TestName.splitSuite(suite);
    return new TestName(
        TestCategory.valueOf(category), pathAndClass.getLeft(), pathAndClass.getRight(), testCase);
  }

  /** Whether the test has a path, that is whether {@link #path} is non empty. */
  public boolean hasPath() {
    return !path.isEmpty();
  }

  /** The full name of the tests (its path, classname and name concatenated with '.'). */
  public String fullName() {
    if (hasPath()) {
      return String.format("%s.%s.%s", path, className, testName);
    }
    return toString();
  }

  public String testName() {
    return testName;
  }

  public boolean matchesClassAndTestName(String className, String testName) {
    return this.className.equals(className) && this.testName.equals(testName);
  }

  /** Split suite into path and class. */
  static Pair<String, String> splitSuite(String suite) {
    if (suite.isBlank()) {
      throw new IllegalArgumentException("Suite name cannot be blank");
    }
    if (suite.contains(".")) {
      return Pair.of(
          StringUtils.substringBeforeLast(suite, "."), StringUtils.substringAfterLast(suite, "."));
    } else {
      return Pair.of(StringUtils.EMPTY, suite);
    }
  }

  @Override
  public int compareTo(TestName that) {
    int cmpCategory = this.category().toString().compareTo(that.category().toString());
    if (cmpCategory != 0) return cmpCategory;

    int cmpPath = this.path.compareTo(that.path);
    if (cmpPath != 0) return cmpPath;

    int cmpClassName = this.className.compareTo(that.className);
    if (cmpClassName != 0) return cmpClassName;

    return this.testName.compareTo(that.testName);
  }

  @Override
  public String toString() {
    return String.format("%s.%s", className, testName);
  }
}
