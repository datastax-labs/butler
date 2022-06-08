/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import okhttp3.Request;
import okio.Okio;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fake web client used for testing code against servers that return JSON outputs.
 *
 * <p>The general principle is that a {@link TestJsonWebClient} provides locally pre-recorded
 * answers to specific requests, and will return a 404 otherwise.
 *
 * <p>For that, a {@link TestJsonWebClient} using a backing json file of the pre-recorded answers to
 * serve back. The
 */
public class TestJsonWebClient implements WebClient {

  private static final Logger logger = LogManager.getLogger();

  private final Moshi moshi;
  private final Map<String, Object> recordedResponses;

  /**
   * Creates a new test client whose pre-recorded answers' file is based on the provided class name.
   *
   * @param klass the name of the class to use to locate the file containing pre-recorded answers.
   *     The file must be in the "resources" folder under {@code web/<className>.json}. For
   *     instance, {@code new TestJsonWebClient(Foo.class)} will expect a json file at {@code
   *     src/test/resources/web/Foo.json}.
   * @throws AssertionError if the file defined by {@code klass} does not exists or cannot be read
   *     correctly.
   */
  public TestJsonWebClient(Class<?> klass) {
    this(resourcePath("web" + File.separator + klass.getSimpleName() + ".json"));
  }

  private static Path resourcePath(String file) {
    URL fileName = TestJsonWebClient.class.getClassLoader().getResource(file);
    assertNotNull(fileName, "Cannot find file " + file + " in test resources directory");
    return Path.of(fileName.getFile());
  }

  /**
   * Creates a new test client using the provided file for pre-recorded answers.
   *
   * @param backingFile the file containing the pre-recorded answers.
   * @throws AssertionError if {@code backingFile}does not exists or cannot be read correctly.
   */
  public TestJsonWebClient(Path backingFile) {
    if (!Files.exists(backingFile)) {
      throw new AssertionError("Cannot find file " + backingFile);
    }

    this.moshi = new Moshi.Builder().build();

    try {
      Type type = Types.newParameterizedType(Map.class, String.class, Object.class);
      JsonAdapter<Map<String, Object>> adapter = moshi.adapter(type);
      recordedResponses = adapter.fromJson(Okio.buffer(Okio.source(backingFile)));
      logger.info("Successfully loaded pre-recorded answers from {}", backingFile);
    } catch (IOException e) {
      throw new AssertionError("I/O error reading file " + backingFile, e);
    }
  }

  /**
   * Find the entry in the recorded responses that matches the provided url, or {@code null} if none
   * is found.
   */
  private Object findEntry(String url) {
    for (Map.Entry<String, Object> entry : recordedResponses.entrySet()) {
      if (matches(url, entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static boolean matches(String url, String entryToMatch) {
    if (entryToMatch.startsWith("*")) {
      return url.contains(entryToMatch.substring(1));
    } else {
      return url.startsWith(entryToMatch);
    }
  }

  @Override
  public String request(Request request) throws InvalidRequestException {
    Object entry = findEntry(request.url().toString());
    if (entry == null) {
      logger.info("For request {}, no entry found. Throwing NotFoundException", request.url());
      throw new NotFoundException();
    }

    if (entry instanceof Number) {
      int statusCode = ((Number) entry).intValue();
      logger.info(
          "For request {}, returning pre-recorded status code {}", request.url(), statusCode);
      throw InvalidRequestException.create(statusCode);
    }

    String body = moshi.adapter(Object.class).toJson(entry);
    logger.info("For request {}, returning pre-recorded answer '{}'", request.url(), body);
    return body;
  }
}
