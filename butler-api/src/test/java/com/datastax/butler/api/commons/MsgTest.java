/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.commons;

import static com.datastax.butler.commons.json.JsonTestUtil.assertJson;
import static com.datastax.butler.commons.json.JsonTestUtil.jField;
import static com.datastax.butler.commons.json.JsonTestUtil.jObj;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Value;
import org.junit.jupiter.api.Test;

class MsgTest {

  @Test
  void testJson() {
    TestClass testValue = new TestClass("foo", 42);
    Msg<TestClass> withMsg = new Msg<>(testValue, "some message");

    Object json =
        jObj(
            jField("value", jObj(jField("prop1", "foo"), jField("prop2", 42))),
            jField("message", "some message"));

    assertJson(json, withMsg, new TypeReference<>() {});
  }

  @Value
  private static class TestClass {
    String prop1;
    int prop2;
  }
}
