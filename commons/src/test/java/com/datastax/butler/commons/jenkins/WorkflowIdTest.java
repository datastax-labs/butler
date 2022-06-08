/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.json.JsonTestUtil.assertJson;
import static com.datastax.butler.commons.json.JsonTestUtil.jField;
import static com.datastax.butler.commons.json.JsonTestUtil.jObj;

import org.junit.jupiter.api.Test;

class WorkflowIdTest {

  @Test
  void testJson() {
    WorkflowId id = WorkflowId.of("ci");
    Object json = jObj(jField("workflow", "ci"));
    assertJson(json, id, WorkflowId.class);
  }
}
