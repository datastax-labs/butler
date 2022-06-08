/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import lombok.Value;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@Value
public class QueryParameters {
  String query;
  SqlParameterSource source;
}
