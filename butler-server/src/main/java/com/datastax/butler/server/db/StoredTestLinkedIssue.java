/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Stored link between TEST (identified by test_id) and ISSUE (e.g. jira) It is allowed to have same
 * issue linked multiple times as what is important is the order - recently linked one will be
 * visible. So it may be needed to link some older issue again.
 */
@Value
@AllArgsConstructor
public class StoredTestLinkedIssue {
  long id; // auto-increment key
  long testId;
  String linkedIssue;
  Instant createdAt;

  public static StoredTestLinkedIssue link(long testId, String linkedIssue) {
    return new StoredTestLinkedIssue(0, testId, linkedIssue, Instant.now());
  }
}
