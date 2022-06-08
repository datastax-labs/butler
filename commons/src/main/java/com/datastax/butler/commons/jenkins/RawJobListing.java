/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.datastax.butler.commons.dev.Branch;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;

public class RawJobListing {
  private static final JsonConverter<RawJobListing> parser =
      new JsonConverter<RawJobListing>(RawJobListing.class, "Jenkins workflow list");

  private final List<RawJob> jobs;

  private RawJobListing(List<RawJob> jobs) {
    this.jobs = jobs;
  }

  static RawJobListing parse(String json) {
    return parser.parse(json);
  }

  static HttpUrl.Builder getUrl(HttpUrl.Builder workflowUrl) {
    return workflowUrl
        .addPathSegments("api/json")
        .addEncodedQueryParameter("tree", "jobs[name,color]");
  }

  List<JobId> get(WorkflowId workflow) {
    return get(workflow, Optional.empty());
  }

  List<JobId> get(WorkflowId workflow, Optional<String> onlyColor) {
    return jobs.stream()
        .filter(j -> onlyColor.isEmpty() || onlyColor.get().equals(j.color))
        .map(j -> workflow.job(j.name))
        .collect(Collectors.toUnmodifiableList());
  }

  private static class RawJob {
    private final Branch name;
    private final String color;

    private RawJob(Branch name, String color) {
      this.name = name;
      this.color = color;
    }
  }
}
