/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.commons;

import lombok.Value;

@Value
public class LoggedUser {
  String username;
  boolean isAdmin;
}
