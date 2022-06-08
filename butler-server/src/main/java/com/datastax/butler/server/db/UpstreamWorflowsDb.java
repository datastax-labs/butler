/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.StringSanitizer;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Defined which {@link Workflow}s are supported (configured) and "observable" in the application.
 *
 * <p>All supported workflows are configured in this class. For all the projects that are
 * potentially supported: DSE, OSS, Stargazer. Then in the database table UPSTREAM_WORKFLOWS we keep
 * a list of what is interesting. So {UpstreamWorkflows} = {code defined} x {db configured}
 */
@Repository
public class UpstreamWorflowsDb extends DbTableService {
  public static final String TABLE = "upstream_workflows";

  private final TableMapper<WorkflowId, WorkflowId> workflowMapper;
  private final WorkflowRegistry workflows = new WorkflowRegistry();

  /** OMG. */
  @Autowired
  public UpstreamWorflowsDb(NamedParameterJdbcTemplate template) {
    super(template, TABLE);
    this.workflowMapper = tableMapper(WorkflowId.class, WorkflowId.class);
  }

  /** Register workflow(s) so that their definitions can be used in api calls. */
  public void registerWorkflows(Collection<Workflow> workflows) {
    logger.info("registering workflows: {}", StringUtils.join(workflows, ","));
    this.workflows.addAll(workflows);
  }

  /**
   * Return list of workflows that are "upstream" (monitorable).
   *
   * <p>This list is based on the configuration, database content and fact that they are running as
   * CI.
   */
  public Set<Workflow> upstreamWorkflows() {
    return workflows.stream()
        .filter(Workflow::runsAsCI)
        .filter(x -> databaseWorkflows().contains(x.workflowId()))
        .collect(Collectors.toSet());
  }

  /** Return list of ALL workflows configured for the application that are also in the database. */
  public Set<Workflow> allWorkflows() {
    return workflows.stream()
        .filter(x -> databaseWorkflows().contains(x.workflowId()))
        .collect(Collectors.toSet());
  }

  public Set<WorkflowId> databaseWorkflows() {
    return Sets.newHashSet(workflowMapper.getAll());
  }

  /** Updates the set of stored workflows to match the provided ones. */
  @Transactional
  public synchronized void update(Set<WorkflowId> workflows) {
    var current = databaseWorkflows();
    Set<WorkflowId> toAdd = Sets.difference(workflows, current);
    Set<WorkflowId> toRemove = Sets.difference(current, workflows);
    logger.info("Requested new upstream workflows to be: {}", toString(workflows));
    logger.info("Workflows to add: {}", toString(toAdd));
    logger.info("Workflows to remove: {}", toString(toRemove));
    workflowMapper.insert(toAdd);
    workflowMapper.delete(toRemove);
  }

  public Optional<Workflow> getWorkflow(String name) {
    return workflows.getWorkflow(name);
  }

  public Optional<Workflow> getWorkflow(WorkflowId id) {
    return workflows.getWorkflow(id);
  }

  private Collection<String> toString(Set<WorkflowId> ids) {
    return StringSanitizer.sanitize(
        ids.stream().map(WorkflowId::toString).collect(Collectors.toList()));
  }

  @Value
  private static class StoredWorkflowBranch {
    String workflow;
    String branch;
  }
}
