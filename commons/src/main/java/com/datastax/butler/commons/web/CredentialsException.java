/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

public class CredentialsException extends RuntimeException {

  public CredentialsException(String message) {
    super(message);
  }
}
