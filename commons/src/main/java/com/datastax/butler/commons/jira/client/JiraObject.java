/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import com.datastax.butler.commons.issues.jira.JiraIssue;
import com.datastax.butler.commons.issues.jira.JiraIssueId;

public class JiraObject {
  String key;
  JiraIssueFields fields;

  public JiraIssue toIssue() {
    var id = new JiraIssueId(key);
    var issue = new JiraIssue(id, fields.summary, fields.description);
    issue.setResolution(fields.resolution());
    issue.setStatus(fields.status());
    return issue;
  }
}
