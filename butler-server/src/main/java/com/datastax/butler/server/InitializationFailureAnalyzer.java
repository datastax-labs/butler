/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server;

import com.datastax.butler.server.exceptions.InitializationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

public class InitializationFailureAnalyzer
    extends AbstractFailureAnalyzer<InitializationException> {

  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, InitializationException cause) {
    return new FailureAnalysis(cause.getMessage(), "Fix it!", cause);
  }
}
