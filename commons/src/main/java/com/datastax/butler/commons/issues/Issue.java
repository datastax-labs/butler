/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.TextStringBuilder;

/**
 * Simplest possible issue tracker Issue.
 *
 * <p>This class can be inherited to describe issues for various issue tracking systems such as JIRA
 * or GithubIssues. For the purpose of butler processing we can assume that each issue has a key,
 * summary and description.
 *
 * <p>Butler also needs to know if the issue is closed or open.
 */
public abstract class Issue {

  final IssueId id;
  String title;
  String body;
  final Set<String> labels = new HashSet<>();

  /** Construct basic Issue object with key, symmary and description. */
  public Issue(IssueId id, String title, String body) {
    this.id = id;
    this.title = title;
    this.body = body;
  }

  public IssueId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String body() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public void addLabel(String label) {
    labels.add(label);
  }

  public Set<String> labels() {
    return labels;
  }

  /** Provide detailed string information about the issue. */
  public Map<String, String> details() {
    Map<String, String> res = new HashMap<>();
    res.put("id", id == null ? null : id.toString());
    res.put("title", title);
    res.put("body", body);
    res.put("labels", StringUtils.joinWith(", ", labels));
    return res;
  }

  /**
   * Build string will all the issue details.
   *
   * <p>This method is used by butler to present information about issue int the UI so it can be
   * overriden by default issue types e.g. with additional fields such as priority etc.
   */
  public String toDetailedString() {
    var sb = new TextStringBuilder();
    if (id != null) {
      // id can be null for an issue that is not fetched but fresh created.
      sb.append("id: ").append(id.toString()).appendNewLine();
    }
    sb.append("title: ").append(title).appendNewLine();
    sb.appendNewLine();
    sb.append(body);
    if (!labels.isEmpty()) {
      sb.appendNewLine();
      sb.append("labels: ").append(StringUtils.joinWith(", ", labels));
    }
    return sb.toString();
  }
}
