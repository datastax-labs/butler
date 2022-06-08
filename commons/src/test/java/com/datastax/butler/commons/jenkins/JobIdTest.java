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

class JobIdTest {

  @Test
  void testJson() {
    JobId id = WorkflowId.of("ci").job(Branch.fromString("main"));
    Object json = jObj(jField("workflow", "ci"), jField("job_name", "main"));
    assertJson(json, id, JobId.class);
  }
}
