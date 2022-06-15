/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.projects.apache.cassandra;

import com.datastax.butler.commons.issues.jira.JiraProject;
import com.datastax.butler.commons.jira.client.JiraClient;

/**
 * Apache Jira Project e.g. CASSANDRA Issues reported should have: - issue type "Task" - set
 * component set to CI - "butler" label
 */
public class ApacheCassandraJiraProject extends JiraProject {

  private static final String URL = "https://issues.apache.org/jira";

  ApacheCassandraJiraProject(JiraClient jiraClient, String projectKey) {
    super(jiraClient, projectKey);
  }

  /** Create new Apache jira project for given key. */
  public static JiraProject forKey(String projectKey) {
    return new ApacheCassandraJiraProject(JiraClient.create(URL), projectKey);
  }

  @Override
  public String issueTemplateName() {
    return "cassandra-issue.ftl";
  }

  boolean isResolutionClosed(String resolution) {
      return Set.of("Fixed", "Done", "Not A Problem").contains(resolution);
  }
}
