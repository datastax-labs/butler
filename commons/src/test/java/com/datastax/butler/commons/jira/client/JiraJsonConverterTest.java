/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.datastax.butler.commons.dev.JenkinsTestHelper;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class JiraJsonConverterTest {

  @Test
  void shouldParseJiraIssue() throws IOException {
    String json = JenkinsTestHelper.testResource("web/jira/issue.json");
    var converter = new JiraJsonConverter();
    var jiraIssue = converter.parseIssue(json);
    assertEquals("XYZ-42", jiraIssue.key);
    assertEquals("Do something super awesome", jiraIssue.fields.summary);
    assertEquals(Set.of("6.0.0"), jiraIssue.fields.fixVersions());
    assertEquals("Done", jiraIssue.fields.resolution());
    assertEquals("Closed", jiraIssue.fields.status());
  }
}
