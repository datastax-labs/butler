/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.ci;

import lombok.NonNull;
import lombok.Value;

@Value
public class JenkinsBuildLoadRequest {
  @NonNull String url;
  @NonNull String workflow;
  @NonNull String branch;
  int buildNumber;
}
