/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

import java.io.IOException;
import java.time.Duration;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** An implementation of {@link WebClient} backed by {@link OkHttpClient}. */
public class OkHttpWebClient implements WebClient {
  private static final Logger logger = LogManager.getLogger();

  private final OkHttpClient httpClient;

  /** Creates a new {@link OkHttpWebClient}. */
  public OkHttpWebClient() {
    // Extending the default timeout (of 10 sec I believe) somewhat. I mean, we use this to connect
    // to JIRA and Jenkins and both can be slow at times. Besides, some of our queries are a bit
    // expensive (think getting a built report) so increasing the timeout for those feels right
    // (meaning, I've seen timeout with the default during testing). Of course, for the latter, we
    // could have a more granular approach and only bump timeouts for known heavy request, but doing
    // it globally is easier and probably good enough for now.
    // Obviously, if we have to toy with this more in the future, it will make sense to make it
    // configurable instead of hard-coded.
    this.httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMinutes(1))
            .readTimeout(Duration.ofMinutes(1))
            .writeTimeout(Duration.ofMinutes(1))
            .build();
  }

  @Override
  public String request(Request request) throws InvalidRequestException, IOException {
    logger.debug("Requesting {}", request);
    try (Response response = httpClient.newCall(request).execute()) {
      logger.debug("  >> {}", response);
      if (!response.isSuccessful()) throw InvalidRequestException.create(response.code());

      var body = response.body();
      // The javadoc ensures that this should be non-null as it comes from a call to Call#execute.
      assert body != null;
      return body.string();
    }
  }

  /**
   * Make a request and return the response headers.
   *
   * @param request the request to perform.
   * @return the body of the response to {@code request} if the request if successful (otherwise, an
   *     exception is thrown).
   * @throws InvalidRequestException if the server response indicates an error.
   * @throws IOException if an I/O error happens during the request.
   */
  public Headers requestHeaders(Request request) throws InvalidRequestException, IOException {
    logger.debug("Requesting {}", request);
    try (Response response = httpClient.newCall(request).execute()) {
      logger.debug("  >> {}", response);
      if (!response.isSuccessful()) throw InvalidRequestException.create(response.code());

      var headers = response.headers();
      // The javadoc ensures that this should be non-null as it comes from a call to Call#execute.
      assert headers != null;
      return headers;
    }
  }
}
