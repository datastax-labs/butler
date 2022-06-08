/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.commons.jenkins.JenkinsWorkflow;
import org.junit.jupiter.api.Test;

public class JenkinsWorkflowTest {

  @Test
  void shouldReturnEmptyForWorkflowWithoutJenkinsUrl() {
    var w = new Workflow("workflow", false);
    assertTrue(JenkinsWorkflow.forWorkflow(w).isEmpty());
  }

  @Test
  void shouldReturnValidOneForWorkflowWithProperJenkinsUrl() {
    var w = new Workflow("workflow", false).withJenkinsUrl("https://jenkins.example.com");
    assertTrue(JenkinsWorkflow.forWorkflow(w).isPresent());
  }

  @Test
  void shouldReturnEmptyForWorkflowWithInvalidJenkinsUrl() {
    var w = new Workflow("workflow", false).withJenkinsUrl("hps://invalid");
    assertTrue(JenkinsWorkflow.forWorkflow(w).isEmpty());
  }
}
