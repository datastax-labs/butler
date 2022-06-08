/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import lombok.Value;

@Value
public class StoredAuthority {
  String username;
  String authority;
}
