/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira;

import static freemarker.template.Configuration.VERSION_2_3_31;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.datastax.butler.commons.issues.jira.JiraIssue;
import com.datastax.butler.commons.issues.jira.JiraProject;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jira.client.JiraClient;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;

class JiraProjectTest {

  private static final TestCategory UNIT_TEST = TestCategory.valueOf("UNIT");
  private static final JiraClient client = JiraClient.create("https://jira.example.com");
  private static final JiraProject XYZ = new JiraProject(client, "XYZ");

  @Test
  void jqlShouldBuildValidQueryWithCondition() {
    var expectedJql = "project in (XYZ) AND labels=butler AND text~\"some text\"";
    assertEquals(expectedJql, XYZ.issueSearchJQL("text~\"some text\""));
  }

  @Test
  void jqlShouldBuildValidQueryWithoutCondition() {
    var expectedJql = "project in (XYZ) AND labels=butler";
    assertEquals(expectedJql, XYZ.issueSearchJQL(""));
  }

  @Test
  void shouldTryToFindMatchingIssuesInAllProjects() {
    var testName = new TestName(UNIT_TEST, "com.example", "TestClass", "testA");
    var jiraClient = Mockito.mock(JiraClient.class);
    var project = new JiraProject(jiraClient, "XYZ");
    project.searchIssuesForTest(testName, false);
    var expectedJql =
        "project in (XYZ) AND ("
            + "summary ~ 'com.example.TestClass.testA' OR "
            + "description ~ 'com.example.TestClass.testA')";
    verify(jiraClient, times(1)).search(expectedJql);
  }

  @Test
  void shouldRenderJiraIssueUsingTemplate() throws TemplateException, IOException, JSONException {
    // given
    var jiraClient = Mockito.mock(JiraClient.class);
    var project = new JiraProject(jiraClient, "XYZ");
    var templateConfig = new Configuration(VERSION_2_3_31);
    var templateLoader = new ClassTemplateLoader(getClass(), "/templates");
    templateConfig.setTemplateLoader(templateLoader);
    project.withTemplateConfiguration(templateConfig);
    // when
    var issue = new JiraIssue(null, "omG", "massacre");
    String issueJson = project.issueToJson(issue, null);
    // then
    assertFalse(issueJson.isEmpty());
    JSONAssert.assertEquals("{fields: { issuetype: { name: Task } } }", issueJson, false);
    JSONAssert.assertEquals("{fields: { project: { key: XYZ } } }", issueJson, false);
    JSONAssert.assertEquals("{fields: { summary: omG } }", issueJson, false);
    JSONAssert.assertEquals("{fields: { description: massacre } }", issueJson, false);
  }
}
