/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.projects;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.issues.IssueTrackingProject;
import com.datastax.butler.commons.issues.jira.JiraProject;
import com.datastax.butler.commons.jira.client.JiraClient;
import com.datastax.butler.commons.projects.ButlerProject;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ExampleProject implements ButlerProject {
  @NonNull
  @Override
  public List<Workflow> workflows() {
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public List<IssueTrackingProject> issueTrackingProjects() {
    var jiraClient = JiraClient.create("http://jira.example.com");
    return List.of(new JiraProject(jiraClient, "EXAMPLEPRJ"));
  }
}
