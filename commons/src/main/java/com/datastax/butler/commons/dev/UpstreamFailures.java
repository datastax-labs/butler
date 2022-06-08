/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.TestName;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Value;

@Value
public class UpstreamFailures {
  List<TestFailure> failures;

  /**
   * Return list of Failures that has failed runs.
   *
   * <p>Note that if building comparison between branch and upstream, it is possible to get failures
   * that have only failed runs on the upstream branch.
   */
  public List<TestFailure> failed() {
    return failures.stream().filter(TestFailure::hasFailed).collect(Collectors.toList());
  }

  /**
   * Find failures for given test.
   *
   * @return hopefully one or zero, but I am not sure about variants.
   */
  public List<TestFailure> findTest(TestName testName) {
    return failures.stream().filter(x -> x.test().equals(testName)).collect(Collectors.toList());
  }

  /** Return UpstreamFailures filtered to builds older than given one. */
  public UpstreamFailures beforeBuild(int buildNum) {
    var selected =
        this.failures.stream().map(x -> x.beforeBuild(buildNum)).collect(Collectors.toList());
    return new UpstreamFailures(selected);
  }

  /** Number of builds in the failure history. */
  public long numBuilds() {
    return failures.stream()
        .map(TestFailure::buildNumbers)
        .flatMap(Collection::stream)
        .distinct()
        .count();
  }
}
