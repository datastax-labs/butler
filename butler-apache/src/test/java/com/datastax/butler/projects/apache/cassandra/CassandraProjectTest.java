/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.projects.apache.cassandra;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class CassandraProjectTest {

  @Test
  void shouldConfigureWorkflows() {
    // we do not check exact conditions as they will change with new workflows
    assertFalse(new Cassandra().workflows().isEmpty());
  }

  @Test
  void shouldConfigureJiraProjects() {
    // we do not check exact conditions as they will change with new workflows
    assertFalse(new Cassandra().issueTrackingProjects().isEmpty());
  }
}
