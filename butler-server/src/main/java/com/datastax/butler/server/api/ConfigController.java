/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import com.datastax.butler.api.commons.LoggedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller pertaining to the configuration of Butler and what it controls. */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

  @Value("${butlerBrand}")
  private String butlerBrand;

  /** Retrieve information on the currently logged user (if any). */
  @GetMapping("/user")
  public LoggedUser loggedUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }

    String name = authentication.getName();
    boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    return new LoggedUser(name, isAdmin);
  }

  @GetMapping("/brand")
  public String brand() {
    return butlerBrand;
  }
}
