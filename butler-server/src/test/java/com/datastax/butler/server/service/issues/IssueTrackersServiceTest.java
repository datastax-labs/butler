/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.issues;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.datastax.butler.commons.issues.jira.JiraProject;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jira.client.JiraClient;
import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IssueTrackersServiceTest {

  private static final TestCategory UNIT_TEST = TestCategory.valueOf("UNIT");

  @Test
  void shouldThrowWhenGettingUnregistredProject() {
    var service = new IssueTrackersService(null);
    var prj = "NEP";
    assertThrows(IllegalArgumentException.class, () -> service.getProject(prj));
  }

  @Test
  void shouldTryToFindMatchingIssuesInSelectedProjects() {
    var service = new IssueTrackersService(null);
    var jiraClientA = Mockito.mock(JiraClient.class);
    var jiraClientB = Mockito.mock(JiraClient.class);
    var jiraClientC = Mockito.mock(JiraClient.class);
    service.registerProject(new JiraProject(jiraClientA, "AAA"));
    service.registerProject(new JiraProject(jiraClientB, "BBB"));
    service.registerProject(new JiraProject(jiraClientC, "CCC"));
    var testName = new TestName(UNIT_TEST, "com.example", "TestClass", "testA");
    service.searchOpenIssuesForTest(List.of("AAA", "CCC"), testName);
    verify(jiraClientA, times(1)).search(anyString());
    verify(jiraClientB, times(0)).search(anyString());
    verify(jiraClientC, times(1)).search(anyString());
  }

  @Test
  void shouldReturnEmptyMatchingIssuesForEmptyListOfProjects() {
    var service = new IssueTrackersService(null);
    var testName = new TestName(UNIT_TEST, "com.example", "TestClass", "testA");
    assertTrue(service.searchOpenIssuesForTest(Lists.emptyList(), testName).isEmpty());
  }
}
