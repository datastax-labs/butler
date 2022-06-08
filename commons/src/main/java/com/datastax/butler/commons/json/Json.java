/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.InputStream;

/** Methods to deal with Json serialization/deserialization. */
public abstract class Json {

  private static final PropertyNamingStrategy NAMING_STRATEGY = PropertyNamingStrategy.SNAKE_CASE;
  private static final ImmutableMap<PropertyAccessor, Visibility> VISIBILITIES =
      ImmutableMap.<PropertyAccessor, Visibility>builder()
          .put(PropertyAccessor.FIELD, Visibility.ANY)
          .put(PropertyAccessor.CREATOR, Visibility.DEFAULT)
          .put(PropertyAccessor.GETTER, Visibility.NONE)
          .put(PropertyAccessor.IS_GETTER, Visibility.NONE)
          .put(PropertyAccessor.SETTER, Visibility.NONE)
          .build();

  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.setPropertyNamingStrategy(NAMING_STRATEGY);
    VISIBILITIES.forEach(mapper::setVisibility);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  private Json() {}

  public static ObjectMapper mapper() {
    return mapper;
  }

  /**
   * Converts the provided object to JSON.
   *
   * @param object the object to convert.
   * @return the JSON string corresponding to {@code object}.
   * @throws IllegalArgumentException if the provided object cannot be converted to JSON.
   */
  public static String toJson(Object object) {
    try {
      return mapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          String.format(
              "Error converting %s of class %s to JSon: %s",
              object, object.getClass(), e.getMessage()),
          e);
    }
  }

  /**
   * Maps the provided JSON value to the provided class.
   *
   * @param json a string containing the json representation of an object of type {@code valueType}.
   * @param valueType the class of the value to map {@code json} into.
   * @param <T> the concrete type of the value returned.
   * @return the mapped value.
   */
  public static <T> T fromJson(String json, Class<T> valueType) {
    try {
      return mapper.readValue(json, valueType);
    } catch (JsonMappingException e) {
      throw new IllegalArgumentException(
          String.format("Cannot map json value %s to %s", json, valueType), e);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(String.format("Invalid json value %s", json), e);
    }
  }

  /**
   * Maps the provided JSON value to the provided class.
   *
   * @param in input stream containing the json string (of type {@code valueType}) to decode.
   * @param valueType the class of the value to map the content of {@code in} into.
   * @param <T> the concrete type of the value returned.
   * @return the mapped value.
   * @throws IOException if an I/O error occurs while reading the input stream.
   */
  public static <T> T fromJson(InputStream in, Class<T> valueType) throws IOException {
    try {
      return mapper.readValue(in, valueType);
    } catch (JsonMappingException e) {
      throw new IllegalArgumentException(
          String.format("Cannot map json value from input stream to %s", valueType), e);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid json value in input stream", e);
    }
  }

  /**
   * Maps the provided JSON value to the provided class.
   *
   * @param json a string containing the json representation of an object of type {@code valueType}.
   * @param valueType the class of the value to map {@code json} into.
   * @param <T> the concrete type of the value returned.
   * @return the mapped value.
   */
  public static <T> T fromJson(String json, TypeReference<T> valueType) {
    try {
      return mapper.readValue(json, valueType);
    } catch (JsonMappingException e) {
      throw new IllegalArgumentException(
          String.format("Cannot map json value %s to %s", json, valueType), e);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(String.format("Invalid json value %s", json), e);
    }
  }

  /**
   * Abstract class aimed to facilitate slightly the write of custom deserialiers that expect what
   * they deserialize to be a JSON object.
   */
  public abstract static class ObjectDeserializer<T> extends StdDeserializer<T> {
    protected ObjectDeserializer(Class<T> type) {
      super(type);
    }

    protected abstract T readFields(JsonNode node, DeserializationContext ctx)
        throws JsonMappingException;

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      return readFields(p.getCodec().readTree(p), ctx);
    }

    protected String getField(JsonNode node, String field, DeserializationContext ctx)
        throws JsonMappingException {
      JsonNode textNode = node.get(field);
      if (textNode == null) {
        return ctx.reportInputMismatch(String.class, "Missing field %s", field);
      }
      if (!textNode.isValueNode() || !textNode.isTextual()) {
        return ctx.reportInputMismatch(String.class, "Expected %s to be a text field", field);
      }
      return textNode.asText();
    }

    protected int getIntField(JsonNode node, String field, DeserializationContext ctx)
        throws JsonMappingException {
      JsonNode intNode = node.get(field);
      if (intNode == null) {
        return ctx.reportInputMismatch(Integer.class, "Missing field %s", field);
      }
      if (!intNode.isValueNode() || !intNode.isIntegralNumber()) {
        return ctx.reportInputMismatch(String.class, "Expected %s to be an integer field", field);
      }
      return intNode.asInt();
    }
  }
}
