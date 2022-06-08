/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service;

import com.datastax.butler.server.db.AuthoritiesDb;
import com.datastax.butler.server.db.UsersDb;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsersService {

  private static final String DEFAULT_ADMIN_USER = "admin";
  private static final String DEFAULT_ADMIN_PASSWORD = "foo";

  private final UsersDb usersDb;
  private final AuthoritiesDb authoritiesDb;

  /** Creates the service (Auto-wired by Spring). */
  @Autowired
  public UsersService(UsersDb usersDb, AuthoritiesDb authoritiesDb) {
    this.usersDb = usersDb;
    this.authoritiesDb = authoritiesDb;
  }

  /** Adds a default admin if none exists. */
  @PostConstruct
  public void addAdmin() {
    if (usersDb.exists(DEFAULT_ADMIN_USER)) return;

    usersDb.add(DEFAULT_ADMIN_USER, DEFAULT_ADMIN_PASSWORD);
    authoritiesDb.setAdmin(DEFAULT_ADMIN_USER);
  }
}
