/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static java.lang.String.format;

import com.datastax.butler.commons.dev.Branch;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;

public class RawBuild {
  private static final JsonConverter<RawBuild> parser =
      new JsonConverter<RawBuild>(RawBuild.class, "Jenkins build");

  private final @Nullable Long duration;
  private final @Nullable Long estimatedDuration;
  private final @Nullable String result;
  private final boolean building;
  private final long timestamp;
  private final List<Map<String, Object>> actions;

  private RawBuild(
      @Nullable Long duration,
      @Nullable Long estimatedDuration,
      @Nullable String result,
      long timestamp,
      boolean building,
      List<Map<String, Object>> actions) {
    this.duration = duration;
    this.estimatedDuration = estimatedDuration;
    this.result = result;
    this.building = building;
    this.timestamp = timestamp;
    this.actions = actions;
  }

  static RawBuild parse(String json) {
    return parser.parse(json);
  }

  static HttpUrl.Builder getUrl(HttpUrl.Builder buildUrl) {
    String topLevelFields = "result,building,timestamp,duration,estimatedDuration";
    String resultCountsFields = "failCount,skipCount,totalCount";
    String buildDataFields = "remoteUrls,lastBuiltRevision[branch[SHA1,name]]";
    String tree = format("%s,actions[%s,%s]", topLevelFields, resultCountsFields, buildDataFields);
    return buildUrl.addPathSegments("api/json").addEncodedQueryParameter("tree", tree);
  }

  JenkinsBuild toBuild(JenkinsWorkflow jenkinsWorkflow, BuildId buildId, HttpUrl buildUrl) {
    // Note that the "result" is not super meaningful while we're "building" so we ignore it and
    // call it "RUNNING" until it's finished building.
    JenkinsBuild.Status status =
        building || result == null
            ? JenkinsBuild.Status.RUNNING
            : JenkinsBuild.Status.valueOf(result);
    Instant startTime = Instant.ofEpochMilli(timestamp);

    TestReport.Summary summary = null;
    ImmutableMap.Builder<String, JenkinsBuild.BuildData> buildData = ImmutableMap.builder();
    // Parse the "actions". We fetch only 2 sub-group of actions currenty:
    // - the result summary on the one hand
    // - the branch and SHA1 for all the projects involved in the build
    for (Map<String, Object> item : actions) {
      if (item.containsKey("totalCount")) {
        summary = parseSummary(item);
      } else if (item.containsKey("lastBuildRevision")) {
        JenkinsBuild.BuildData data = parseBuildData(item);
        buildData.put(data.name(), data);
      }
    }

    return new JenkinsBuild(
        jenkinsWorkflow,
        buildId,
        buildUrl,
        status,
        startTime,
        duration(duration),
        duration(estimatedDuration),
        summary,
        buildData.build());
  }

  private @Nullable Duration duration(@Nullable Long field) {
    return field == null ? null : Duration.ofMillis(field);
  }

  private static TestReport.Summary parseSummary(Map<String, Object> item) {
    return TestReport.Summary.fromTotalCount(
        getLong(item, "failCount"), getLong(item, "skipCount"), getLong(item, "totalCount"));
  }

  private static long getLong(Map<String, Object> item, String name) {
    Object value = item.get(name);
    if (value == null) {
      throw new AssertionError("Unexpected missing value for" + name);
    }
    if (!(value instanceof Number)) {
      throw new AssertionError(format("Unexpected non-number value %s for %s", value, name));
    }
    return ((Number) value).longValue();
  }

  @SuppressWarnings("unchecked")
  private static JenkinsBuild.BuildData parseBuildData(Map<String, Object> item) {
    // First, the remoteUrls list has the link(s) to the git repo used
    List<String> remoteUrls = get(item, "remoteUrls", List.class);
    assert !remoteUrls.isEmpty() : "Missing remote url for " + item;
    // The url should be of the form https://github.com/organization/project.git.
    // We take the last 2 parts for the org and repository, and strip any ".git".
    String url = remoteUrls.get(0);
    List<String> splits = Splitter.on("/").omitEmptyStrings().splitToList(url);
    assert splits.size() >= 2 : "Weird remote url found " + url + " in " + item;
    String name = splits.get(splits.size() - 1);
    if (name.endsWith(".git")) {
      name = name.substring(0, name.length() - 4);
    }
    String repo = splits.get(splits.size() - 2);

    // Then we read into lastBuildRevision to get both the branch name and SHA1
    Map<String, Map<String, String>> rev = get(item, "lastBuiltRevision", Map.class);
    Map<String, String> branchMap = rev.get("branch");
    String sha1 = branchMap.get("SHA1");
    String branchName = branchMap.get("name");

    Branch branch = Branch.fromString(branchName);

    return new JenkinsBuild.BuildData(name, branch, repo, sha1);
  }

  private static <T> T get(Map<String, Object> item, String name, Class<T> klass) {
    Object value = item.get(name);
    if (value == null) {
      throw new AssertionError("Unexpected missing value for" + name);
    }
    if (!klass.isInstance(value)) {
      throw new AssertionError(
          format(
              "Unexpected class %s for value %s of %s (expected class %s)",
              value.getClass(), value, name, klass));
    }
    return klass.cast(value);
  }
}
