/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This is a crappy little query builder that's _very specific_ the huge unified upstream failures
 * query.
 */
public class UpstreamFailuresSummaryQuery {
  private static final String GROUP_CONCAT_FORMAT =
      "GROUP_CONCAT(IF(%s,"
          + "  CONCAT_WS('|',"
          + "    j.workflow,"
          + "    j.job_name,"
          + "    b.build_number,"
          + "    UNIX_TIMESTAMP(b.start_time),"
          + "    r.variant,"
          + "    r.run_blocks,"
          + "    r.failed,"
          + "    r.skipped,"
          + "    r.run_url),"
          + "  null)) AS builds"; // , limitToFailed ? "r.failed" : "true"),

  private static final List<String> SELECTS =
      Arrays.asList(
          "t.category",
          "t.path",
          "t.class_name",
          "t.test_name",
          null, // group concat placeholder
          "SUM(r.failed) AS failed_count",
          "COUNT(*) AS ran_count",
          "SUM(IF(r.failed AND b.start_time >= :aWeekAgo, 1, 0)) AS week_failed_count",
          "COUNT(IF(b.start_time >= :aWeekAgo, 1, null)) AS week_ran_count",
          "SUM(IF(r.failed AND b.start_time >= :aMonthAgo, 1, 0)) AS month_failed_count",
          "COUNT(IF(b.start_time >= :aMonthAgo, 1, null)) AS month_ran_count");

  private static final List<String> FROMS = List.of(TestRunsDb.TABLE + " r");

  private static final List<String> JOINS =
      Arrays.asList(
          String.format(
              "INNER JOIN (%s b, %s t)"
                  + "  ON (r.build_id = b.id "
                  + "      AND r.test_id = t.id ) ",
              BuildsDb.TABLE, TestNamesDb.TABLE),
          String.format("STRAIGHT_JOIN %s j ON b.job_id = j.id ", JobsDb.JOBS_TABLE));
  private static final List<String> WHERES = List.of("r.skipped = false");

  private final List<String> selects;
  private final List<String> froms;
  private final List<String> joins;
  private final List<String> wheres;

  /**
   * Builds the base query optionally limiting it only failures, or all results.
   *
   * @param limitToFailed if true, only return failed results, all if false
   */
  public UpstreamFailuresSummaryQuery(boolean limitToFailed) {

    selects = new ArrayList<>(SELECTS);
    selects.set(
        selects.indexOf(null),
        String.format(GROUP_CONCAT_FORMAT, limitToFailed ? "r.failed" : "true"));
    froms = new ArrayList<>(FROMS);
    joins = new ArrayList<>(JOINS);
    wheres = new ArrayList<>(WHERES);
  }

  public List<String> selects() {
    return Collections.unmodifiableList(selects);
  }

  public List<String> joins() {
    return Collections.unmodifiableList(joins);
  }

  @Override
  public String toString() {
    return "SELECT "
        + String.join(", ", selects)
        + " FROM "
        + String.join(", ", froms)
        + " "
        + String.join(" ", joins)
        + " WHERE "
        + String.join(" AND ", wheres)
        + " GROUP BY r.test_id";
  }

  public void add(String where, String what) {
    target(where).add(what);
  }

  public void remove(String where, String what) {
    target(where).remove(find(where, what));
  }

  public void replace(String where, String from, String too) {
    target(where).set(find(where, from), too);
  }

  private int find(String where, String what) {
    int idx = target(where).indexOf(what);
    if (idx == -1) {
      throw new IllegalArgumentException(String.format("Unable to find '%s' in %s", what, where));
    }
    return idx;
  }

  private List<String> target(String where) {
    switch (where.toLowerCase()) {
      case "select":
        return selects;
      case "from":
        return froms;
      case "join":
        return joins;
      case "where":
        return wheres;
      default:
        throw new IllegalArgumentException("Unable to target: " + where);
    }
  }
}
