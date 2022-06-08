/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.jira;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JiraIssueIdTest {

  @Test
  void shouldBuildJiraIssueId() {
    assertEquals("XYZ", new JiraIssueId("XYZ-123").projectName());
  }

  @Test
  void shouldThrowOnNonPositiveIssueNumber() {
    assertThrows(IllegalArgumentException.class, () -> new JiraIssueId("XYZ-0"));
  }

  @Test
  void shouldThrowOnInvalidJiraIssueId() {
    assertThrows(IllegalArgumentException.class, () -> new JiraIssueId("NOTAJIRAID"));
    assertThrows(IllegalArgumentException.class, () -> new JiraIssueId("XYZ-AAA"));
    assertThrows(IllegalArgumentException.class, () -> new JiraIssueId("XYZ-123-AAA"));
  }
}
