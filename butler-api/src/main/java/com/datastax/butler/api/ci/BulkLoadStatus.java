/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.ci;

import com.datastax.butler.commons.jenkins.JobId;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.UUID;
import lombok.Value;

/** Status of the Butler server CI builds bulk-loading service. */
@Value
@JsonInclude(Include.NON_NULL)
public class BulkLoadStatus {
  public static final BulkLoadStatus NONE =
      new BulkLoadStatus(null, null, null, null, null, null, null, null);

  UUID taskId;
  JobId jobId;
  Boolean finished;
  Integer progress;
  Long duration;
  String error;
  List<String> messages;
  String updatedAt;
}
