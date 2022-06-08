/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import java.util.Objects;

/** The name of an upstream branch (release branch or trunk, main etc.) */
public final class UpstreamBranch extends GenericBranch {

  UpstreamBranch(String name) {
    super(name);
  }

  public static UpstreamBranch fromString(String branchName) {
    return new UpstreamBranch(branchName);
  }

  @Override
  public Kind kind() {
    return Kind.UPSTREAM;
  }

  @Override
  public int compareTo(Branch that) {
    if (that.kind() == Kind.UPSTREAM) {
      return this.compareToUpstream((UpstreamBranch) that);
    }
    return -1;
  }

  private int compareToUpstream(UpstreamBranch that) {
    return this.name().compareTo(that.name());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof UpstreamBranch)) {
      return false;
    }
    UpstreamBranch that = (UpstreamBranch) obj;
    return this.name().equals(that.name());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name());
  }
}
