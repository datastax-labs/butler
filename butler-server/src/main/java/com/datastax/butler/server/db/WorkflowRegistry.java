/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** A registry of workflows definitions. */
public class WorkflowRegistry {

  private final List<Workflow> workflows = new ArrayList<>();

  public Stream<Workflow> stream() {
    return workflows.stream();
  }

  public void add(Workflow w) {
    workflows.add(w);
  }

  public void addAll(Collection<Workflow> ws) {
    workflows.addAll(ws);
  }

  public Optional<Workflow> getWorkflow(String name) {
    return workflows.stream().filter(x -> x.name().equals(name)).findFirst();
  }

  public Optional<Workflow> getWorkflow(WorkflowId id) {
    return workflows.stream().filter(x -> x.name().equals(id.name())).findFirst();
  }
}
