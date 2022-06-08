/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.google.errorprone.annotations.FormatMethod;
import lombok.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

abstract class DbService {
  protected static final Logger logger = LogManager.getLogger();

  protected final @NonNull NamedParameterJdbcTemplate template;

  protected DbService(NamedParameterJdbcTemplate template) {
    this.template = template;
  }

  @FormatMethod
  protected String q(String query, Object... args) {
    return String.format(query, args);
  }
}
