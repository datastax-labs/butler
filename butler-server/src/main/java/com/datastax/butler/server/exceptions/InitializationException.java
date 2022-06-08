/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.exceptions;

import static java.lang.String.format;

import com.google.errorprone.annotations.FormatMethod;

public class InitializationException extends RuntimeException {
  private InitializationException(String msg, Throwable t) {
    super(msg, t);
  }

  @FormatMethod
  public static InitializationException error(String fmt, Object... args) {
    return new InitializationException(format(fmt, args), null);
  }

  @FormatMethod
  public static InitializationException error(Throwable cause, String fmt, Object... args) {
    return new InitializationException(format(fmt, args), cause);
  }
}
