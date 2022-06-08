/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.jira;

import com.datastax.butler.commons.issues.Issue;
import com.datastax.butler.commons.issues.IssueId;
import java.util.HashSet;
import java.util.Set;

/**
 * JIRA Issue adds some fields that are present in every jira project: resolution, status,
 * affectsVersions ... Please notice that in jira everything can be configured per installation or
 * per project => we do not use enums. *
 */
public class JiraIssue extends Issue {

  /**
   * Jira resolution can be any of the predefined values: Fixed, Duplicate, Won't Fix, Incomplete,
   * and Cannot Reproduce but set of available resolutions can be different per jira installation.
   */
  private String resolution;

  /** Status of the jira ticket. */
  private String status;

  /** Set of affects versions values for ticket (potentially empty). */
  private final Set<String> affectsVersions = new HashSet<>();

  /** Construct basic Issue object with key, title and body. */
  public JiraIssue(IssueId id, String title, String body) {
    super(id, title, body);
  }

  public String resolution() {
    return resolution;
  }

  public void setResolution(String res) {
    this.resolution = res;
  }

  public String status() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
