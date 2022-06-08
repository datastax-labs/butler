/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.TestRunOutput;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
public class RunDetails {
  BuildId id;
  TestVariant variant;
  String url;
  long timestamp;
  boolean failed;
  boolean skipped;
  @NonFinal TestRunOutput output;

  public void clearOutput() {
    output = TestRunOutput.EMPTY_OUTPUT;
  }

  @VisibleForTesting
  public boolean hasOutput() {
    return output != null && !output.isEmpty();
  }

  static RunDetails max(RunDetails d1, RunDetails d2) {
    if (d1 == null) return d2;
    if (d2 == null) return d1;
    return d1.timestamp() < d2.timestamp() ? d2 : d1;
  }

  static RunDetails min(RunDetails d1, RunDetails d2) {
    if (d1 == null) return d2;
    if (d2 == null) return d1;
    return d1.timestamp() < d2.timestamp() ? d1 : d2;
  }

  Optional<BranchVersion> extractVersion() {
    Branch branch = id().jobId().jobName();
    return Optional.ofNullable(branch.version());
  }
}
