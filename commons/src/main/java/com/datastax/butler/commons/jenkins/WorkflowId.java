/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.datastax.butler.commons.dev.Branch;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** Identifies a Jenkins "workflow" (which contains Jobs). */
@Value
public class WorkflowId implements Comparable<WorkflowId> {

  /** The name of the workflow. */
  @Schema(description = "Name of a 'workflow' on Jenkins", example = "Cassandra-trunk")
  @JsonProperty("workflow")
  String name;

  private WorkflowId(String name) {
    this.name = name;
  }

  /** Creates the workflow identifier for the provided name. */
  @JsonCreator
  public static WorkflowId of(@JsonProperty("workflow") String name) {
    return new WorkflowId(name);
  }

  /**
   * The {@link JobId} corresponding to the provided job within this workflow.
   *
   * <p>Please note that this method does not ensure the job itself exists or not.
   *
   * @param jobName the name of the job.
   * @return the created job identifier.
   */
  public JobId job(Branch jobName) {
    return new JobId(this, jobName);
  }

  @Override
  public int compareTo(WorkflowId that) {
    return this.name.compareTo(that.name);
  }

  @Override
  public String toString() {
    return name;
  }
}
