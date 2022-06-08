/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.json.JsonTestUtil.assertJson;
import static com.datastax.butler.commons.json.JsonTestUtil.jField;
import static com.datastax.butler.commons.json.JsonTestUtil.jObj;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestNameTest {

  @Test
  void testJson() {
    TestName testName =
        new TestName(
            TestCategory.valueOf("UNIT"), "org.apache.cassandra.foo", "SomeTestSuite", "someTest");

    Object json =
        jObj(
            jField("category", "UNIT"),
            jField("path", "org.apache.cassandra.foo"),
            jField("class_name", "SomeTestSuite"),
            jField("test_name", "someTest"));

    assertJson(json, testName, TestName.class);
  }

  @Test
  void shouldSplitSuiteWithPath() {
    var pathAndClass = TestName.splitSuite("com.example.product.Clazz");
    assertEquals("com.example.product", pathAndClass.getLeft());
    assertEquals("Clazz", pathAndClass.getRight());
  }

  @Test
  void shouldSplitSuiteWithoutPath() {
    var pathAndClass = TestName.splitSuite("Clazz");
    assertEquals("", pathAndClass.getLeft());
    assertEquals("Clazz", pathAndClass.getRight());
  }
}
