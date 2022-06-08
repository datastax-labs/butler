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

/** Identifies a Jenkins build. */
@Value
@JsonDeserialize(using = BuildId.JsonDeserializer.class) // See comment on JsonDeserializer below.
public class BuildId implements Comparable<BuildId> {
  /** The identifier of the job the build is a build of. */
  @JsonUnwrapped JobId jobId;
  /** The number of the build. */
  @Schema(description = "A build number on Jenkins", example = "123")
  int buildNumber;

  /** bloody constructor. */
  public BuildId(JobId jobId, int buildNumber) {
    this.jobId = jobId;
    if (buildNumber <= 0)
      throw new IllegalArgumentException("Invalid build number: " + buildNumber);
    this.buildNumber = buildNumber;
  }

  @Override
  public int compareTo(BuildId that) {
    int jobIdCompare = this.jobId.compareTo(that.jobId);
    if (jobIdCompare != 0) return jobIdCompare;
    return Integer.compare(this.buildNumber, that.buildNumber);
  }

  @Override
  public String toString() {
    return String.format("%s > #%d", jobId, buildNumber);
  }

  /**
   * JSon Deserializer for a BuildId.
   *
   * <p>See comment on {@link JobId.JsonDeserializer}: this apply here in its entirety.
   */
  public static class JsonDeserializer extends Json.ObjectDeserializer<BuildId> {

    public JsonDeserializer() {
      super(BuildId.class);
    }

    @Override
    protected BuildId readFields(JsonNode node, DeserializationContext ctx)
        throws JsonMappingException {

      String workflow = getField(node, "workflow", ctx);
      String job = getField(node, "job_name", ctx);
      int build = getIntField(node, "build_number", ctx);
      return WorkflowId.of(workflow).job(Branch.fromString(job)).build(build);
    }
  }
}
