/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

import static com.datastax.butler.commons.json.JsonTestUtil.assertJson;
import static com.datastax.butler.commons.json.JsonTestUtil.jField;
import static com.datastax.butler.commons.json.JsonTestUtil.jObj;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CredentialsTest {

  @Test
  void testJson() {
    Credentials credentials = new Credentials("admin", "foo");
    Object json = jObj(jField("login", "admin"), jField("password", "foo"));
    assertJson(json, credentials, Credentials.class);
  }

  @Test
  void shouldThrowOnNonExistentFile() {
    Path path = Paths.get("this-does-not-exist");
    String machineName = "jenkins.example.com";

    Exception exception =
        assertThrows(
            CredentialsException.class,
            () -> {
              Credentials.readFromNetrcFile(path, machineName);
            });

    assertNotNull(exception.getMessage());

    String expected = String.format("%s does not exist", path);
    assertTrue(exception.getMessage().contains(expected));
  }

  @Test
  void shouldThrowOnMissingMachine() {
    Path path = Paths.get("../butler-server/config/testing/netrc");
    String machineName = "non-existent-machine";

    Exception exception =
        assertThrows(
            CredentialsException.class,
            () -> {
              Credentials.readFromNetrcFile(path, machineName);
            });

    assertNotNull(exception.getMessage());

    String expected = String.format("No entry for '%s'", machineName);
    assertTrue(exception.getMessage().contains(expected));
  }
}
