/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.issues.jira.JiraProject;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.commons.jira.client.JiraClient;
import com.datastax.butler.commons.projects.ButlerProject;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.db.WorkflowBranchesDb;
import com.datastax.butler.server.service.issues.IssueTrackersService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * Register workflows and issue tracking projects for projects tracked in butler.
 *
 * <p>It should get implementations of ButlerProjects from Spring. To have specific class discovered
 * and registered it has to be a @Component.
 */
@Component
public class ButlerProjectsRegistry {

  private static final Logger logger = LogManager.getLogger();

  private final UpstreamWorflowsDb workflowsDb;
  private final WorkflowBranchesDb workflowBranchesDb;
  private final IssueTrackersService issuesService;

  @Value("${butler.jira.default.project.enabled: false}")
  private boolean defaultJiraProjectEnabled;

  @Value("${butler.jira.key: }")
  private String defaultJiraProjectKey;

  @Value("${butler.jira.url: }")
  private String defaultJiraUrl;

  @Autowired
  public ButlerProjectsRegistry(
      List<ButlerProject> projects,
      IssueTrackersService issueService,
      UpstreamWorflowsDb workflowsDb,
      WorkflowBranchesDb workflowBranchesDb) {
    this.workflowsDb = workflowsDb;
    this.issuesService = issueService;
    this.workflowBranchesDb = workflowBranchesDb;
    for (ButlerProject p : projects) {
      logger.info("Registering project {}", p);
      p.issueTrackingProjects().forEach(issueService::registerProject);
      workflowsDb.registerWorkflows(p.workflows());
    }
  }

  /** Register all workflows from the database as basic workflows (for raw import). */
  @PostConstruct
  public void autoconfigureDatabaseWorkflows() {
    if (defaultJiraProjectEnabled) {
      registerDefaultJiraProject();
    }
    var databaseWorkflows =
        workflowsDb.databaseWorkflows().stream().map(WorkflowId::name).collect(Collectors.toSet());
    var registeredWorkflows =
        workflowsDb.allWorkflows().stream().map(Workflow::name).collect(Collectors.toSet());
    var workflowsToConfigure = Sets.difference(databaseWorkflows, registeredWorkflows);
    logger.info("Autoconfiguring db workflows: {}", workflowsToConfigure);
    var toRegister =
        workflowsToConfigure.stream().map(this::genericWorkflow).collect(Collectors.toList());
    workflowsDb.registerWorkflows(toRegister);
  }

  private Workflow genericWorkflow(String name) {
    var w = new Workflow(name, true);
    var configuredBranches = workflowBranchesDb.getBranchesForWorkflow(name);
    if (configuredBranches.isEmpty()) {
      w.setBranches("main", List.of());
    } else {
      String main;
      if (configuredBranches.contains("main")) main = "main";
      else if (configuredBranches.contains("master")) main = "master";
      else main = CollectionUtils.firstElement(configuredBranches);
      w.setBranches(main, configuredBranches);
    }
    if (defaultJiraProjectEnabled) {
      w.withJiraProjects(defaultJiraProjectKey, Collections.emptyList());
    }
    return w;
  }

  @VisibleForTesting
  public String defaultJiraProjectKey() {
    return defaultJiraProjectEnabled ? defaultJiraProjectKey : "";
  }

  private void registerDefaultJiraProject() {
    // some fields are in this case mandatory
    if (StringUtils.isBlank(defaultJiraProjectKey)) {
      throw new IllegalArgumentException(
          "butler.jira.project.key must be set if butler.jira.default.project.enabled is true");
    }
    if (StringUtils.isBlank(defaultJiraUrl)) {
      throw new IllegalArgumentException(
          "butler.jira.url must be set if butler.jira.default.project.enabled is true");
    }
    logger.info(
        "Registering jira project {} for jira url {}", defaultJiraProjectKey, defaultJiraUrl);
    var jiraClient = JiraClient.create(defaultJiraUrl);
    var jiraProject = new JiraProject(jiraClient, defaultJiraProjectKey);
    issuesService.registerProject(jiraProject);
  }
}
