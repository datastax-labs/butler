/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.server.tools.StreamUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Handles access to mapping between tests and issues (gh, jira). */
@Repository
public class TestLinkedIssuesDb extends DbTableService {

  private static final String TABLE_NAME = "test_linked_issues";

  private final TestNamesDb testNamesDb;
  private final TableMapper<StoredTestLinkedIssue, Long> mapper;

  /** Creates the repository (Auto-wired by Spring). */
  @Autowired
  public TestLinkedIssuesDb(NamedParameterJdbcTemplate template, TestNamesDb testNamesDb) {
    super(template, TABLE_NAME);
    this.testNamesDb = testNamesDb;
    this.mapper = tableMapper(StoredTestLinkedIssue.class, Long.class);
  }

  /**
   * List of all known (linked to at least one test) issues (gh,jira).
   *
   * @return set of issue ids.
   */
  public Set<IssueId> allKnownIssues() {
    return template
        .queryForList(
            q("SELECT DISTINCT linked_issue FROM %s", TABLE_NAME),
            Collections.emptyMap(),
            String.class)
        .stream()
        .map(IssueId::fromString)
        .collect(Collectors.toSet());
  }

  /**
   * Return list of unique issues linked to given test.
   *
   * <p>SELECT is made order by create_at so recently linked issues are returned first.
   */
  public List<StoredTestLinkedIssue> linkedIssues(long testId, int limit) {
    String whereClause = "test_id=:test_id";
    String orderBy = "created_at DESC";
    if (limit > 0) {
      orderBy += " LIMIT " + limit;
    }
    return mapper.getWhere(whereClause, Map.of("test_id", testId), orderBy).stream()
        .filter(StreamUtil.distinctByKey(StoredTestLinkedIssue::linkedIssue))
        .collect(Collectors.toUnmodifiableList());
  }

  public List<StoredTestLinkedIssue> linkedIssues(TestName testName) {
    return linkedIssues(testNamesDb.dbId(testName), -1);
  }

  public Optional<StoredTestLinkedIssue> recentLinkedIssue(TestName testName) {
    return recentLinkedIssue(testNamesDb.dbId(testName));
  }

  public Optional<StoredTestLinkedIssue> recentLinkedIssue(long testId) {
    return linkedIssues(testId, 1).stream().findFirst();
  }

  public void linkIssueToTest(TestName testName, IssueId issueId) {
    logger.info("Creating link between test {} and issue {}", testName, issueId);
    final long testId = testNamesDb.dbId(testName);
    final StoredTestLinkedIssue link = StoredTestLinkedIssue.link(testId, issueId.id());
    mapper.insert(link);
  }
}
