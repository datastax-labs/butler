/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class TestIdTest {

  TestCategory unitTest = TestCategory.valueOf("UNIT");

  private TestId unitTest(String name, TestVariant variant) {
    return new TestId(
        new TestName(unitTest, "org.apache.cassandra.foo", "SomeTestSuite", name), variant);
  }

  @Test
  void testEquals() {
    assertEquals(
        unitTest("testCase", TestVariant.fromString("cdc")),
        unitTest("testCase", TestVariant.fromString("cdc")));
    assertEquals(
        unitTest("testCase", TestVariant.DEFAULT), unitTest("testCase", TestVariant.DEFAULT));
    assertNotEquals(
        unitTest("testCase1", TestVariant.DEFAULT), unitTest("testCase2", TestVariant.DEFAULT));
    assertNotEquals(
        unitTest("testCase", TestVariant.DEFAULT),
        unitTest("testCase", TestVariant.fromString("cdc")));
  }
}
