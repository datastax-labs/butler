/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.issues;

import static java.lang.String.format;

import com.datastax.butler.commons.dev.BranchVersion;
import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.issues.content.Markdown;
import com.datastax.butler.commons.jenkins.TestName;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/** Builder for issue title and body. */
public class IssueContent {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM);

  private final Markdown markdown;
  private final List<String> paragraphs = new ArrayList<>();

  public IssueContent(Markdown markdown) {
    this.markdown = markdown;
  }

  /**
   * Build jira summary line from given test names. List is limited so that it does not become too
   * long.
   */
  public String title(List<TestName> names) {
    if (names.size() == 1) {
      TestName name = names.get(0);
      return format("%s test failure: %s", name.category(), name.fullName());
    } else {
      List<String> classes =
          names.stream().map(TestName::className).distinct().collect(Collectors.toList());
      int displayCount = Math.min(2, classes.size());
      int remaining = classes.size() - displayCount;

      String classNames = String.join(", ", classes.subList(0, displayCount));
      String summary = format("%d test failures in %s", names.size(), classNames);
      if (remaining > 0) {
        summary += format(" (and %d more)", remaining);
      }
      return summary;
    }
  }

  /** Builds description for given test and failure details. */
  public String paragraphForFailure(TestName name, TestFailure failureDetails) {
    var nowStr = LocalDateTime.now(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);

    List<String> lines = new ArrayList<>();
    lines.add(format("The %s '%s' has been failing on CI.", name.category(), name.fullName()));
    lines.add(format("Last failures per-version as of this ticket creation (%s):", nowStr));
    lines.addAll(listOfBranchFailures(name, failureDetails));

    var lastOutput = failureDetails.lastFailedOutput();
    if (lastOutput.isPresent()) {
      var out = lastOutput.get();
      noFormatSection("error details", out.errorDetails()).ifPresent(lines::add);
      noFormatSection("stack trace", out.errorStackTrace()).ifPresent(lines::add);
      noFormatSection("stdout", out.stdout()).ifPresent(lines::add);
      noFormatSection("stderr", out.stderr()).ifPresent(lines::add);
    }

    return StringUtils.join(lines, "\n");
  }

  public void addParagraph(String paragraph) {
    paragraphs.add(paragraph);
  }

  /** Build string with non-blank paragraphs separated by newlines. */
  public String body() {
    return paragraphs.stream()
        .filter(p -> !p.isBlank())
        .collect(Collectors.joining(format("%n%s%n", markdown.separator())));
  }

  private List<String> listOfBranchFailures(TestName name, TestFailure failureDetails) {
    List<String> items = new ArrayList<>();
    var lastByVersions = failureDetails.failureDetails().lastByVersions();
    for (Map.Entry<BranchVersion, RunDetails> entry : lastByVersions.entrySet()) {
      BranchVersion version = entry.getKey();
      RunDetails data = entry.getValue();
      String failureLink =
          markdown.link(sanitizedString(name.testId(data.variant())), sanitizedString(data.url()));
      items.add(format("%s: %s (on %s)", version, failureLink, timestampString(data.timestamp())));
    }
    return markdown.unorderedListOf(items);
  }

  /** Creates a {noformat} section with title and content if content is not blank. */
  private Optional<String> noFormatSection(String title, String content) {
    if (!StringUtils.isBlank(content)) {
      return Optional.of(format("%s%n%s", markdown.title(title), markdown.noFormat(content)));
    } else {
      return Optional.empty();
    }
  }

  private static String timestampString(long tstamp) {
    var timeZoneID = ZoneId.systemDefault();
    var localTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(tstamp), timeZoneID);
    return localTime.format(DATE_TIME_FORMATTER);
  }

  /** Created toString() for object sanitized so that it will not break jira format rules. */
  public static String sanitizedString(Object obj) {
    if (obj == null) return null;
    return obj.toString().replaceAll("([\\[\\]])", "\\\\$1");
  }
}
