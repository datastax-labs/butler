/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UpstreamBranchTest {

  @Test
  void hasProperKind() {
    assertEquals(Branch.Kind.UPSTREAM, UpstreamBranch.fromString("trunk").kind());
    assertEquals(Branch.Kind.UPSTREAM, UpstreamBranch.fromString("main").kind());
    assertEquals(Branch.Kind.UPSTREAM, UpstreamBranch.fromString("6.8-dev").kind());
  }
}
