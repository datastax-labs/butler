/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.json.JsonTestUtil.assertJson;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestVariantTest {
  @Test
  void testJson() {
    for (String str : Arrays.asList("compression", "nio", "vnodes")) {
      TestVariant variant = TestVariant.fromString(str);
      assertJson(str, variant, TestVariant.class);
    }
  }

  @Test
  void shouldReturnDefaultFromBlankString() {
    assertEquals(TestVariant.DEFAULT, TestVariant.fromString(null));
    assertEquals(TestVariant.DEFAULT, TestVariant.fromString(""));
    assertEquals(TestVariant.DEFAULT, TestVariant.fromString("   "));
  }

  @Test
  void compareToShouldHandleDefault() {
    var expected =
        List.of(
            TestVariant.fromString("aaa"),
            TestVariant.fromString("aaa"),
            TestVariant.fromString("bbb"),
            TestVariant.DEFAULT,
            TestVariant.DEFAULT);
    var data =
        new ArrayList<>(
            List.of(
                TestVariant.DEFAULT,
                TestVariant.fromString("bbb"),
                TestVariant.fromString("aaa"),
                TestVariant.fromString("<default>"),
                TestVariant.fromString("aaa")));
    Collections.sort(data);
    assertEquals(expected, data);
  }
}
