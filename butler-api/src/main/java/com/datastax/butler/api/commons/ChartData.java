/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.api.commons;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/** A list of points that composes a (line) chart. */
@Value
@Builder
@JsonDeserialize(builder = ChartData.ChartDataBuilder.class)
public class ChartData {
  @NonNull @Singular List<Point> points;

  /** A x,y point in a chart. */
  @Value
  public static class Point {
    /** The x coordinate of the point. */
    long x;
    /** The y coordinate of the point. */
    long y;
    /** Extra data associated to the point. */
    String extra;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ChartDataBuilder {}
}
