/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

public enum TestResult {
  PASSED,
  FAILED,
  SKIPPED;

  static TestResult fromJenkinsStatus(String status) {
    switch (status) {
      case "FIXED":
        return PASSED;
      case "REGRESSION":
        return FAILED;
      default:
        return valueOf(status);
    }
  }
}
