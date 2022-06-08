/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.content;

import static java.lang.String.format;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of JIRA style markdown.
 * https://jira.atlassian.com/secure/WikiRendererHelpAction.jspa?section=all
 */
public class JiraMarkdown implements Markdown {

  private static final String NEW_PARAGRAPH = format("%n");

  @Override
  public String noFormat(String text) {
    return format("{noformat}%n%s%n{noformat}", text);
  }

  @Override
  public String title(String title) {
    return format("*%s*", title);
  }

  @Override
  public String newParagraph() {
    return NEW_PARAGRAPH;
  }

  @Override
  public List<String> unorderedListOf(List<String> items) {
    return items.stream().map(i -> format("* %s", i)).collect(Collectors.toList());
  }

  @Override
  public String link(String text, String url) {
    return format("[%s|%s]", text, url);
  }

  @Override
  public String separator() {
    return "----";
  }
}
