/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.dev.JenkinsTestHelper.testResource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.datastax.butler.commons.web.WebClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class JenkinsClientTest {
  private static final String defaultHost = "jenkins.example.com";

  @Test
  void canParseHttpUrl() {
    String host = defaultHost;
    String scheme = "http";

    String url = String.format("%s://%s", scheme, host);
    JenkinsClient client = new JenkinsClient(url, null);
    HttpUrl builtUrl = client.baseUrl();

    assertEquals(scheme, builtUrl.scheme());
    // Default HTTP port (80) is used if port not specified in url
    assertEquals(80, builtUrl.port());
    assertEquals(host, builtUrl.host());
    assertEquals(1, builtUrl.pathSegments().size());
    assertEquals("", builtUrl.pathSegments().get(0));
  }

  @Test
  void canParseBaseHttpsUrlWithPort() {
    String scheme = "http";
    String host = defaultHost;
    int port = 8080;

    List<String> segments = Arrays.asList("job", "test");
    String url = String.format("%s://%s:%s/%s", scheme, host, port, String.join("/", segments));

    JenkinsClient client = new JenkinsClient(url, null);
    HttpUrl builtUrl = client.baseUrl();

    assertEquals(scheme, builtUrl.scheme());
    assertEquals(port, builtUrl.port());
    assertEquals(host, builtUrl.host());
    for (int i = 0; i < segments.size(); i++) {
      assertEquals(segments.get(i), builtUrl.pathSegments().get(i));
    }
  }

  @Test
  void invalidUrlThrowsException() {
    String url = "this is a bogus url";
    JenkinsClient client = new JenkinsClient(url, null);
    Assertions.assertThrows(JenkinsException.class, client::baseUrl);
  }

  @Test
  void shouldListWorkflows() throws IOException, WebClient.InvalidRequestException {
    var webClient = Mockito.mock(WebClient.class);
    JenkinsClient client = new JenkinsClient("http://jenkins.example.com", webClient);
    Mockito.when(webClient.request(any(okhttp3.Request.class)))
        .thenReturn(testResource("web/list-jobs.json"));
    var workflows = client.listWorkflows();
    assertFalse(workflows.isEmpty());
    assertEquals(251, workflows.size());
    var workflowNames = workflows.stream().map(WorkflowId::name).collect(Collectors.toSet());
    assertTrue(workflowNames.contains("1.1-dev-fast-ci-daily"));
    assertTrue(workflowNames.contains("adrestia"));
    assertTrue(workflowNames.contains("example-project-2.1"));
    assertTrue(workflowNames.contains("compile-command-annotations"));
    assertTrue(workflowNames.contains("daily-fast-ci-builds"));
    assertTrue(workflowNames.contains("mydb-1.0-dev-cqlsh-tests"));
  }

  @Test
  void shouldFailListJobsOnSimpleWorkflow() throws WebClient.InvalidRequestException, IOException {
    var webClient = Mockito.mock(WebClient.class);
    JenkinsClient client = new JenkinsClient("http://jenkins.example.com", webClient);
    Mockito.when(webClient.request(any(okhttp3.Request.class)))
        .thenReturn(testResource("web/list-jobs.json"));
    assertThrows(
        IllegalArgumentException.class,
        () -> client.listJobs(WorkflowId.of("test-pr-checks"), Optional.empty()));
  }

  @Test
  void shouldRecognizeMultiBranchPipeline() throws WebClient.InvalidRequestException, IOException {
    var webClient = Mockito.mock(WebClient.class);
    JenkinsClient client = new JenkinsClient("http://jenkins.example.com", webClient);
    Mockito.when(webClient.request(any(okhttp3.Request.class)))
        .thenReturn(testResource("web/list-jobs.json"));
    assertTrue(client.isMultiBranchPipeline(WorkflowId.of("ci")));
    assertTrue(client.isMultiBranchPipeline(WorkflowId.of("not-existing-pipeline")));
    assertFalse(client.isMultiBranchPipeline(WorkflowId.of("test-pr-checks")));
  }

  @Test
  void shouldListJobs() throws WebClient.InvalidRequestException, IOException {
    var webClient = Mockito.mock(WebClient.class);
    JenkinsClient client = new JenkinsClient("http://jenkins.example.com", webClient);
    Mockito.when(webClient.request(any(okhttp3.Request.class)))
        .thenReturn(testResource("web/list-jobs.json"));
    assertNotNull(client.listJobs(WorkflowId.of("ci"), Optional.empty()));
  }
}
