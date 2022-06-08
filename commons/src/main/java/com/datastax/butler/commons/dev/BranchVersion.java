/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class BranchVersion implements Comparable<BranchVersion> {

  private final String branch;

  BranchVersion(String branch) {
    this.branch = branch;
  }

  @JsonCreator
  public static BranchVersion fromString(String versionString) {
    return new BranchVersion(versionString);
  }

  public Branch branch() {
    return Branch.fromString(this.branch);
  }

  @Override
  @JsonValue
  public String toString() {
    return this.branch;
  }

  @Override
  public int compareTo(@NotNull BranchVersion that) {
    return this.branch.compareTo(that.branch);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BranchVersion)) {
      return false;
    } else return this.toString().equals(o.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hash(branch);
  }
}
