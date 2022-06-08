/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import static java.lang.String.format;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.simpleflatmapper.jdbc.SqlTypeColumnProperty;
import org.simpleflatmapper.jdbc.spring.JdbcTemplateMapperFactory;
import org.simpleflatmapper.jdbc.spring.SqlParameterSourceFactory;
import org.simpleflatmapper.map.property.ConverterProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class Mapper<T> {
  private final Class<T> type;
  private final JdbcTemplateMapperFactory factory;
  private final RowMapper<T> rowMapper;
  private final SqlParameterSourceFactory<T> sourceFactory;

  private final String whereClause;

  private Mapper(
      Class<T> type,
      JdbcTemplateMapperFactory factory,
      RowMapper<T> rowMapper,
      SqlParameterSourceFactory<T> sourceFactory,
      String whereClause) {
    this.type = type;
    this.factory = factory;
    this.rowMapper = rowMapper;
    this.sourceFactory = sourceFactory;
    this.whereClause = whereClause;
  }

  Class<T> type() {
    return type;
  }

  JdbcTemplateMapperFactory factory() {
    return factory;
  }

  static <T> Builder<T> builder(Class<T> type) {
    return new Builder<>(type);
  }

  static <T> Mapper<T> create(Class<T> type, String... columnNames) {
    return builder(type).columns(columnNames).build();
  }

  RowMapper<T> rowMapper() {
    return rowMapper;
  }

  SqlParameterSource source(T value) {
    return sourceFactory.newSqlParameterSource(value);
  }

  SqlParameterSource source(SqlParameterSource other, T value) {
    SqlParameterSource entitySource = source(value);
    return new SqlParameterSource() {
      @Override
      public boolean hasValue(String paramName) {
        return other.hasValue(paramName) || entitySource.hasValue(paramName);
      }

      @Override
      public Object getValue(String paramName) throws IllegalArgumentException {
        return other.hasValue(paramName)
            ? other.getValue(paramName)
            : entitySource.getValue(paramName);
      }

      @Override
      public int getSqlType(String paramName) {
        return other.hasValue(paramName)
            ? other.getSqlType(paramName)
            : entitySource.getSqlType(paramName);
      }

      @Override
      public String getTypeName(String paramName) {
        return other.hasValue(paramName)
            ? other.getTypeName(paramName)
            : entitySource.getTypeName(paramName);
      }
    };
  }

  String whereClause() {
    return whereClause;
  }

  static class Builder<T> {
    private final Class<T> type;
    private final JdbcTemplateMapperFactory sourceFactory = JdbcTemplateMapperFactory.newInstance();
    private final JdbcTemplateMapperFactory rowMapperFactory =
        JdbcTemplateMapperFactory.newInstance();

    private final List<String> columnNames = new ArrayList<>();

    private Builder(Class<T> type) {
      this.type = type;
    }

    Builder<T> ids(String... names) {
      sourceFactory.addKeys(names);
      rowMapperFactory.addKeys(names);
      return this;
    }

    <C> Builder<T> column(
        String column, Class<C> type, Function<C, String> to, Function<String, C> from) {
      columnNames.add(column);

      sourceFactory.addColumnProperty(
          column, ConverterProperty.<C, String>of((v, c) -> to.apply(v), type));
      sourceFactory.addColumnProperty(column, SqlTypeColumnProperty.of(Types.VARCHAR));

      rowMapperFactory.addColumnProperty(
          column, ConverterProperty.<String, C>of((v, c) -> from.apply(v), type));
      return this;
    }

    Builder<T> columns(String... names) {
      columnNames.addAll(Arrays.asList(names));
      return this;
    }

    private String makeWhereClause() {
      return columnNames.stream()
          .map(n -> format("%s = :%s", n, n))
          .collect(Collectors.joining(" AND "));
    }

    Mapper<T> build() {
      return new Mapper<>(
          type,
          sourceFactory,
          rowMapperFactory.newRowMapper(type),
          sourceFactory.newSqlParameterSourceFactory(type),
          makeWhereClause());
    }
  }
}
