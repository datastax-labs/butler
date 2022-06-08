/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.jenkins.JenkinsException.error;
import static com.datastax.butler.commons.jenkins.JenkinsException.ioError;

import com.datastax.butler.commons.dev.Branch;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.function.Function;
import javax.annotation.Nullable;
import okio.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Converts Raw JSON strings into POJOs using moshi. */
public class JsonConverter<T> {

  private static final Logger logger = LogManager.getLogger();
  private final Moshi moshi;
  private final JsonAdapter<T> adapter;
  private final String converterName;

  /** Construct a JsonConverter which outputs objects of the given type. */
  public JsonConverter(Type type, String converterName) {
    this.moshi = new Moshi.Builder().add(Branch.class, adapter(Branch::fromString)).build();
    this.adapter = moshi.adapter(type);
    this.converterName = converterName;
  }

  /** Converts raw json into the desired object. */
  public T parse(String json) {
    try {
      if (logger.isDebugEnabled()) {
        debugJson(json, converterName);
      }
      T value = adapter.fromJson(json);
      if (value == null)
        throw error("Error parsing JSON for %s (got null): %s", converterName, json);
      return value;
    } catch (IOException e) {
      throw ioError(e, "Error parsing JSON for %s: %s", converterName, json);
    }
  }

  private void debugJson(String json, String what) {
    try (Buffer source = new Buffer().writeUtf8(json)) {
      JsonReader reader = JsonReader.of(source);
      Object value = reader.readJsonValue();
      String prettyPrinted = moshi.adapter(Object.class).indent("  ").toJson(value);
      logger.debug("Decoding JSON for {}:{}{}", what, System.lineSeparator(), prettyPrinted);
    } catch (IOException e) {
      throw ioError(e, "Error parsing JSON for %s: %s", what, json);
    }
  }

  private static <T> JsonAdapter<T> adapter(Function<String, T> parser) {
    return new JsonAdapter<>() {
      @Nullable
      @Override
      public T fromJson(JsonReader reader) throws IOException {
        return parser.apply(reader.nextString());
      }

      @Override
      public void toJson(JsonWriter writer, @Nullable T value) throws IOException {
        if (value == null) {
          writer.nullValue();
        } else {
          writer.value(value.toString());
        }
      }
    };
  }
}
