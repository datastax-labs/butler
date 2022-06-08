/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.gate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

@Value
public class JenkinsBuildApprovalRequest {
  @NonNull String url; // build URL

  /**
   * workflow name under which it should be imported into database workflow from jenkins can take
   * form such as pr-gate/PR-123 for multijob so it is {pipeline}/{job} where butler should use only
   * {pipeline} thus we make workflow() access NONE to enforce use of pipeline();.
   */
  @NonNull
  @Getter(AccessLevel.NONE)
  String workflow;

  @NonNull String branch; // branch of the build
  int buildNumber; // build number
  @NonNull String upstreamWorkflow; // which workflow compare to
  @NonNull String upstreamBranch; // which branch in the given workflow compare to

  /**
   * Return only pipeline from given workflow, which can be multibranch. We can get
   * project-pr-gate/PRJ-13 from jenkins approve request, and this this case we need to return only
   * the pipeline part so that butler can use if as workflow name.
   */
  @SuppressWarnings("StringSplitter")
  public String pipeline() {
    return workflow.split("/")[0];
  }
}
