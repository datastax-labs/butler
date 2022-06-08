/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues;

import com.datastax.butler.commons.issues.github.GithubIssueId;
import com.datastax.butler.commons.issues.jira.JiraIssueId;

public abstract class IssueId {

  final String id;

  public IssueId(String id) {
    this.id = id;
  }

  public abstract String projectName();

  public String id() {
    return id;
  }

  /** Create IssueId from given string: either JiraIssue or Github Issue or generic one. */
  public static IssueId fromString(String id) {
    try {
      return new JiraIssueId(id);
    } catch (IllegalArgumentException notJira) {
      try {
        return new GithubIssueId(id);
      } catch (IllegalArgumentException notGithub) {
        throw new IllegalArgumentException("Unrecognized issue tracking issue type: " + id);
      }
    }
  }

  @Override
  public String toString() {
    return id;
  }
}
