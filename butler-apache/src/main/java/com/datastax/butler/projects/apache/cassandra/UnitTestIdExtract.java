/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.projects.apache.cassandra;

import com.datastax.butler.commons.dev.TestNameScheme;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestId;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.TestVariant;

/** Extractor for the cassandra-style unit test names. */
public class UnitTestIdExtract implements TestNameScheme.IdExtract {

  private final TestCategory category;

  public UnitTestIdExtract(TestCategory category) {
    this.category = category;
  }

  @Override
  public TestId create(String className, String name) {
    var variant = TestVariant.DEFAULT;
    var testName = name;
    if (name.contains("-")) {
      var idx = name.indexOf('-');
      var afterDash = name.substring(idx + 1);
      if (afterDash.contains(" ")) {
        // this is not a variant, rather some Scala spec fancy name
        testName = name;
        variant = TestVariant.DEFAULT;
      } else {
        testName = name.substring(0, idx);
        variant = TestVariant.fromString(name.substring(idx + 1));
      }
    }
    // extract path and class name
    var idx = className.lastIndexOf('.');
    assert idx > 0;
    var path = className.substring(0, idx);
    var clName = className.substring(idx + 1);
    // return value
    return new TestId(new TestName(category, path, clName, testName), variant);
  }
}
