/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import static java.lang.String.format;

import com.google.errorprone.annotations.FormatMethod;
import java.io.IOException;

/** Top-level exception thrown when encountering JIRA related errors. */
public class JiraException extends RuntimeException {
  public JiraException(String message, Throwable cause) {
    super(message, cause);
  }

  @FormatMethod
  static JiraException error(String fmt, Object... args) {
    return new JiraException(format(fmt, args), null);
  }

  @FormatMethod
  static JiraException ioError(IOException cause, String fmt, Object... args) {
    return new JiraException(format(fmt, args), cause);
  }
}
