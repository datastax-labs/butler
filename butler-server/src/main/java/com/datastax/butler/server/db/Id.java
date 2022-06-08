/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import lombok.Data;

@Data
public class Id<T> {
  private final long id;
  private final T value;
}
