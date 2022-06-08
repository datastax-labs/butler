/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.github;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GithubIssueIdTest {

  @Test
  void shouldParseValidId() {
    var id = new GithubIssueId("example/repo#123");
    assertEquals("#123", id.toString());
    assertEquals("example/repo", id.projectName());
  }

  @Test
  void shouldParseComplesId() {
    var id = new GithubIssueId("example-org/repo_for_app#123");
    assertEquals("#123", id.toString());
    assertEquals("example-org/repo_for_app", id.projectName());
  }

  @Test
  void shouldThrowOnIncompleteName() {
    assertThrows(IllegalArgumentException.class, () -> new GithubIssueId("example/repo"));
    assertThrows(IllegalArgumentException.class, () -> new GithubIssueId("example/repo#"));
    assertThrows(IllegalArgumentException.class, () -> new GithubIssueId("example#992"));
  }
}
