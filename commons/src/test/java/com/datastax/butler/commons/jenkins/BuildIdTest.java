/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.jenkins;

import static com.datastax.butler.commons.json.JsonTestUtil.assertJson;
import static com.datastax.butler.commons.json.JsonTestUtil.jField;
import static com.datastax.butler.commons.json.JsonTestUtil.jObj;

import com.datastax.butler.commons.dev.Branch;
import org.junit.jupiter.api.Test;

class BuildIdTest {
  @Test
  void testJson() {
    BuildId id = WorkflowId.of("ci").job(Branch.fromString("main")).build(42);

    Object json =
        jObj(jField("workflow", "ci"), jField("job_name", "main"), jField("build_number", 42));
    assertJson(json, id, BuildId.class);
  }
}
