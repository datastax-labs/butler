/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.config;

import com.datastax.butler.server.db.AuthoritiesDb;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {
  protected static final Logger log = LogManager.getLogger();

  private final JwtParser claimsParser;
  private final AuthoritiesDb authoritiesDb;

  JwtAuthorizationFilter(
      AuthenticationManager authenticationManager, JwtConfig config, AuthoritiesDb authoritiesDb) {
    super(authenticationManager);
    this.claimsParser = Jwts.parserBuilder().setSigningKey(config.secretKey()).build();
    this.authoritiesDb = authoritiesDb;
  }

  private UsernamePasswordAuthenticationToken parseToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(JwtConfig.HEADER_PREFIX)) {
      return null;
    }

    String token = header.substring(JwtConfig.HEADER_PREFIX.length());
    try {
      String username = this.claimsParser.parseClaimsJws(token).getBody().getSubject();
      if (username == null || username.isEmpty()) {
        return null;
      }

      List<SimpleGrantedAuthority> authorities = authoritiesDb.grantedAuthorities(username);
      return new UsernamePasswordAuthenticationToken(username, null, authorities);
    } catch (JwtException exception) {
      log.warn("Error decoding JWT token {}: {}", token, exception.getMessage());
      return null;
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {
    UsernamePasswordAuthenticationToken authentication = parseToken(request);

    if (authentication != null) {
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } else {
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }
}
