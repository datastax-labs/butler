/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues.content;

import java.util.List;

/**
 * Markdown implementation allowing to place formated text into issue content. Should be implemented
 * for each issue tracking system such as jira or gh issues.
 */
public interface Markdown {

  /** Create section without formatting. */
  String noFormat(String text);

  /** Create title-like text. */
  String title(String title);

  /** Start new paragraph. */
  String newParagraph();

  /** Unordered list. */
  List<String> unorderedListOf(List<String> items);

  /** Link. */
  String link(String text, String url);

  /** Create a horizontal separator e.g. horizontal ruler. */
  String separator();
}
