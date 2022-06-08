/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import java.util.Objects;

public class TestId implements Comparable<TestId> {
  private final TestName name;
  private final TestVariant variant;

  public TestId(TestName name, TestVariant variant) {
    this.name = name;
    this.variant = variant;
  }

  public TestName name() {
    return name;
  }

  public TestVariant variant() {
    return variant;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TestId)) {
      return false;
    }
    TestId that = (TestId) o;
    return this.name.equals(that.name) && this.variant.equals(that.variant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, variant);
  }

  @Override
  public int compareTo(TestId that) {
    int cmpName = this.name.compareTo(that.name);
    if (cmpName != 0) return cmpName;

    return this.variant.compareTo(that.variant);
  }

  @Override
  public String toString() {
    return String.format("%s%s", name.fullName(), variant.isDefault() ? "" : "[" + variant + "]");
  }
}
