/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.projects.apache.cassandra;

import com.datastax.butler.commons.dev.TestNameScheme;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.issues.IssueTrackingProject;
import com.datastax.butler.commons.jenkins.TestCategory;
import com.datastax.butler.commons.projects.ButlerProject;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

/**
 * Project for Apache Cassandra builds.
 *
 * <p>Apache Cassandra builds in https://ci-cassandra.apache.org are organized differently. There is
 * a job for every version e.g. https://ci-cassandra.apache.org/job/Cassandra-4.0/
 * https://ci-cassandra.apache.org/job/Cassandra-4.0.0/
 * https://ci-cassandra.apache.org/job/Cassandra-3.11/
 *
 * <p>Due to above structure if we want to automatically import jobs from jenkins we need to create
 * separate workflow for each version so that the name of workflow matches jenkins job name.
 *
 * <p>Alternative approach would be to create single worklow with N branches. This would still work
 * and allow us to import via rest api.
 */
@Component
public class Cassandra implements ButlerProject {

  private static final TestCategory UNIT = TestCategory.valueOf("UNIT");
  private static final TestCategory DTEST = TestCategory.valueOf("DTEST");
  private static final TestCategory CQLSH = TestCategory.valueOf("CQLSH");
  private static final TestCategory DTEST_UPGRADE = TestCategory.valueOf("DT-UPGR");
  private static final TestCategory JVM_UPGRADE = TestCategory.valueOf("JVM-UPGR");

  private static final String JENKINS_URL = "https://ci-cassandra.apache.org";

  @Override
  public @Nonnull List<Workflow> workflows() {
    return List.of(
        cassandraBuildWorkflow("Cassandra-2.2", "cassandra-2.2"),
        cassandraBuildWorkflow("Cassandra-3.0", "cassandra-3.0"),
        cassandraBuildWorkflow("Cassandra-3.11", "cassandra-3.11"),
        cassandraBuildWorkflow("Cassandra-4.0", "cassandra-4.0"),
        cassandraBuildWorkflow("Cassandra-trunk", "trunk"));
  }

  @Override
  public @Nonnull List<IssueTrackingProject> issueTrackingProjects() {
    return List.of(ApacheCassandraJiraProject.forKey("CASSANDRA"));
  }

  /** Create a cassandra workflow with given upstream branch. */
  public Workflow cassandraBuildWorkflow(String name, String branch) {
    var w = new Workflow(name, true);
    w.setBranches(branch, Collections.emptyList());
    w.withTestNameScheme(unitTestScheme());
    w.withTestNameScheme(jvmUpgradeTestScheme());
    w.withTestNameScheme(dtestUpgradeTestScheme());
    w.withTestNameScheme(cqlshTestScheme());
    w.withTestNameScheme(dtestTestScheme());
    w.withJiraProjects("CASSANDRA", Collections.emptyList());
    w.withJenkinsUrl(JENKINS_URL);
    return w;
  }

  /** Test Naming Scheme for cassandra-build unit tests. */
  public static TestNameScheme unitTestScheme() {
    return new TestNameScheme(
        UNIT.toString(),
        List.of("org.apache", "com.datastax", "com.google"),
        new UnitTestIdExtract(UNIT));
  }

  /** Test Naming Scheme for cassandra-build jvm upgrade tests. */
  public static TestNameScheme jvmUpgradeTestScheme() {
    return new TestNameScheme(
        JVM_UPGRADE.toString(),
        List.of("org.apache.cassandra.distributed.upgrade"),
        new UnitTestIdExtract(JVM_UPGRADE));
  }

  /** Test Naming Scheme for cassandra-build python dtests. */
  public static TestNameScheme dtestTestScheme() {
    return new TestNameScheme(
        DTEST.toString(), List.of("^dtest-", "^dtest[.]"), new DTestIdExtract(DTEST, "dtest-"));
  }

  /** Test Naming Scheme for cassandra-build cqlshlib tests. */
  public static TestNameScheme cqlshTestScheme() {
    return new TestNameScheme(
        CQLSH.toString(), List.of("^cqlshlib."), new CqlshlibIdExtract(CQLSH));
  }

  /** Test Naming Scheme for dtest upgrade tests. */
  public static TestNameScheme dtestUpgradeTestScheme() {
    return new TestNameScheme(
        DTEST_UPGRADE.toString(),
        List.of("^dtest-upgrade-"),
        new DTestIdExtract(DTEST_UPGRADE, "dtest-upgrade-"));
  }
}
