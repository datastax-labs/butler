/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.config;

import com.datastax.butler.server.db.AuthoritiesDb;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration.
 *
 * <p>Currently, this is mostly interested in allowing users to login and to restrict access to a
 * number of resources to an "admin" role.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(jsr250Enabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
  private final DataSource dataSource;

  private final JwtConfig jwtConfig;
  private final AuthoritiesDb authoritiesDb;

  /** Creates the configuration (Wired by Spring). */
  @Autowired
  public SecurityConfig(DataSource dataSource, JwtConfig jwtConfig, AuthoritiesDb authoritiesDb) {
    this.dataSource = dataSource;
    this.jwtConfig = jwtConfig;
    this.authoritiesDb = authoritiesDb;
  }

  /** The encoder uses to encode password in the database. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    // Note that we 'permit all' on everything below, but we use 'jsr 250 annotations in the
    // controllers that needs authentication'
    http.cors()
        .and()
        .csrf()
        .disable()
        .addFilter(new JwtAuthenticationFilter(authenticationManager(), jwtConfig))
        .addFilter(new JwtAuthorizationFilter(authenticationManager(), jwtConfig, authoritiesDb))
        .authorizeRequests(authorizeRequests -> authorizeRequests.anyRequest().permitAll())
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }

  @Override
  protected void configure(AuthenticationManagerBuilder builder) throws Exception {
    builder
        .jdbcAuthentication()
        .dataSource(dataSource)
        .passwordEncoder(passwordEncoder())
        .usersByUsernameQuery("SELECT username, password, enabled FROM users where username = ?")
        .authoritiesByUsernameQuery(
            "SELECT username, authority FROM authorities where username = ?");
  }
}
