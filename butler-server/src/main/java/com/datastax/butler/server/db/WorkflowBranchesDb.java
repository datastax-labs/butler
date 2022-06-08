/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Maintains WORKFLOW_BRANCHES table content. */
@Repository
public class WorkflowBranchesDb extends DbTableService {
  public static final String WORKFLOW_BRANCHES_TABLE = "workflow_branches";

  private final TableMapper<StoredWorkflowBranch, StoredWorkflowBranch> workflowBranchesMapper;

  @Autowired
  public WorkflowBranchesDb(NamedParameterJdbcTemplate template) {
    super(template, WORKFLOW_BRANCHES_TABLE);
    this.workflowBranchesMapper =
        tableMapper(StoredWorkflowBranch.class, StoredWorkflowBranch.class);
  }

  /** Insert connection between workflow and branch . */
  public void addBranchForWorkflow(String workflow, String branch) {
    var row = new StoredWorkflowBranch(workflow, branch);
    workflowBranchesMapper.insert(row);
  }

  /** Return all branches configured in the db for given workflow. */
  public Set<String> getBranchesForWorkflow(String workflow) {
    return workflowBranchesMapper.getWhere("workflow=:workflow", Map.of("workflow", workflow))
        .stream()
        .map(StoredWorkflowBranch::branch)
        .collect(Collectors.toSet());
  }

  @Value
  public static class StoredWorkflowBranch {
    String workflow;
    String branch;
  }
}
