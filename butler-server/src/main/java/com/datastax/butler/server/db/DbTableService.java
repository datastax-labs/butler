/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import java.util.Collection;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class DbTableService extends DbService {
  protected final String table;

  protected DbTableService(NamedParameterJdbcTemplate template, String table) {
    super(template);
    this.table = table;
  }

  protected <T, K> TableMapper<T, K> tableMapper(Class<T> valueClass, Class<K> keyClass) {
    return new TableMapper<>(template, table, valueClass, keyClass);
  }

  protected <T, K> TableMapper<T, K> tableMapper(Mapper<T> valueMapper, Class<K> keyClass) {
    return new TableMapper<>(template, table, valueMapper, keyClass);
  }

  protected static String idsToInClause(Collection<Long> ids) {
    return ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
  }
}
