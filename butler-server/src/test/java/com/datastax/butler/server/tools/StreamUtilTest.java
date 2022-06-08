/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;
import org.junit.jupiter.api.Test;

class StreamUtilTest {

  @Value
  static class Good {
    public String name;
    public int price;
  }

  @Test
  void distinctByKeyShouldRemoveDuplicatesAndPreserveOrder() {
    // given
    List<Good> goods =
        List.of(
            new Good("milk", 10),
            new Good("milk", 12),
            new Good("bread", 7),
            new Good("milk", 6),
            new Good("bread", 8));
    // when filtered without parallelism
    final List<Good> distinctGoods =
        goods.stream().filter(StreamUtil.distinctByKey(Good::name)).collect(Collectors.toList());
    // then only first prices for each good should remain
    assertEquals(2, distinctGoods.size());
    assertEquals("milk", distinctGoods.get(0).name);
    assertEquals(10, distinctGoods.get(0).price);
    assertEquals("bread", distinctGoods.get(1).name);
    assertEquals(7, distinctGoods.get(1).price);
  }
}
