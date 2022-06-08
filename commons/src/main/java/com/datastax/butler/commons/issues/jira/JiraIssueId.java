/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.jira;

import com.datastax.butler.commons.issues.IssueId;
import org.apache.commons.lang3.StringUtils;

public class JiraIssueId extends IssueId {

  final String projectKey;

  public JiraIssueId(String id) {
    super(id);
    this.projectKey = extractProjectKey(id);
  }

  @Override
  public String projectName() {
    return projectKey;
  }

  private String extractProjectKey(String id) {
    var split = StringUtils.split(id, "-");
    if (split.length != 2)
      throw new IllegalArgumentException("Cannot parse jira id to extract project key: " + id);
    var projectKey = split[0];
    var issueNumber = Integer.parseInt(split[1]);
    if (issueNumber < 1)
      throw new IllegalArgumentException("Invalid issue number (should be > 0) in: " + id);
    return projectKey;
  }
}
