/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.errorprone.annotations.Immutable;
import java.io.Serializable;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A test variant, which correspond to a specific configuration onto which a test was run. */
@Immutable
public class TestVariant implements Comparable<TestVariant>, Serializable {

  private static final String DEFAULT_REPRESENTATION = "<default>";
  private final String name;

  private TestVariant(String name) {
    this.name = name;
  }

  /** Whether that is a test run in its default configuration. */
  public boolean isDefault() {
    return false;
  }

  public static final TestVariant DEFAULT =
      new TestVariant(DEFAULT_REPRESENTATION) {
        @Override
        public boolean isDefault() {
          return true;
        }
      };

  /**
   * Parses a variant from a string.
   *
   * @param str the string to parse as a test variant.
   * @return the parsed variant.
   * @throws IllegalArgumentException if {@code str} cannot be parsed as a test variant.
   */
  @JsonCreator
  public static TestVariant fromString(String str) {
    if (DEFAULT_REPRESENTATION.equalsIgnoreCase(str)) return DEFAULT;
    if (str == null) return DEFAULT;
    if (str.isBlank()) return DEFAULT;
    return new TestVariant(str);
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TestVariant)) {
      return false;
    }
    TestVariant that = (TestVariant) o;
    return this.name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public int compareTo(@NotNull TestVariant that) {
    if (this.isDefault() && that.isDefault()) return 0;
    if (this.isDefault()) return 1;
    if (that.isDefault()) return -1;
    return this.name.compareTo(that.name);
  }
}
