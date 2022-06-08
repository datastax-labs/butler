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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test Id Extractor for apache cassandra style dtest or upgrade tests.
 *
 * <p>dtest-[variant].path.class dtest-large-offheap-bti.replace_address_test.TestReplaceAddress
 */
public class DTestIdExtract implements TestNameScheme.IdExtract {

  private static final Logger logger = LogManager.getLogger();

  private final TestCategory category;
  private final String prefix;

  DTestIdExtract(TestCategory cat, String prefix) {
    this.category = cat;
    this.prefix = prefix;
  }

  @Override
  public TestId create(String className, String name) {
    // example: dtest-large-offheap-bti.replace_address_test.TestReplaceAddress
    var firstDotIdx = className.indexOf('.');
    var lastDotIdx = className.lastIndexOf('.');
    if (firstDotIdx <= 0 || lastDotIdx <= 0 || firstDotIdx >= lastDotIdx) {
      logger.warn("Cannot extract {} TestId from {}::{}", category, className, name);
      return null;
    }
    var path = className.substring(firstDotIdx + 1, lastDotIdx);
    var clName = className.substring(lastDotIdx + 1);
    var variant = TestVariant.DEFAULT;
    var variantAndGroupStr = className.substring(0, firstDotIdx);
    var dashIndex = variantAndGroupStr.indexOf('-');
    if (dashIndex > 0) {
      var variantStr = variantAndGroupStr.replaceFirst(prefix, "");
      if (!variantStr.isBlank()) {
        variant = TestVariant.fromString(variantStr);
      }
    }
    return new TestId(new TestName(category, path, clName, name), variant);
  }
}
