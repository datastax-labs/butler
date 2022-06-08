/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.upstream;

import com.datastax.butler.api.commons.ChartData;
import com.datastax.butler.commons.dev.BranchVersion;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;

@Value
public class UpstreamTrends {
  List<WorkflowVersionData> data;

  public static UpstreamTrends build(List<WorkflowVersionData> trendData) {
    var comp = new WorkflowVersionDataComparator();
    return new UpstreamTrends(trendData.stream().sorted(comp).collect(Collectors.toList()));
  }

  @Value
  public static class WorkflowVersionData {
    BranchVersion version;
    String workflow;
    Data data;
  }

  private static class WorkflowVersionDataComparator implements Comparator<WorkflowVersionData> {
    @Override
    public int compare(WorkflowVersionData a, WorkflowVersionData b) {
      var versionCompare = a.version.compareTo(b.version);
      if (versionCompare != 0) return versionCompare;
      return a.workflow.compareTo(b.workflow);
    }
  }

  @Value
  public static class Data {
    ChartData testFailures; // data for plotting tests failed per build
    ChartData testRuns; // data for plotting tests run per build
    ChartData duration; // data for plotting duration

    long numBuilds;
    long numBuildsFailed;
    long numBuildsBroken;
    long numRecent;
    long numRecentFailed;
    long numRecentBroken;
    double avgNumFailuresPerBuild;
    double avgBuildDurationInMin;
    double avgRecentDurationInMin;
    double p90BuildDurationInMin;
    double p90RecentDurationInMin;
  }
}
