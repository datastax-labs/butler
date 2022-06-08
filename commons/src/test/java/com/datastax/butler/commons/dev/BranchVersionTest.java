/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BranchVersionTest {

  @Test
  void comparisonTest() {
    // given
    var v10 = BranchVersion.fromString("1.0");
    var v103 = BranchVersion.fromString("1.0.3");
    var v20 = BranchVersion.fromString("2.0");
    var vMain = BranchVersion.fromString("main");
    var v20Dup = BranchVersion.fromString("2.0");
    // when
    var toTest = List.of(v10, v20, vMain, v103, v20Dup);
    var unique = Sets.newHashSet(toTest);
    var sorted = unique.stream().sorted().collect(Collectors.toList());
    // then
    assertEquals(4, unique.size());
    assertEquals(List.of(v10, v103, v20, vMain), sorted);
  }

  @Test
  void fromStringTest() {
    assertEquals("6.8-dev", BranchVersion.fromString("6.8-dev").toString());
  }
}
