/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
public class FailureDetails {

  List<RunDetails> allRuns;
  // Note that the keys are String, not TestVariant, because in practice, we massage the variant
  // a bit for upgrade test to get something more readable/useful
  Map<String, RunDetails> lastByVariants;
  Map<BranchVersion, RunDetails> lastByVersions;

  Map<String, List<RunDetails>> allByVariants;
  Map<BranchVersion, List<RunDetails>> allByVersions;

  Map<BranchVersion, RunDetails> lastFailedByVersion;

  RunDetails last;
  RunDetails oldest;
  RunDetails lastFailed;

  long failures;
  long lastWeekFailures;
  long lastMonthFailures;

  /** Builds FailureDetails object from given history of runs. */
  public static FailureDetails build(List<RunDetails> runs) {
    Map<String, RunDetails> byVariant = new HashMap<>();
    Map<BranchVersion, RunDetails> byVersion = new HashMap<>();
    Map<String, List<RunDetails>> allByVariant = new HashMap<>();
    Map<BranchVersion, List<RunDetails>> allByVersion = new HashMap<>();
    Map<BranchVersion, RunDetails> lastFailedByVersion = new HashMap<>();
    RunDetails last = null;
    RunDetails oldest = null;
    RunDetails lastFailed = null;
    long failedCount = 0;
    long lastWeekFailedCount = 0;
    long lastMonthFailedCount = 0;
    long lastWeekTimestamp = daysAgoTimestampSeconds(7);
    long lastMonthTimestamp = daysAgoTimestampSeconds(30);

    for (RunDetails data : runs) {
      if (data == null) {
        continue;
      }

      BranchVersion version = data.extractVersion().orElse(BranchVersion.fromString("main"));
      String variant = data.variant().toString();
      byVariant.compute(variant, (v, d) -> RunDetails.max(d, data));
      byVersion.compute(version, (v, d) -> RunDetails.max(d, data));
      if (data.failed()) {
        // calculate total statistics
        failedCount += 1;
        lastWeekFailedCount += (data.timestamp() > lastWeekTimestamp) ? 1 : 0;
        lastMonthFailedCount += (data.timestamp() > lastMonthTimestamp) ? 1 : 0;
        // calculate last failed run
        lastFailedByVersion.compute(version, (v, d) -> RunDetails.max(d, data));
        if (lastFailed != null) {
          // clear output so that we will not send it over network
          // and not analyze in the view layer, only recent one should be preserved
          RunDetails.min(lastFailed, data).clearOutput();
        }
        lastFailed = RunDetails.max(lastFailed, data);
      } else {
        // no need to send output for passed runs
        data.clearOutput();
      }
      allByVariant.computeIfAbsent(variant, k -> new ArrayList<RunDetails>()).add(data);
      allByVersion.computeIfAbsent(version, k -> new ArrayList<RunDetails>()).add(data);
      last = RunDetails.max(last, data);
      oldest = RunDetails.min(last, data);
    }

    // all runs should be sorted from newest to oldest
    var sortedRuns = new ArrayList<>(runs);
    sortedRuns.sort(Comparator.comparing(RunDetails::timestamp).reversed());

    return new FailureDetails(
        sortedRuns,
        byVariant,
        byVersion,
        allByVariant,
        allByVersion,
        lastFailedByVersion,
        last,
        oldest,
        lastFailed,
        failedCount,
        lastWeekFailedCount,
        lastMonthFailedCount);
  }

  public long lastWeekRunsCount() {
    long weekStart = daysAgoTimestampSeconds(7);
    return allRuns.stream().filter(x -> x.timestamp() > weekStart).count();
  }

  public long lastMonthRunsCount() {
    long monthStart = daysAgoTimestampSeconds(30);
    return allRuns.stream().filter(x -> x.timestamp() > monthStart).count();
  }

  /** Remove stdout/stderr/stacktrace/error from all the test run details. */
  public void clearTestRunsOutput() {
    // RunDetails objects are shared so cleaning up allRuns is enough.
    allRuns.forEach(RunDetails::clearOutput);
  }

  private static long daysAgoTimestampSeconds(int daysAgo) {
    return Instant.now().minus(daysAgo, ChronoUnit.DAYS).getEpochSecond();
  }
}
