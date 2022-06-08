/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.ButlerProjectsRegistry;
import com.datastax.butler.server.IntegrationTest;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.db.WorkflowBranchesDb;
import com.datastax.butler.server.service.issues.IssueTrackersService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test for workflow autoconfiguration.
 *
 * <p>Every workflow defined in the UPSTREAM_WORKFLOWS db should be registered as simple workflow
 * for raw build imports with branches defined in the MAINTAINED_VERSIONS unless it is already
 * defined by components discovery
 */
public class WorkflowsAutoconfigurationTest extends IntegrationTest {

  @Autowired private UpstreamWorflowsDb workflowsDb;
  @Autowired private WorkflowBranchesDb workflowBranchesDb;
  @Autowired private ButlerProjectsRegistry projectsRegistry;
  @Autowired private IssueTrackersService issuesService;

  @Test
  void shouldRegisterWorkflowsFromUpstreamWorkflowsTable() {
    // given
    var testWorkflows = List.of("potter-stinks", "weasley-is-our-king");
    updateWorkflowsInDb(testWorkflows);
    // when
    var harryCI = workflowsDb.getWorkflow("potter-stinks");
    var ronCI = workflowsDb.getWorkflow("weasley-is-our-king");
    // then
    assertTrue(harryCI.isPresent());
    assertTrue(ronCI.isPresent());
  }

  @Test
  void shouldNotOverrideConfiguredWorkflow() {
    // given some already registered workflow with specific jenkins configuration
    var lumosJenkinsUrl = "https://jenkins.lumos.example.com";
    var lumosWorkflow = new Workflow("lumos", false).withJenkinsUrl(lumosJenkinsUrl);
    workflowsDb.registerWorkflows(List.of(lumosWorkflow));
    // when workflows are auto-configured based on the db content
    updateWorkflowsInDb(List.of("lumos", "reparo"));
    var lumosCI = workflowsDb.getWorkflow("lumos");
    var reparoCI = workflowsDb.getWorkflow("reparo");
    // then this already registered one should remain unchanged
    assertTrue(lumosCI.isPresent());
    assertEquals(lumosJenkinsUrl, lumosCI.get().getJenkinsUrl());
    assertTrue(reparoCI.isPresent());
    assertTrue(StringUtils.isBlank(reparoCI.get().getJenkinsUrl()));
  }

  @Test
  void shouldRegisterDefaultJiraProject() {
    // given BJR configured in application-integrationTest.properties
    // when
    var defaultJiraProjectKey = projectsRegistry.defaultJiraProjectKey();
    // then
    assertFalse(StringUtils.isBlank(defaultJiraProjectKey));
    var defaultJiraProject = issuesService.getProject(defaultJiraProjectKey);
    assertEquals(defaultJiraProjectKey, defaultJiraProject.projectName());
  }

  @Test
  void autoconfiguredProjectShouldHaveDefaultJiraProject() {
    // given BJR configured in application-integrationTest.properties
    var testWorkflows = List.of("potter-stinks");
    updateWorkflowsInDb(testWorkflows);
    // when
    var harryCI = workflowsDb.getWorkflow("potter-stinks");
    // then
    assertTrue(harryCI.isPresent());
    assertTrue(harryCI.get().allJiraProjects().contains("BJR"));
  }

  @Test
  void autoconfiguredWorkflowsShouldUseBranchesFromDb() {
    // given
    workflowBranchesDb.addBranchForWorkflow("voldi-ci", "master");
    workflowBranchesDb.addBranchForWorkflow("voldi-ci", "1.0-dev");
    workflowsDb.update(Set.of(WorkflowId.of("voldi-ci")));
    projectsRegistry.autoconfigureDatabaseWorkflows();
    // when
    var voldiCI = workflowsDb.getWorkflow("voldi-ci");
    // then
    assertTrue(voldiCI.isPresent());
    assertTrue(voldiCI.get().isBranchUpstream("master"));
    assertTrue(voldiCI.get().isBranchUpstream("1.0-dev"));
    assertFalse(voldiCI.get().isBranchUpstream("my-branch"));
  }

  private void updateWorkflowsInDb(List<String> names) {
    workflowsDb.update(names.stream().map(WorkflowId::of).collect(Collectors.toSet()));
    // we need to kick it after updating db
    projectsRegistry.autoconfigureDatabaseWorkflows();
  }
}
