/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.prgate;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/** A Story of test runs from recent to oldest: PFPPF SPF. */
public class TestRunsStory {

  private String story;

  public TestRunsStory(String story) {
    this.story = story;
  }

  public String story() {
    return story;
  }

  public Optional<Boolean> alwaysPassing() {
    return numRuns() > 0 ? Optional.of(numRuns() == numPassed()) : Optional.empty();
  }

  public Optional<Boolean> alwaysFailing() {
    return numRuns() > 0 ? Optional.of(numRuns() == numFailed()) : Optional.empty();
  }

  @Override
  public String toString() {
    return story.replace("P", "+");
  }

  public boolean hasFailures() {
    return numFailed() > 0;
  }

  public int numRuns() {
    return story.length();
  }

  public int numFailed() {
    return StringUtils.countMatches(story, 'F');
  }

  public int numPassed() {
    return StringUtils.countMatches(story, 'P');
  }

  /** Only "run" results, no skipped no missing runs. */
  public String results() {
    return story.replaceAll("[S ]+", "");
  }

  public boolean noResults() {
    return results().length() == 0;
  }

  /** Return up to 4 latests results. */
  public TestRunsStory head() {
    return new TestRunsStory(StringUtils.left(story, 4));
  }

  /** Return anything that is older than 4 latest results. */
  public TestRunsStory tail() {
    return new TestRunsStory(StringUtils.substring(story, 4));
  }
}
