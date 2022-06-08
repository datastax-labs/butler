/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jira.client;

import com.datastax.butler.commons.web.Email;

/** A JIRA user. */
public class JiraUser {
  /*
   * Note that this object is generated directly through JiraJsonConverter
   * from the corresponding json object returned by the JIRA API.
   * So the field names must either match the names in said json object,
   * or an @Json(name="...") must be used to map to the proper name from JIRA.
   */

  private final String name;
  private final String displayName;
  private final Email emailAddress;
  private final String accountId;

  private JiraUser(String name, String displayName, Email emailAddress, String accountId) {
    this.name = name;
    this.displayName = displayName;
    this.emailAddress = emailAddress;
    this.accountId = accountId;
  }

  /** The account ID for this user. */
  public String accountId() {
    return accountId;
  }

  /** The "display" name of this user. */
  public String displayName() {
    return displayName;
  }

  /** The email of this user. */
  public Email email() {
    return emailAddress;
  }

  /** The account name of this user. */
  public String accountName() {
    return name;
  }

  /**
   * Returns whether the user "matches" <b>all</b> the provided search terms.
   *
   * <p>A user "matches" a term if any of its display name, name or email contains the term, and
   * this in a case insensitive way.
   *
   * @param searchTerms the terms to search.
   * @return {@code true} if the user "matches" all the terms from {@code searchTerms}, {@code
   *     false} otherwise.
   */
  public boolean matches(String... searchTerms) {
    for (String term : searchTerms) {
      if (!name.toLowerCase().contains(term)
          && !displayName.toLowerCase().contains(term)
          && !emailAddress.toString().toLowerCase().contains(term)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
