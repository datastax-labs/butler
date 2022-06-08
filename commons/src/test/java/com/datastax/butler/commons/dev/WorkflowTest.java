/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import static com.datastax.butler.commons.dev.JenkinsTestHelper.jenkinsBuild;
import static com.datastax.butler.commons.dev.JenkinsTestHelper.testReportSummary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.datastax.butler.commons.jenkins.JobId;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowTest {

  @Test
  void mainIsAlsoUpstream() {
    var w = new Workflow("nightly-test", true).setBranches("trunk", Collections.emptyList());
    assertTrue(w.isBranchMain("trunk"));
    assertTrue(w.isBranchUpstream("trunk"));
    assertFalse(w.isBranchMain("master"));
    assertFalse(w.isBranchUpstream("master"));
    assertFalse(w.isBranchUpstream("XYZ-231"));
  }

  @Test
  void shouldIncludeMainInUpstreamBranches() {
    var w = new Workflow("nightly-test", true).setBranches("trunk", List.of("5.1-dev"));
    assertEquals(Set.of("trunk", "5.1-dev"), w.upstreamBranches());
  }

  @Test
  void isUpstream() {
    var upstreams = List.of("6.8-dev", "6.7-dev");
    var w = new Workflow("nightly-test", true).setBranches("master", upstreams);
    assertTrue(w.isBranchUpstream("master"));
    assertTrue(w.isBranchUpstream("6.8-dev"));
    assertTrue(w.isBranchUpstream("6.7-dev"));
    assertFalse(w.isBranchUpstream("6.0-dev"));
    assertFalse(w.isBranchUpstream("somebranch"));
  }

  @Test
  void jobCategoryOnNonCiWorkflowIsAlwaysUser() {
    var upstreams = List.of("6.8-dev", "6.7-dev");
    var w = new Workflow("nightly-test", false).setBranches("master", upstreams);
    assertEquals(JobId.Category.USER, w.jobCategory("master"));
    assertEquals(JobId.Category.USER, w.jobCategory("6.8-dev"));
  }

  @Test
  void jobCategoryOnCiWorkflowDependsOnBranch() {
    var upstreams = List.of("6.8-dev", "6.7-dev");
    var w = new Workflow("nightly-test", true).setBranches("master", upstreams);
    assertEquals(JobId.Category.UPSTREAM, w.jobCategory("master"));
    assertEquals(JobId.Category.UPSTREAM, w.jobCategory("6.8-dev"));
    assertEquals(JobId.Category.USER, w.jobCategory("non-upstream-branch"));
    assertEquals(JobId.Category.USER, w.jobCategory("1.0-dev"));
    assertEquals(JobId.Category.USER, w.jobCategory("XYZ-234"));
  }

  @Test
  void shouldReturnJiraProjects() {
    var w = new Workflow("nightly", true).withJiraProjects("XYZ", List.of("PRJ"));
    assertEquals("XYZ", w.mainJiraProject().orElseThrow());
    assertEquals(List.of("XYZ", "PRJ"), w.allJiraProjects());
  }

  @Test
  void shouldApplyAllSkipChecks() {
    var w = new Workflow("nightly", true);
    w.addSkipBuildCheck(new WorkflowChecks.SkipJenkinsBuildWithNotEnoughTests(8));
    w.addSkipBuildCheck(new WorkflowChecks.SkipJenkinsBuildWithNotEnoughTests(3));
    assertFalse(w.shouldSkipBuild(jenkinsBuild(testReportSummary(0, 0, 10))));
    assertTrue(w.shouldSkipBuild(jenkinsBuild(testReportSummary(0, 0, 7))));
    assertTrue(w.shouldSkipBuild(jenkinsBuild(testReportSummary(0, 0, 2))));
  }

  @Test
  void shouldNotSkipWithoutChecks() {
    var w = new Workflow("nightly", true);
    assertFalse(w.shouldSkipBuild(jenkinsBuild(testReportSummary(0, 0, 0))));
  }

  @Test
  void shouldCompareUserBranchWithSelfByDefault() {
    var w = new Workflow("workflow", false).setBranches("main", List.of("dev"));
    assertEquals(Set.of("workflow"), w.workflowsToCompareBuildWith("some-user-branch"));
  }

  @Test
  void shouldHaveEmptyCompareWorkflowsForUpstreamBranchByDefault() {
    var w = new Workflow("workflow", false).setBranches("main", List.of("dev"));
    assertTrue(w.workflowsToCompareBuildWith("main").isEmpty());
    assertTrue(w.workflowsToCompareBuildWith("dev").isEmpty());
  }

  @Test
  void shouldCompareWithDeclaredWorkflows() {
    var w = new Workflow("workflow", false).setBranches("main", List.of("dev"));
    w.userBranchCompareWith(List.of("daily", "nightly"));
    w.upstreamBranchCompareWith(List.of("workflow-java11"));
    assertEquals(Set.of("daily", "nightly"), w.workflowsToCompareBuildWith("some-user-branch"));
    assertEquals(Set.of("workflow-java11"), w.workflowsToCompareBuildWith("main"));
    assertEquals(Set.of("workflow-java11"), w.workflowsToCompareBuildWith("dev"));
  }
}
