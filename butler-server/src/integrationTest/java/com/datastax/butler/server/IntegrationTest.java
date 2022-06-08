/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integrationTest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IntegrationTest {

  @LocalServerPort int serverPort;

  protected URI apiCallUri(String... pathSegments) {
    var uriComponents =
        UriComponentsBuilder.newInstance()
            .scheme("http")
            .host("localhost")
            .port(serverPort)
            .path("/api");
    uriComponents.pathSegment(pathSegments);
    return uriComponents.encode().build().toUri();
  }

  protected String testResource(String path) throws IOException {
    var workingDir = Path.of("", "src/integrationTest/resources");
    Path file = workingDir.resolve(path);
    return Files.readString(file);
  }

  protected String randomBranch() {
    return RandomStringUtils.randomAlphanumeric(8);
  }

  protected String randomTestCase() {
    return "test" + RandomStringUtils.randomAscii(8);
  }
}
