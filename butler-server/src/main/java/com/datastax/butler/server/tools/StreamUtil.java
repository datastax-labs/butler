/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.tools;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class StreamUtil {

  /**
   * Allows to implement distinct() by property on the stream of objects.
   *
   * <p>from <a href="https://javadevcentral.com/java-stream-distinct-by-property"/>example:
   * people.stream().filter(distinctByKey(Person::getAge))
   */
  public static <T> Predicate<T> distinctByKey(Function<T, Object> function) {
    Set<Object> seen = new HashSet<>();
    return t -> seen.add(function.apply(t));
  }
}
