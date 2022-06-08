/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.gate;

import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import lombok.Value;

/** The result of the request to load a single CI build. */
@Value
public class JenkinsBuildApprovalResponse {

  boolean approval;
  @NonNull String summary;
  String butlerLink;
  List<String> details;

  public static JenkinsBuildApprovalResponse approved(String msg, String butlerLink) {
    return new JenkinsBuildApprovalResponse(true, msg, butlerLink, Collections.emptyList());
  }

  public static JenkinsBuildApprovalResponse rejected(String msg, String butlerLink) {
    return new JenkinsBuildApprovalResponse(false, msg, butlerLink, Collections.emptyList());
  }

  public static JenkinsBuildApprovalResponse rejected(
      String msg, String butlerLink, List<String> details) {
    return new JenkinsBuildApprovalResponse(false, msg, butlerLink, details);
  }
}
