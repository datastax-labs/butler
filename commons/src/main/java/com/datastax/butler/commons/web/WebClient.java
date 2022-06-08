/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

import java.io.IOException;
import okhttp3.Request;

/**
 * Interface to abstract a basic web client.
 *
 * <p>This exists mostly so we can "mock" responses from JIRA or Jenkins for tests. In particular,
 * the interface depends on OkHttp {@link Request} class, which is not ideal in terms of
 * encapsulation, but is use as a shortcut for now.
 */
public interface WebClient {

  /**
   * Performs the provided request through the client.
   *
   * @param request the request to perform.
   * @return the body of the response to {@code request} if the request if successful (otherwise, an
   *     exception is thrown).
   * @throws InvalidRequestException if the server response indicates an error.
   * @throws IOException if an I/O error happens during the request.
   */
  String request(Request request) throws InvalidRequestException, IOException;

  /**
   * Exception thrown when a request is invalid.
   *
   * <p>Note that the constructor is private and implementations of {@link WebClient} should use
   * {@link #create(int)} to create an request exception. This ensures that the {@link
   * NotFoundException} specialization is used for 404 errors.
   */
  class InvalidRequestException extends Exception {
    private final int statusCode;

    private InvalidRequestException(int statusCode) {
      super("Response with code " + statusCode);
      this.statusCode = statusCode;
    }

    public static InvalidRequestException create(int statusCode) {
      return statusCode == 404 ? new NotFoundException() : new InvalidRequestException(statusCode);
    }

    /** The status code returned by the request. */
    public int statusCode() {
      return statusCode;
    }
  }

  /**
   * Specialization of {@link InvalidRequestException} thrown for 404 errors.
   *
   * <p>This specialization exists because 404 errors can often use a specific treatment compared to
   * more generic errors and this make it a bit easier to do.
   *
   * <p>Note that implementations of {@link WebClient} can use {@link
   * InvalidRequestException#create(int)} even for 404 errors as this will properly create a {@link
   * NotFoundException}.
   */
  class NotFoundException extends InvalidRequestException {
    public NotFoundException() {
      super(404);
    }
  }
}
