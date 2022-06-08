/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import java.util.List;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;

public class RawBuildNumberListing {
  private static final JsonConverter<RawBuildNumberListing> parser =
      new JsonConverter<RawBuildNumberListing>(
          RawBuildNumberListing.class, "Jenkins build number list");

  private final List<RawBuildNumber> builds;

  private RawBuildNumberListing(List<RawBuildNumber> builds) {
    this.builds = builds;
  }

  static RawBuildNumberListing parse(String json) {
    return parser.parse(json);
  }

  static HttpUrl.Builder getUrl(HttpUrl.Builder jobUrl) {
    return jobUrl.addPathSegments("api/json").addEncodedQueryParameter("tree", "builds[number]");
  }

  List<BuildId> get(JobId jobId) {
    return builds.stream().map(j -> jobId.build(j.number)).collect(Collectors.toUnmodifiableList());
  }

  private static class RawBuildNumber {
    private final int number;

    private RawBuildNumber(int number) {
      this.number = number;
    }
  }
}
