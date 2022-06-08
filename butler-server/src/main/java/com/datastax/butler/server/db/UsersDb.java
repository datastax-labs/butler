/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

/**
 * Handles accesses to the Users in the database (so maintenance of the {@link UsersDb#TABLE}
 * table).
 */
@Repository
public class UsersDb extends DbTableService {
  public static final String TABLE = "users";

  private final PasswordEncoder passwordEncoder;
  private final TableMapper<StoredUser, String> usersMapper;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public UsersDb(NamedParameterJdbcTemplate template, PasswordEncoder passwordEncoder) {
    super(template, TABLE);
    this.passwordEncoder = passwordEncoder;
    this.usersMapper = tableMapper(StoredUser.class, String.class);
  }

  /** Whether the provided user exists in the database. */
  public boolean exists(String username) {
    return usersMapper.get(username).isPresent();
  }

  /** Adds a new user to the database. */
  public void add(String username, String password) {
    usersMapper.insert(new StoredUser(username, passwordEncoder.encode(password), true));
  }
}
