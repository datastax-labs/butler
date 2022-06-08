/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.projects.apache.cassandra;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.issues.jira.JiraProject;
import org.junit.jupiter.api.Test;

class ApacheCassandraJiraProjectTest {

  private static final JiraProject CASSANDRA = ApacheCassandraJiraProject.forKey("CASSANDRA");

  @Test
  void forKeyShouldCreateJiraClient() {
    assertNotNull(ApacheCassandraJiraProject.forKey("CASSANDRA"));
  }

  @Test
  void toJiraClientShouldLinkToIssuesApacheOrg() {
    assertTrue(CASSANDRA.jira().toString().contains("issues.apache.org"));
  }

  @Test
  void jqlShouldBuildValidQueryWithCondition() {
    var expectedJql = "project in (CASSANDRA) AND labels=butler AND text~\"some text\"";
    assertEquals(expectedJql, CASSANDRA.issueSearchJQL("text~\"some text\""));
  }

  @Test
  void jqlShouldBuildValidQueryWithoutCondition() {
    var expectedJql = "project in (CASSANDRA) AND labels=butler";
    assertEquals(expectedJql, CASSANDRA.issueSearchJQL(""));
  }
}
