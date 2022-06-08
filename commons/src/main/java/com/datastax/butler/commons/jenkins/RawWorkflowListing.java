/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import com.squareup.moshi.Json;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;

public class RawWorkflowListing {
  private static final JsonConverter<RawWorkflowListing> parser =
      new JsonConverter<RawWorkflowListing>(RawWorkflowListing.class, "Jenkins workflow list");

  // Note: this does have to be called "jobs" because that's what is in the Jenkins Json
  private final List<RawWorkflow> jobs;

  private RawWorkflowListing(List<RawWorkflow> workflows) {
    this.jobs = workflows;
  }

  static RawWorkflowListing parse(String json) {
    return parser.parse(json);
  }

  static RawWorkflowListing empty() {
    return new RawWorkflowListing(Collections.emptyList());
  }

  static HttpUrl.Builder getUrl(HttpUrl.Builder baseUrl) {
    return baseUrl.addPathSegments("api/json").addEncodedQueryParameter("tree", "jobs[name]");
  }

  Optional<Boolean> isMultiBranch(String name) {
    return jobs.stream()
        .filter(x -> x.name.equals(name))
        .findFirst()
        .map(
            y ->
                y.klass.endsWith("WorkflowMultiBranchProject")
                    || y.klass.endsWith("MultiJobProject"));
  }

  List<WorkflowId> get() {
    return jobs.stream().map(w -> WorkflowId.of(w.name)).collect(Collectors.toUnmodifiableList());
  }

  private static class RawWorkflow {
    @Json(name = "_class")
    private final String klass;

    private final String name;

    private RawWorkflow(String klass, String name) {
      this.klass = klass;
      this.name = name;
    }
  }
}
