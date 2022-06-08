/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.service.issues;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.FailureDetails;
import com.datastax.butler.commons.dev.RunDetails;
import com.datastax.butler.commons.dev.TestFailure;
import com.datastax.butler.commons.issues.content.JiraMarkdown;
import com.datastax.butler.commons.jenkins.BuildId;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.jenkins.TestName;
import com.datastax.butler.commons.jenkins.TestRunOutput;
import com.datastax.butler.commons.jenkins.TestVariant;
import com.datastax.butler.commons.jenkins.WorkflowId;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssueContentTest {

  private static final TestCategory UTEST = TestCategory.valueOf("UNIT");
  private static final TestCategory DTEST = TestCategory.valueOf("DTEST");
  private static final TestCategory CQLTEST = TestCategory.valueOf("CQLTEST");

  @Test
  void shouldCreateSummaryForOneTest() {
    var testA = new TestName(UTEST, "com.example", "TestClass", "testA");
    var tests = List.of(testA);
    var content = new IssueContent(new JiraMarkdown());
    var out = content.title(tests);
    assertTrue(out.contains("UNIT"));
    assertTrue(out.contains(testA.fullName()));
  }

  @Test
  void shouldCutSummaryToTwoClasses() {
    var testA = new TestName(UTEST, "com.example", "TestClass", "testA");
    var testB = new TestName(DTEST, "com.example", "SomeClass", "testB");
    var testC = new TestName(CQLTEST, "com.example", "OtherClass", "testC");
    var tests = List.of(testA, testB, testC);
    var content = new IssueContent(new JiraMarkdown());
    var out = content.title(tests);
    assertTrue(out.contains("3 test failures"));
    assertTrue(out.contains(testA.className()));
    assertTrue(out.contains(testB.className()));
    assertFalse(out.contains(testC.className()));
    assertTrue(out.contains("and 1 more"));
  }

  @Test
  void shouldNotRepeatClassNamesInSummary() {
    var testA = new TestName(UTEST, "com.example", "TestClass", "testA");
    var testB = new TestName(DTEST, "com.example", "SomeClass", "testB");
    var testC = new TestName(CQLTEST, "com.example", "SomeClass", "testC");
    var tests = List.of(testA, testB, testC);
    var content = new IssueContent(new JiraMarkdown());
    var out = content.title(tests);
    assertTrue(out.contains("3 test failures"));
    assertTrue(out.contains(testA.className()));
    assertTrue(out.contains(testB.className()));
    assertTrue(out.contains(testC.className())); // as it is same for A and B
    assertFalse(out.contains("and 1 more"));
  }

  @Test
  void shouldCreateParagraphForTestFailure() {
    var testName = new TestName(UTEST, "com.example", "TestClass", "testA");
    var jobId = new JobId(WorkflowId.of("nightly"), Branch.fromString("trunk"));
    var buildId = new BuildId(jobId, 1);
    var output = new TestRunOutput("error details", "error stacktrace", "stdout", null);
    var failedRun = new RunDetails(buildId, TestVariant.DEFAULT, null, 0, true, false, output);
    var failure =
        new TestFailure(
            testName,
            null,
            FailureDetails.build(List.of(failedRun)),
            WorkflowId.of("nightly"),
            1,
            3,
            12);
    var content = new IssueContent(new JiraMarkdown());
    var p = content.paragraphForFailure(testName, failure);
    assertTrue(p.contains("UNIT"));
    assertTrue(p.contains(testName.fullName()));
    assertTrue(p.contains("error details"));
    assertTrue(p.contains("stack trace"));
    assertFalse(p.contains("stderr"));
  }
}
