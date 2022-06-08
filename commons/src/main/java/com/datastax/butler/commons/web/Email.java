/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

/**
 * An email address.
 *
 * <p>This class exists first and foremost to preserve the information tha twe deal with an email
 * address when we do. At this point, however, it does not do any validation nor parse the
 * underlying string in any way, so getting an {@link Email} object does not guarantee this isn't a
 * random string. There is also no methods to access the specific parts of the email address. Of
 * course, all of the above might be improved if the need arises.
 */
public class Email {
  private final String emailAddress;

  Email(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public static Email fromString(String emailAddress) {
    return new Email(emailAddress);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Email)) {
      return false;
    }
    return emailAddress.equals(((Email) obj).emailAddress);
  }

  @Override
  public int hashCode() {
    return emailAddress.hashCode();
  }

  @Override
  public String toString() {
    return emailAddress;
  }
}
