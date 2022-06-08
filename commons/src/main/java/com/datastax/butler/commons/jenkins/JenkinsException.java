/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static java.lang.String.format;

import com.google.errorprone.annotations.FormatMethod;
import java.io.IOException;

/** Top-level exception thrown when encountering Jenkins related errors. */
public class JenkinsException extends RuntimeException {
  private JenkinsException(String message, Throwable cause) {
    super(message, cause);
  }

  @FormatMethod
  static JenkinsException error(String fmt, Object... args) {
    return new JenkinsException(format(fmt, args), null);
  }

  @FormatMethod
  static JenkinsException ioError(IOException cause, String fmt, Object... args) {
    return new JenkinsException(format(fmt, args), cause);
  }
}
