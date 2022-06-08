/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.issues;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class IssueLinkTest {

  private static ObjectMapper mapper;

  @BeforeAll
  public static void createMapper() {
    mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @Test
  void shouldProperlySerializeJiraIssueLinkToJsonWithName() throws IOException {
    IssueLink issueLink = jiraIssueLink("XYZ-123", null);
    String asJson = mapper.writeValueAsString(issueLink);
    var asMap = jsonToMap(asJson);
    assertTrue(asMap.containsKey("name"));
    assertFalse(asMap.containsKey("id"));
    assertTrue(asMap.containsKey("url"));
    assertNull(asMap.getOrDefault("closed", "X"));
  }

  @Test
  void shouldProperlySerializeClosedJiraIssueLinkToJson() throws IOException {
    IssueLink issueLink = jiraIssueLink("XYZ-123", true);
    String asJson = mapper.writeValueAsString(issueLink);
    var asMap = jsonToMap(asJson);
    assertEquals(true, asMap.getOrDefault("closed", "X"));
  }

  @Test
  void shouldProperlySerializeOpenJiraIssueLinkToJson() throws IOException {
    IssueLink issueLink = jiraIssueLink("XYZ-123", false);
    String asJson = mapper.writeValueAsString(issueLink);
    var asMap = jsonToMap(asJson);
    assertEquals(false, asMap.getOrDefault("closed", "X"));
  }

  private IssueLink jiraIssueLink(String id, Boolean closed) throws MalformedURLException {
    return new IssueLink(
        IssueId.fromString(id), new URL("http://jira.example.com/browse/" + id), closed);
  }

  private Map<String, Object> jsonToMap(String json) throws JsonProcessingException {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }
}
