/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import java.util.Objects;

/**
 * The name of a branch that has no particular meaning in the dev process (so is neither a ticket
 * branch, nor an upstream branch, nor a ticket dtest branch).
 *
 * <p>Please note that there is no way to create a {@link GenericBranch} instance "manually", you
 * have to use {@link Branch#fromString}
 */
public class GenericBranch extends Branch {

  private final String name;

  GenericBranch(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public BranchVersion version() {
    return BranchVersion.fromString(name);
  }

  @Override
  public Kind kind() {
    return Kind.OTHER;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof GenericBranch)) {
      return false;
    }
    GenericBranch that = (GenericBranch) obj;
    return this.kind().equals(that.kind()) && this.name().equals(that.name());
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind(), name);
  }
}
