/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import com.squareup.moshi.Json;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Holds the known fields of a JIRA issue object
 *
 * <p>The layout here (and field naming when no {@link Json} annotation is provided) matches what
 * JIRA expects in its JSON.
 *
 * <p>Note that the fields marked {@link Nullable} are those that may come back {@code null} when
 * fetching issue, but give how the code works, all field can effectively be {@code null} (if we
 * ever use fancy analysis around {@link Nullable} and this become a problem, we can just turn those
 * into comments, but that's not a problem right now).
 */
public class JiraIssueFields {

  String summary;
  @Nullable String description;

  Named issuetype;
  Named resolution;
  Named status;

  @Json(name = "project.key")
  String project;

  @Nullable JiraUser assignee;
  @Nullable JiraUser reporter;

  @Nullable Instant created;

  @Nullable List<Named> components;

  @Nullable List<String> labels;

  @Json(name = "versions")
  @Nullable
  List<Named> affectedVersion;

  @Nullable List<Named> fixVersions;

  public String resolution() {
    return resolution != null ? resolution.name : null;
  }

  public String status() {
    return status != null ? status.name : null;
  }

  public Set<String> fixVersions() {
    if (fixVersions != null)
      return fixVersions.stream().map(Named::toString).collect(Collectors.toSet());
    else return Collections.emptySet();
  }

  /**
   * Multiple fields in jira objects are packed with different attributes of which only "name" is
   * really interesting for butler parsing jira json parse.
   */
  static class Named {
    String name;

    Named(String name) {
      this.name = name;
    }

    public static Named fromName(String name) {
      return new Named(name);
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Named)) {
        return false;
      }
      Named that = (Named) o;
      return this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
