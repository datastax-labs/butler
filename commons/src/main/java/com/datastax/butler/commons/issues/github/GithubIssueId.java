/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.github;

import com.datastax.butler.commons.issues.IssueId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Github Issue Id that consists of repo name and #. Good example would be apache/cassandra#2123 */
public class GithubIssueId extends IssueId {

  private static final Pattern ID_PATTERN =
      Pattern.compile("([\\w-.]{1,128})/([\\w-.]{1,128})#(\\d+)");

  private String repoName;
  private int issueNumber;

  public GithubIssueId(String id) {
    super(id);
    parse(id);
  }

  private void parse(String id) {
    Matcher m = ID_PATTERN.matcher(id);
    if (m.find()) {
      repoName = m.group(1) + "/" + m.group(2);
      issueNumber = Integer.parseInt(m.group(3));
    } else {
      throw new IllegalArgumentException(
          "Github issue id " + id + " does not match " + ID_PATTERN.pattern());
    }
  }

  @Override
  public String toString() {
    return "#" + issueNumber;
  }

  @Override
  public String projectName() {
    return repoName;
  }
}
