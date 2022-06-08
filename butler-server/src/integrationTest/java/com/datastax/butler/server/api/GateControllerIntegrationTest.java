/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import static org.hamcrest.Matchers.containsString;

import com.datastax.butler.server.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GateControllerIntegrationTest extends IntegrationTest {

  @Test
  void shouldFailOnUnknownWorkflow() {
    Map<String, String> reqBody = new HashMap<>();
    reqBody.put("url", "http://jenkins.example.com/some/build/");
    reqBody.put("workflow", "not-existing");
    reqBody.put("branch", "br");
    reqBody.put("build_number", "1");
    reqBody.put("upstream_workflow", "ci");
    reqBody.put("upstream_branch", "main");

    RestAssured.given()
        .contentType(ContentType.JSON)
        .body(reqBody)
        .log()
        .all()
        .when()
        .post(apiCallUri("gate", "builds", "approve"))
        .then()
        .assertThat()
        .statusCode(400)
        .body("message", containsString("Workflow not-existing is not configured"))
        .log()
        .all();
  }
}
