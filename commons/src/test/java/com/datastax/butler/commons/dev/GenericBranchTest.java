/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GenericBranchTest {

  @Test
  void testEqualVsGeneric() {
    assertEquals(GenericBranch.fromString("5.1-dev"), GenericBranch.fromString("5.1-dev"));
    assertNotEquals(GenericBranch.fromString("6.0-dev"), GenericBranch.fromString("5.1-dev"));
  }

  @Test
  void testEqualVsUpstream() {
    assertNotEquals(GenericBranch.fromString("5.1-dev"), UpstreamBranch.fromString("5.1-dev"));
    assertNotEquals(UpstreamBranch.fromString("5.1-dev"), GenericBranch.fromString("5.1-dev"));
  }

  @Test
  void shouldReturnVersion() {
    assertEquals(
        BranchVersion.fromString("5.1-dev"), GenericBranch.fromString("5.1-dev").version());
  }

  @Test
  void shouldUseKindAndNameForHash() {
    assertNotEquals(
        GenericBranch.fromString("5.1-dev").hashCode(),
        UpstreamBranch.fromString("5.1-dev").hashCode());
  }
}
