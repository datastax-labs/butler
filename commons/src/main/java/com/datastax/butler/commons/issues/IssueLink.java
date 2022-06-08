/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * A link to a Jira Issue.
 *
 * <p>This object is mostly a minor convenience, but in practice mostly exists for the sake of the
 * Butler API, as a way to ship to clients a ticket with it's url (so clients don't have to
 * reconstruct urls).
 *
 * <p>Note that UI expects "name" to be present in the json serialization.
 */
@Value
@NonFinal
public class IssueLink {

  @JsonIgnore IssueId id;
  @JsonProperty URL url;
  @JsonProperty Boolean closed;

  /** Construct a link with name url and no information if the issue is closed. */
  public IssueLink(IssueId id, URL url) {
    this.id = id;
    this.url = url;
    this.closed = null;
  }

  /** Construct a link with name url and information if the issue is closed. */
  public IssueLink(IssueId id, URL url, Boolean closed) {
    this.id = id;
    this.url = url;
    this.closed = closed;
  }

  public static IssueLink withClosed(IssueLink of, Boolean closed) {
    return new IssueLink(of.id, of.url, closed);
  }

  @JsonProperty("name")
  public String name() {
    return toString();
  }

  @Override
  public String toString() {
    return id.toString();
  }
}
