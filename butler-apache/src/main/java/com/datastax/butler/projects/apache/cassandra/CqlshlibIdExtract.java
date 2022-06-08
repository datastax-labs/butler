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
import java.util.regex.Pattern;

public class CqlshlibIdExtract implements TestNameScheme.IdExtract {

  private final TestCategory category;

  // cqlshlib.(variant).test.(path).(class)
  private static final Pattern PATTERN =
      Pattern.compile("^cqlshlib.([\\w-.]{1,128})[.]test[.]([\\w-.]{1,128})[.]([\\w-]{1,128})$");

  public CqlshlibIdExtract(TestCategory category) {
    this.category = category;
  }

  @Override
  public TestId create(String className, String name) {
    // cqlshlib.python2.7-no-cython.jdk8.test.test_unicode.TestCqlshUnicode
    var match = PATTERN.matcher(className);
    if (match.matches()) {
      var variant = TestVariant.fromString(match.group(1));
      var path = match.group(2);
      var clazz = match.group(3);
      return new TestId(new TestName(category, path, clazz, name), variant);
    } else {
      return null;
    }
  }
}
