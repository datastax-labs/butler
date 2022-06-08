/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import static java.lang.String.format;

import com.datastax.butler.commons.refs.Ref;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.simpleflatmapper.jdbc.spring.JdbcTemplateCrud;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class TableMapper<T, K> {
  private final NamedParameterJdbcTemplate template;
  private final String table;
  private final Mapper<T> mapper;
  private final Class<K> keyClass;
  private final JdbcTemplateCrud<T, K> crud;

  TableMapper(
      NamedParameterJdbcTemplate template, String table, Mapper<T> valueMapper, Class<K> keyClass) {
    this.template = template;
    this.table = table;
    this.keyClass = keyClass;
    this.mapper = valueMapper;
    this.crud =
        mapper.factory().crud(mapper.type(), keyClass).to(template.getJdbcOperations(), table);
  }

  TableMapper(
      NamedParameterJdbcTemplate template, String table, Class<T> valueClass, Class<K> keyClass) {
    this(template, table, Mapper.create(valueClass), keyClass);
  }

  @FormatMethod
  private String q(String query, Object... args) {
    return format(query, args);
  }

  Mapper<T> mapper() {
    return mapper;
  }

  K insert(T entity) {
    Ref<K> kRef = Ref.ref(keyClass);
    crud.create(entity, kRef::set);
    return kRef.get();
  }

  void insert(Collection<T> entities) {
    if (entities.isEmpty()) return;
    crud.create(entities);
  }

  void insertOrUpdate(T entity) {
    crud.createOrUpdate(entity);
  }

  void update(T entity) {
    crud.update(entity);
  }

  void delete(K key) {
    crud.delete(key);
  }

  void delete(Collection<K> keys) {
    if (keys.isEmpty()) return;
    crud.delete((keys instanceof List) ? (List<K>) keys : new ArrayList<>(keys));
  }

  Optional<T> get(K key) {
    return Optional.ofNullable(crud.read(key));
  }

  List<T> getAll() {
    return template.query(q("SELECT * FROM %s", table), mapper.rowMapper());
  }

  List<T> getWhere(String whereClause, Map<String, Object> params) {
    return template.query(
        q("SELECT * FROM %s WHERE %s", table, whereClause), params, mapper.rowMapper());
  }

  List<T> getWhere(String whereClause, Map<String, Object> params, String orderBy) {
    return template.query(
        q("SELECT * FROM %s WHERE %s ORDER BY %s", table, whereClause, orderBy),
        params,
        mapper.rowMapper());
  }

  @FormatMethod
  List<T> getJoinedWhere(String joinClause, @FormatString String whereFormat, Object... args) {
    String whereClause = String.format(whereFormat, args);
    return template.query(
        q("SELECT t.* FROM %s AS t %s WHERE %s", table, joinClause, whereClause),
        mapper.rowMapper());
  }

  <UniqueKey> Optional<T> getUnique(UniqueKey key, Mapper<UniqueKey> keyMapper) {
    List<T> results =
        template.query(
            q("SELECT * FROM %s WHERE %s", table, keyMapper.whereClause()),
            keyMapper.source(key),
            mapper.rowMapper());

    if (results.size() > 1) {
      throw new IllegalArgumentException(
          format("The provided key %s does not return a unique result (got %s)", key, results));
    }

    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }
}
