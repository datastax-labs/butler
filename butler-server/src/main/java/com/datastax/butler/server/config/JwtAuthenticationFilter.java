/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.config;

import com.datastax.butler.commons.json.Json;
import com.datastax.butler.commons.web.Credentials;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Instant;
import java.util.Date;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
  private final JwtConfig config;

  JwtAuthenticationFilter(AuthenticationManager authenticationManager, JwtConfig config) {
    this.config = config;
    setAuthenticationManager(authenticationManager);
    setFilterProcessesUrl("/api/login");
  }

  @Override
  public Authentication attemptAuthentication(
      HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

    if (!request.getMethod().equals("POST")) {
      throw new AuthenticationServiceException(
          "Authentication method not supported: " + request.getMethod());
    }

    try {
      Credentials credentials = Json.fromJson(request.getInputStream(), Credentials.class);
      UsernamePasswordAuthenticationToken authRequest =
          new UsernamePasswordAuthenticationToken(credentials.login(), credentials.password());

      // Allow subclasses to set the "details" property
      setDetails(request, authRequest);

      return this.getAuthenticationManager().authenticate(authRequest);
    } catch (Exception e) {
      logger.info("Error with credentials = " + e);
      throw new AuthenticationServiceException("Error handling user credentials: " + e);
    }
  }

  @Override
  protected void successfulAuthentication(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      Authentication authentication) {
    User user = (User) authentication.getPrincipal();
    var expireDate = Instant.now().plusMillis(JwtConfig.TOKEN_EXPIRATION_MILLIS);
    String token =
        Jwts.builder()
            .signWith(config.secretKey(), SignatureAlgorithm.HS512)
            .setSubject(user.getUsername())
            .setExpiration(Date.from(expireDate))
            .compact();

    response.addHeader(HttpHeaders.AUTHORIZATION, JwtConfig.HEADER_PREFIX + token);
  }
}
