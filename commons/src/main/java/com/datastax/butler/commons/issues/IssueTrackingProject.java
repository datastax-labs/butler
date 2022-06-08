/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues;

import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.issues.content.Markdown;
import com.datastax.butler.commons.jenkins.TestName;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Issue Tracking Project such as jira project or gh issues project. */
public interface IssueTrackingProject {

  /** Project name uniquely identifying project e.g. jira/XYZ or butler/ghi */
  String projectName();

  /**
   * Create a link for issue with given identifier.
   *
   * @param issueId issue id. e.g. XYZ-12 (jira) or "123" (gh issues)
   * @return IssueLink object potentially with status being set.
   */
  IssueLink getLink(IssueId issueId);

  /**
   * Fetches issue content from issue tracking system. There is no guarantee if all the fields are
   * fetched, but at least title and body should be.
   */
  Issue fetchIssue(IssueId issueId);

  /** Return true if issue is closed, false if still open. Empty if we do not know. */
  Optional<Boolean> isClosed(IssueId issueId);

  /**
   * Creates empty issue to be filled by services before creating.
   *
   * <p>Issue should be created with empty title and body but can have other project-specific fields
   * set.
   */
  Issue newIssue();

  /**
   * Create new issue in the issue tracking system.
   *
   * @param issue issue content
   * @return IssueLink of the newly created issue.
   */
  IssueLink createIssue(Issue issue, Collection<TestFailure> failures);

  /**
   * Search for issues that looks like related to the given test name.
   *
   * @param test test name
   * @param onlyOpen if true should only return "open" issues
   * @return set of issue links
   */
  Set<IssueLink> searchIssuesForTest(TestName test, boolean onlyOpen);

  /** Markdown that should be used for building issue content. */
  Markdown markdown();
}
