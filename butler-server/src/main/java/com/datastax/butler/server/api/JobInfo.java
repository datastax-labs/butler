/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.jenkins.JobId;
import lombok.Value;

/** JobInfo is send from api to views when listing jobs via known_jobs. */
@Value
public class JobInfo {
  String workflow;
  Branch jobName;
  JobId.Category category;
}
