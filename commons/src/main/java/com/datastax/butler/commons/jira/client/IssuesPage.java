/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import java.util.List;

/** Represents a page of results when searching for JIRA issues. */
class IssuesPage {
  final int startAt;
  final int maxResults;
  final int total;
  final List<Item> issues;

  IssuesPage(int startAt, int maxResults, int total, List<Item> issues) {
    this.startAt = startAt;
    this.maxResults = maxResults;
    this.total = total;
    this.issues = issues;
  }

  static class Item {
    String id;
    String key;
  }
}
