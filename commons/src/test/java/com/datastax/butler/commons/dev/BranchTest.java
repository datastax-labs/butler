/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchTest {

  @Test
  void comparisonTest() {

    // Defines a number of branch in their expected order.
    // Note that we need to explicitly use classes as
    // fromString does not know if the branch is upstream (it is per repository)
    List<Branch> toTest =
        Arrays.asList(
            UpstreamBranch.fromString("5.1-dev"),
            UpstreamBranch.fromString("5.1.0-rel"),
            UpstreamBranch.fromString("5.1.1-rel"),
            UpstreamBranch.fromString("6.0-dev"),
            UpstreamBranch.fromString("6.0.0-rel"),
            UpstreamBranch.fromString("6.1.0-rel"),
            UpstreamBranch.fromString("6.1.1-rel"),
            UpstreamBranch.fromString("6.7-dev"),
            UpstreamBranch.fromString("6.8-dev"),
            UpstreamBranch.fromString("master"),
            Branch.fromString("PRJ-42-6.0"),
            Branch.fromString("PRJ-42-6.7"),
            Branch.fromString("PRJ-42-dtests"),
            Branch.fromString("PRJ-42-master"),
            Branch.fromString("PRJ-64-5.1"),
            Branch.fromString("PRJ-64-6.0"),
            Branch.fromString("foobar"),
            Branch.fromString("random_name"));

    List<Branch> branches = new ArrayList<>(toTest);
    Collections.shuffle(branches);
    branches.sort(Comparator.naturalOrder());
    for (int i = 0; i < toTest.size(); i++) {
      assertEquals(toTest.get(i), branches.get(i));
    }
  }
}
