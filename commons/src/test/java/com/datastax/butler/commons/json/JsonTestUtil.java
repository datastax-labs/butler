/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.json;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Provides helper methods to test JSON serialization/deserialization. */
public class JsonTestUtil {
  // A "raw" jackson Object mapper with no specific configuration (unlike the on in Json) use to
  // check json serialization. It's meant to be used only on raw structure like List, Map or basic
  // types (String, int, float, ...).
  private static final ObjectMapper rawMapper = new ObjectMapper();

  /**
   * Asserts the serialization and deserialization in JSON of a give object.
   *
   * <p>This method will bother assert that serializing {@code toTest} in JSON yields {@code
   * expected}, but also that it can the be deserialized back into the same object.
   *
   * @param expected the expect JSON output of {@code toTest}. This argument should be created
   *     exclusively through the {@link #jObj}, {@link #jArray} and {@link #jField} methods provided
   *     by this class.
   * @param toTest the object of which to test the serialization/deserialization.
   * @param type the class of {@code toTest}.
   * @param <T> the concrete type of {@code toTest}.
   */
  public static <T> void assertJson(Object expected, T toTest, Class<T> type) {
    assertJson(expected, toTest, json -> Json.fromJson(json, type));
  }

  /**
   * Asserts the serialization and deserialization in JSON of a give object.
   *
   * <p>This method will bother assert that serializing {@code toTest} in JSON yields {@code
   * expected}, but also that it can the be deserialized back into the same object.
   *
   * @param expected the expect JSON output of {@code toTest}. This argument should be created
   *     exclusively through the {@link #jObj}, {@link #jArray} and {@link #jField} methods provided
   *     by this class.
   * @param toTest the object of which to test the serialization/deserialization.
   * @param type a {@link TypeReference} for the class of {@code toTest} (used when generics are
   *     involved).
   * @param <T> the concrete type of {@code toTest}.
   */
  public static <T> void assertJson(Object expected, T toTest, TypeReference<T> type) {
    assertJson(expected, toTest, json -> Json.fromJson(json, type));
  }

  private static <T> void assertJson(Object expected, T toTest, Function<String, T> deserializer) {
    String expectedJson;
    try {
      expectedJson = rawMapper.writeValueAsString(expected);
    } catch (JsonProcessingException e) {
      throw new AssertionError(
          String.format(
              "Error converting expected object '%s' to json. Make sure that object is "
                  + "built only with the jObj, jArray and jField, of this class",
              expected));
    }
    String json = Json.toJson(toTest);
    assertEquals(expectedJson, json);
    assertEquals(toTest, deserializer.apply(json));
  }

  /**
   * Creates a JSON object with the provided fields, which should be provided using {@link #jField}.
   */
  @SafeVarargs
  public static Map<String, Object> jObj(Map.Entry<String, Object>... fields) {
    // We use a linkedHashMap so the order in the Json is predictable.
    Map<String, Object> m = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : fields) {
      m.put(entry.getKey(), entry.getValue());
    }
    return m;
  }

  /** Creates a JSON array with the provided values. */
  public static List<Object> jArray(Object... values) {
    return Arrays.asList(values);
  }

  /** Creates a JSON object field (name and value) to be passed to {@link #jObj}. */
  public static Map.Entry<String, Object> jField(String name, Object value) {
    return new AbstractMap.SimpleImmutableEntry<>(name, value);
  }
}
