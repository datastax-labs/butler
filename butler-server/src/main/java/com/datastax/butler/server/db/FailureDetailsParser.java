/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.FailureDetails;
import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.TestRunOutput;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;

public class FailureDetailsParser {

  private static final Splitter splitOnPipe = Splitter.on('|');

  FailureDetails parseFailureData(String builds) {
    List<RunDetails> details = new ArrayList<>();
    if (builds != null) {
      for (String str : Splitter.on(',').split(builds)) {
        details.add(parseBuildFailureData(str));
      }
    }
    return FailureDetails.build(details);
  }

  RunDetails parseBuildFailureData(String concatenatedData) {
    List<String> parts = splitOnPipe.splitToList(concatenatedData);
    if (parts.size() < 8) {
      return null;
    }
    String workflow = parts.get(0);
    Branch branch = Branch.fromString(parts.get(1));
    int buildNumber = Integer.parseInt(parts.get(2));
    long timestamp = Long.parseLong(parts.get(3));
    TestVariant variant = parseVariant(parts.get(4));
    boolean failed = "1".equals(parts.get(6));
    boolean skipped = "1".equals(parts.get(7));
    String runUrl = parts.size() > 8 ? parts.get(8) : null;

    BuildId buildId = WorkflowId.of(workflow).job(branch).build(buildNumber);
    return new RunDetails(
        buildId, variant, runUrl, timestamp, failed, skipped, TestRunOutput.EMPTY_OUTPUT);
  }

  TestVariant parseVariant(String variant) {
    return variant.isEmpty() ? TestVariant.DEFAULT : TestVariant.fromString(variant);
  }
}
