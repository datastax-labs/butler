/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.projects;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.issues.IssueTrackingProject;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * A Project at a minimum contains a list of {@link com.datastax.butler.commons.dev.Workflow}s which
 * each describe the data butler will gather and display.
 *
 * <p>To have a new ButlerProject be recognized as supported, its workflows should be added to the
 * UpstreamWorkflowsDb constructor.
 */
public interface ButlerProject {

  /**
   * List of workflows defined for the project to be registered in butler.
   *
   * @return list of project related worklflows, can be empty.
   */
  @Nonnull
  List<Workflow> workflows();

  /**
   * List of jira projects that can be reported or linked for project workflows.
   *
   * @return list of project related jira projects, can be empty.
   */
  @Nonnull
  List<IssueTrackingProject> issueTrackingProjects();
}
