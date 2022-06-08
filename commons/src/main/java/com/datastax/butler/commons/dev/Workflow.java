/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.dev;

import com.datastax.butler.commons.jenkins.JenkinsBuild;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Workflow describes how to translate tests results to butler data.
 *
 * <p>It tells jenkins URL for given workflow, and if it is multi- or single-branch; It tells which
 * branch is considered "main", if the branch is upstream etc; It tells category for given test;
 *
 * <p>To have butler regularly scrape and display information for a Workflow, it must be inserted
 * into the upstream_workflows table by name, manually.
 */
public class Workflow {

  private final boolean runsAsCI; // if this workflow is run periodically as CI
  private final String name; // name must reflect jenkins job name part
  private String mainBranch; // main branch name e.g. trunk or main or master
  private Set<String> upstreamBranches; // list of upstream e.g. release branches
  private final List<TestNameScheme> testNameSchemes = new ArrayList<>();
  private final TestNameScheme allAsOtherScheme = TestNameScheme.acceptAllAsOtherScheme();
  private final List<String> jiraProjects = new ArrayList<>();
  private String jenkinsUrl = null;
  private String jenkinsUrlPath;
  private final Set<String> userBranchCompareWorkflows = new HashSet<>();
  private final Set<String> upstreamBranchCompareWorkflows = new HashSet<>();

  // workflow should have a way to decide if builds should be skipped
  // for example if they are broken or incomplete (not enough tests run)
  private final List<WorkflowChecks.SkipJenkinsBuildCheck> skipBuildChecks = new ArrayList<>();

  public Workflow(String name, boolean isCI) {
    this.name = name;
    this.runsAsCI = isCI;
  }

  /** Configure branches for this workflow. */
  public Workflow setBranches(String name, Collection<String> upstreamBranches) {
    this.mainBranch = name;
    this.upstreamBranches =
        (upstreamBranches != null) ? Set.copyOf(upstreamBranches) : Sets.newHashSet();
    return this;
  }

  /**
   * Set jira projects for workflow: main (for reporting), and others (for linking and search only).
   */
  public Workflow withJiraProjects(String main, Collection<String> others) {
    this.jiraProjects.clear();
    this.jiraProjects.add(main);
    this.jiraProjects.addAll(others);
    return this;
  }

  public Optional<String> mainJiraProject() {
    return jiraProjects.stream().findFirst();
  }

  public List<String> allJiraProjects() {
    return jiraProjects;
  }

  public Workflow withJenkinsUrl(String url) {
    this.jenkinsUrl = url;
    return this;
  }

  public Workflow withJenkinsUrlPath(String path) {
    this.jenkinsUrlPath = path;
    return this;
  }

  public Optional<String> getJenkinsUrlPath() {
    return Optional.ofNullable(this.jenkinsUrlPath);
  }

  public String getJenkinsUrl() {
    return this.jenkinsUrl;
  }

  public void withTestNameScheme(TestNameScheme scheme) {
    this.testNameSchemes.add(scheme);
  }

  public String name() {
    return name;
  }

  public WorkflowId workflowId() {
    return WorkflowId.of(name);
  }

  public boolean runsAsCI() {
    return runsAsCI;
  }

  public boolean isBranchMain(String branch) {
    return branch.equals(mainBranch);
  }

  public boolean isBranchUpstream(String branch) {
    return isBranchMain(branch) || upstreamBranches.contains(branch);
  }

  /** List of upstream branches e.g. for releases. */
  public Set<String> upstreamBranches() {
    var res = new HashSet<>(this.upstreamBranches);
    if (this.mainBranch != null) res.add(this.mainBranch);
    return res;
  }

  /**
   * Determines if this workflow build on a given branch should be considered "UPSTREAM".
   *
   * @param branch name of the branch
   * @return job category: e.g. UPSTREAM or USER
   */
  public JobId.Category jobCategory(String branch) {
    if (runsAsCI() && isBranchUpstream(branch)) {
      return JobId.Category.UPSTREAM;
    } else {
      return JobId.Category.USER;
    }
  }

  /**
   * Determines if this workflow build on a given branch should be considered "UPSTREAM".
   *
   * @param branch name of the branch
   * @return job category: e.g. UPSTREAM or USER
   */
  public JobId.Category jobCategory(Branch branch) {
    return jobCategory(branch.toString());
  }

  /** Returns first matching test naming scheme from all registered. */
  public TestNameScheme matchTestNameScheme(String className) {
    return testNameSchemes.stream()
        .filter(x -> x.suiteMatchesPattern(className))
        .findFirst()
        .orElse(allAsOtherScheme);
  }

  /**
   * Return upstream branches that potentially are base for given user branch. This heuristic is
   * very much project specific as it depends on the branch naming strategy.
   *
   * @return potentially matching upstream branches
   */
  public Set<String> upstreamBranchesForBranch(@SuppressWarnings("unused") String branch) {
    return upstreamBranches();
  }

  /**
   * Add check for skipping jenkins build.
   *
   * @param check check implementation.
   * @return self
   */
  public Workflow addSkipBuildCheck(WorkflowChecks.SkipJenkinsBuildCheck check) {
    skipBuildChecks.add(check);
    return this;
  }

  /**
   * Return TRUE if jenkins build should be skipped and not imported.
   *
   * @param jenkinsBuild jenkinsBuild to analyze
   * @return true if skip, false if proceed and import
   */
  public boolean shouldSkipBuild(JenkinsBuild jenkinsBuild) {
    return skipBuildChecks.stream().anyMatch(check -> check.skip(jenkinsBuild));
  }

  /** Add workflows that jobs on user branches should be compared with. */
  public Workflow userBranchCompareWith(Collection<String> otherWorkflows) {
    userBranchCompareWorkflows.addAll(otherWorkflows);
    return this;
  }

  /** Add workflows that jobs on user branches should be compared with. */
  public Workflow upstreamBranchCompareWith(Collection<String> otherWorkflows) {
    upstreamBranchCompareWorkflows.addAll(otherWorkflows);
    return this;
  }

  /**
   * Return (potenatially empty) set of worklflows that build on given branch should be compared
   * with.
   *
   * @param branch branch name
   * @return if empty we should show history, in other case try to compare vs best from returned.
   */
  public Set<String> workflowsToCompareBuildWith(String branch) {
    if (isBranchUpstream(branch)) {
      return upstreamBranchCompareWorkflows;
    } else {
      return userBranchCompareWorkflows.isEmpty() ? Set.of(name) : userBranchCompareWorkflows;
    }
  }

  @Override
  public String toString() {
    return name;
  }
}
