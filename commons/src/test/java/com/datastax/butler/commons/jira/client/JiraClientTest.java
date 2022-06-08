/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.issues.jira.JiraIssueId;
import com.datastax.butler.commons.web.Credentials;
import com.datastax.butler.commons.web.TestJsonWebClient;
import org.junit.jupiter.api.Test;

/** Tests the {@link JiraClient} using faked answers through {@link TestJsonWebClient}. */
class JiraClientTest {

  private final TestJsonWebClient webClient = new TestJsonWebClient(JiraClientTest.class);
  private final Credentials credentials = new Credentials("foobar", "42");
  private final JiraClient client =
      new JiraClient("https://jira.example.com", webClient, credentials);

  @Test
  void testCreateLink() {
    var issueLink = client.link(new JiraIssueId("XYZ-22"));
    assertEquals("https://jira.example.com/browse/XYZ-22", issueLink.url().toString());
    var apache = new JiraClient("http://issues.apache.org/jira", webClient, credentials);
    var cass123 = apache.link(new JiraIssueId("CASSANDRA-123"));
    assertEquals("http://issues.apache.org/jira/browse/CASSANDRA-123", cass123.url().toString());
  }

  @Test
  void testFetchMissingIssue() {
    JiraIssueId ticket = new JiraIssueId("PRJ-43");
    client
        .fetchIssue(ticket)
        .ifPresent(issue -> fail("Should not have found issue PRJ-43 but found " + issue));
  }

  @Test
  void shouldNotFoundAnyIssueForInvalidSearch() {
    var jql = "project in (NON-EXISTING-PROJECT)";
    var found = client.search(jql);
    assertTrue(found.isEmpty());
  }

  @Test
  void shouldTryCreateIssueViaPostRequest() {
    var jiraClient = new JiraClient("http://jira.example.com", webClient);
    JiraException exc =
        assertThrows(
            JiraException.class,
            () -> {
              jiraClient.createIssue("{ fields: { projectKey: XYZ } }");
            });
    assertTrue(exc.getMessage().contains("404"));
    assertTrue(exc.getMessage().contains("http://jira.example.com/rest/api/2/issue"));
  }

  @Test
  void testEscapeJqlPhrase() {
    assertEquals("test_name?param?3?", JiraClient.escapeJqlPhrase("test_name[param=3]"));
  }
}
