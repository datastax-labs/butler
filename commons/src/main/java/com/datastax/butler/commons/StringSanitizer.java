/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons;

import java.util.Collection;
import java.util.stream.Collectors;

/** Helper class to perform string sanitization for logging etc. */
public class StringSanitizer {

  /** Sanitize string to not be vulnerable for logging. */
  public static String sanitize(String value) {
    if (value == null) return null;
    return value.replaceAll("[\n\r\t]", "_");
  }

  /** Sanitize collection of strings to not be vulnerable for logging. */
  public static Collection<String> sanitize(Collection<String> values) {
    return values.stream().map(StringSanitizer::sanitize).collect(Collectors.toList());
  }
}
