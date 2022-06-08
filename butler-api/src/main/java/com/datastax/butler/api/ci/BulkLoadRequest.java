/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.ci;

import com.datastax.butler.commons.jenkins.JobId;
import java.util.List;
import lombok.NonNull;
import lombok.Value;

/** Request for bulk loading the builds of CI jobs. */
@Value
public class BulkLoadRequest {
  @NonNull List<JobId> jobs;
  int maxBuildsPerJob;
}
