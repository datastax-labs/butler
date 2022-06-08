/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.json.Json;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** Identifies a Jenkins Job. */
@Value
@JsonDeserialize(using = JobId.JsonDeserializer.class) // See comment on JsonDeserializer below.
public class JobId implements Comparable<JobId> {
  /** The workflow the Job is part of. */
  @JsonUnwrapped WorkflowId workflow;
  /**
   * The name of the job.
   *
   * <p>Note that we interpret job names are being branch names (see {@link Branch}). Which this is
   * not technically exact, this correspond to how we create jobs in practice.
   */
  @Schema(description = "Name of a job on Jenkins", example = "main")
  Branch jobName;

  /**
   * The {@link BuildId} corresponding to the provided build number within this job.
   *
   * <p>Please note that this method does not ensure the build itself exists or not.
   *
   * @param buildNumber the number of the build.
   * @return the created build identifier.
   */
  public BuildId build(int buildNumber) {
    return new BuildId(this, buildNumber);
  }

  @Override
  public int compareTo(JobId that) {
    int workFlowCompare = this.workflow.compareTo(that.workflow);
    if (workFlowCompare != 0) return workFlowCompare;
    return this.jobName.compareTo(that.jobName);
  }

  @Override
  public String toString() {
    return String.format("%s > %s", workflow, jobName);
  }

  /** Categories of Jobs. */
  public enum Category {
    UPSTREAM, // upstream job e.g nightly on upstream branch
    USER // user job e.g. build on a PR or user branch
  }

  /** Create JobId for given workflow and branch. */
  public static JobId forWorkflowAndBranch(String workflow, String branch) {
    return new JobId(WorkflowId.of(workflow), Branch.fromString(branch));
  }

  /**
   * JSon Deserializer for a JobId.
   *
   * <p>This shouldn't be needed as the @JsonUnwrapped annotation on 'workflow' in {@link JobId}
   * should be enough, but that's a current limitation of Jackson that this currently doesn't work
   * (it work for serialization, which is why we still have it). See
   * https://github.com/FasterXML/jackson-databind/issues/1467 for details. So, for now, this is a
   * work-around, but hopefully we can get rid of it at some point.
   */
  public static class JsonDeserializer extends Json.ObjectDeserializer<JobId> {

    public JsonDeserializer() {
      super(JobId.class);
    }

    @Override
    protected JobId readFields(JsonNode node, DeserializationContext ctx)
        throws JsonMappingException {

      String workflow = getField(node, "workflow", ctx);
      String job = getField(node, "job_name", ctx);
      return WorkflowId.of(workflow).job(Branch.fromString(job));
    }
  }
}
