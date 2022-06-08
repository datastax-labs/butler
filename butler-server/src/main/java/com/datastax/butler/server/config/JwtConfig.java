/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.jsonwebtoken.security.Keys;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Configuration relates to JSON Web Tokens (<a href="https://jwt.io">JWT</a>). */
@Configuration
public class JwtConfig {
  static final String HEADER_PREFIX = "Bearer ";
  static final long TOKEN_EXPIRATION_MILLIS = TimeUnit.DAYS.toMillis(1);

  private final SecretKey secretKey;

  /** Creates the configuration (Wired by Spring). */
  @Autowired
  public JwtConfig(@Value("${jwt.secret}") String jwtSecret) {
    this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(UTF_8));
  }

  SecretKey secretKey() {
    return secretKey;
  }
}
