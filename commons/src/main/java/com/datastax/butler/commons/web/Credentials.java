/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.web;

import static java.lang.String.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A pair of a login and a password. */
@Value
public class Credentials {
  private static final Logger logger = LogManager.getLogger();

  /** The login of the credential pair. */
  String login;
  /** The password of the credential pair. */
  String password;

  private static Path defaultNetrcFileLocation() {
    String homeDir = System.getProperty("user.home");
    return Paths.get(homeDir, ".netrc");
  }

  /**
   * Attempts to read credential from the specified {@code .netrc} file.
   *
   * <p>See <a
   * href="https://www.gnu.org/software/inetutils/manual/html_node/The-_002enetrc-file.html">the
   * "inet utils" documentation</a> for what the {@code .netrc} file is.
   *
   * @param netrcFile the path to the netrc file
   * @param machine the name of the "machine" for which to retrieve the credentials, possibly a url.
   *     If this contains the ':' character, anything from that character is stripped from the name
   *     of the machine looked up (as this typically indicate a port number).
   * @return the credential of {@code machine} read from the local machine {@code .netrc} if those
   *     can be read, throws CredentialException otherwise (the latter may mean anything from the
   *     {@code .netrc} file not existing locally, no entry being found for {@code machine}, or any
   *     other issue happening while reading the file).
   * @throws CredentialsException if an error was encountered reading the specified netrc file
   */
  public static Credentials readFromNetrcFile(Path netrcFile, String machine)
      throws CredentialsException {
    logger.debug("Attempting to read '{}' credentials from {}", machine, netrcFile);
    if (!Files.exists(netrcFile)) {
      String message = String.format("File %s does not exist", netrcFile);
      throw new CredentialsException(message);
    }

    // Strip anything after ':', as that is typically a port number
    if (machine.contains(":")) {
      machine = machine.substring(0, machine.indexOf(':'));
    }

    try (Stream<String> lines = Files.lines(netrcFile)) {
      String content = lines.collect(Collectors.joining(" "));
      String pattern = format("machine\\h+%s\\s+login\\h+(\\S+)\\s+password\\h+(\\S+)", machine);
      Matcher matcher = Pattern.compile(pattern).matcher(content);
      if (matcher.find()) {
        String login = matcher.group(1);
        logger.debug("Found credentials for login {}", login);
        return new Credentials(login, matcher.group(2));
      } else {
        String message = String.format("No entry for '%s' found in %s", machine, netrcFile);
        throw new CredentialsException(message);
      }
    } catch (IOException e) {
      String message = String.format("I/O error reading from %s: %s", netrcFile, e.getMessage());
      throw new CredentialsException(message);
    }
  }

  /**
   * Read credentials from the local machine {@code .netrc} file.
   *
   * @throws CredentialException if an error was encountered reading the specified netrc file
   */
  public static Credentials readFromNetrcFile(String machine) throws CredentialsException {
    return readFromNetrcFile(defaultNetrcFileLocation(), machine);
  }
}
