/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * Test category (wrapper over string).
 *
 * <p>TestCategory scheme can vary between projects, and this is the reason we cannot have an enum
 * or any logic inside it.
 */
public class TestCategory {

  @JsonValue private final String name;

  public static final TestCategory UNKNOWN = new TestCategory("?");

  public TestCategory(String name) {
    this.name = name;
  }

  public static TestCategory valueOf(String category) {
    if (StringUtils.isBlank(category)) return UNKNOWN;
    return new TestCategory(category);
  }

  public boolean isUnknown() {
    return this.equals(UNKNOWN);
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TestCategory) {
      TestCategory other = (TestCategory) obj;
      return Objects.equal(this.name, other.name);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
