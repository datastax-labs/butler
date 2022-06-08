/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Repository;

/**
 * Handles accesses to the user authorities in the database (so maintenance of the {@link
 * AuthoritiesDb#TABLE}).
 */
@Repository
public class AuthoritiesDb extends DbTableService {
  public static final String TABLE = "authorities";

  private final TableMapper<StoredAuthority, StoredAuthority> authoritiesMapper;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public AuthoritiesDb(NamedParameterJdbcTemplate template) {
    super(template, TABLE);
    this.authoritiesMapper = tableMapper(StoredAuthority.class, StoredAuthority.class);
  }

  /**
   * Adds the ADMIN role to the provided user.
   *
   * @param username the user to which to add the ADMIN role (that user must exists).
   */
  public void setAdmin(String username) {
    authoritiesMapper.insertOrUpdate(new StoredAuthority(username, "ROLE_ADMIN"));
  }

  /**
   * The authorities granted to the provided user.
   *
   * @param username the username for which to retrieve the authorities.
   * @return a list of {@code username} authorities. This will be empty if the user does not exists.
   */
  public List<SimpleGrantedAuthority> grantedAuthorities(String username) {
    return authoritiesMapper.getAll().stream()
        .map(a -> new SimpleGrantedAuthority(a.authority()))
        .collect(Collectors.toUnmodifiableList());
  }
}
