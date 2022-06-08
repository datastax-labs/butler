/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/** A branch name. */
public abstract class Branch implements Comparable<Branch> {

  /** The kind of branches we know about. */
  public enum Kind {
    UPSTREAM,
    OTHER
  }

  public abstract Kind kind();

  public abstract BranchVersion version();

  /**
   * Parse a string representing a branch name.
   *
   * <p>Note that this method never fails, but may return a {@link GenericBranch} (which will just
   * warp {@code str}) if the provided string does not match the convention of any other type of
   * branches.
   *
   * @param str the string to parse as a branch name.
   * @return the parsed branch name.
   */
  @JsonCreator
  public static Branch fromString(String str) {
    return new GenericBranch(str);
  }

  /**
   * Branches are sorted first by kind UPSTREAM >> TICKET >> OTHER and then string representation.
   */
  @Override
  public int compareTo(Branch that) {
    List<Kind> order = List.of(Kind.UPSTREAM, Kind.OTHER);
    var thisKindIndex = order.indexOf(this.kind());
    var thatKindIndex = order.indexOf(that.kind());
    if (thisKindIndex == thatKindIndex) return this.toString().compareTo(that.toString());
    else return Integer.compare(thisKindIndex, thatKindIndex);
  }

  @Override
  @JsonValue
  public abstract String toString();
}
