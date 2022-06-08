/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorkflowRegistryTest {

  private final Workflow x = new Workflow("x", false);
  private final Workflow y = new Workflow("y", false);

  @Test
  void testGetByName() {
    var r = new WorkflowRegistry();
    r.add(x);
    r.add(y);
    assertTrue(r.getWorkflow("x").isPresent());
    assertTrue(r.getWorkflow("y").isPresent());
    assertTrue(r.getWorkflow("z").isEmpty());
  }

  @Test
  void testGetByWorkflowId() {
    var r = new WorkflowRegistry();
    r.add(new Workflow("x", false));
    r.add(new Workflow("y", false));
    assertTrue(r.getWorkflow(WorkflowId.of("x")).isPresent());
    assertTrue(r.getWorkflow(WorkflowId.of("y")).isPresent());
    assertTrue(r.getWorkflow(WorkflowId.of("z")).isEmpty());
  }

  @Test
  void testAddAllAndStream() {
    var r = new WorkflowRegistry();
    r.addAll(List.of(x, y));
    assertEquals(List.of(x, y), r.stream().collect(Collectors.toList()));
  }
}
