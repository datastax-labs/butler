/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import lombok.Value;

/**
 * Jenkins Test Run output.
 *
 * <p>Includes all the interesting test results details such as error details, stacktrace, stdin,
 * stdout etc.
 */
@Value
public class TestRunOutput {

  public static final TestRunOutput EMPTY_OUTPUT = new TestRunOutput(null, null, null, null);

  String errorDetails;
  String errorStackTrace;
  String stdout;
  String stderr;

  public boolean isEmpty() {
    return errorDetails == null && errorStackTrace == null && stdout == null && stderr == null;
  }
}
