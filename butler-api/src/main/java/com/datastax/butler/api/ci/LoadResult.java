/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.ci;

import com.google.errorprone.annotations.FormatMethod;
import lombok.Value;

/** The result of the request to load a single CI build. */
@Value
public class LoadResult {
  boolean success;
  String message;

  @FormatMethod
  public LoadResult(boolean success, String fmt, Object... args) {
    this.success = success;
    this.message = String.format(fmt, args);
  }
}
