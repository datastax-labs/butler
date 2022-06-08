/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.api;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.dev.Workflow;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.BuildsService;
import com.datastax.butler.server.tools.BuildLoader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CiControllerTest {

  private final JobsDb jobsDb = Mockito.mock(JobsDb.class);
  private final BuildsDb buildsDb = Mockito.mock(BuildsDb.class);
  private final UpstreamWorflowsDb workflowsDb = Mockito.mock(UpstreamWorflowsDb.class);
  private final BuildsService buildService = Mockito.mock(BuildsService.class);
  private final BuildLoader buildLoader = Mockito.mock(BuildLoader.class);

  private static final WorkflowId FAST_CI = WorkflowId.of("fast-ci");
  private static final WorkflowId NIGHTLY_CI = WorkflowId.of("nightly-ci");
  private static final WorkflowId WEEKLY_CI = WorkflowId.of("weekly-ci");

  CiController controller() {
    return new CiController(jobsDb, buildsDb, workflowsDb, buildService, buildLoader);
  }

  @Test
  void shouldListWorkflow() {
    var a = new Workflow(FAST_CI.name(), false);
    var b = new Workflow(WEEKLY_CI.name(), true);
    Mockito.when(workflowsDb.allWorkflows()).thenReturn(Set.of(a, b));
    var expected = List.of(FAST_CI, WEEKLY_CI);
    assertEquals(expected, controller().listWorkflow());
  }

  @Test
  void shouldReturnKnownJobs() {
    var fast10 = new JobId(FAST_CI, Branch.fromString("1.0-dev"));
    var fast20 = new JobId(FAST_CI, Branch.fromString("2.0-dev"));
    var weeklyMain = new JobId(WEEKLY_CI, Branch.fromString("main"));
    Mockito.when(jobsDb.getAll()).thenReturn(List.of(fast10, fast20, weeklyMain));
    var out = controller().knownJobs();
    assertEquals(3, out.size());
    checkJobInfo(fast10, out.get(0));
    checkJobInfo(fast20, out.get(1));
    checkJobInfo(weeklyMain, out.get(2));
  }

  @Test
  void shouldReturnUpstreamJobs() {
    var fast10 = new JobId(FAST_CI, Branch.fromString("1.0-dev"));
    var fast20 = new JobId(FAST_CI, Branch.fromString("2.0-dev"));
    var weeklyMain = new JobId(WEEKLY_CI, Branch.fromString("main"));
    Mockito.when(jobsDb.getConfiguredUpstreamJobs()).thenReturn(Set.of(fast10, fast20, weeklyMain));
    var out = controller().upstreamJobs();
    assertEquals(3, out.size());
    checkJobInfo(fast10, out.get(0));
    checkJobInfo(fast20, out.get(1));
    checkJobInfo(weeklyMain, out.get(2));
  }

  @Test
  void shouldReturnJobsByBranchInOrder() {
    var fast10 = new JobId(FAST_CI, Branch.fromString("1.0-dev"));
    var nightly10 = new JobId(NIGHTLY_CI, Branch.fromString("1.0-dev"));
    Mockito.when(jobsDb.getByBranch(Branch.fromString("1.0-dev")))
        .thenReturn(List.of(nightly10, fast10));
    var out = controller().branchJobs("1.0-dev");
    assertEquals(2, out.size());
    checkJobInfo(fast10, out.get(0));
    checkJobInfo(nightly10, out.get(1));
  }

  private void checkJobInfo(JobId jobId, JobInfo info) {
    assertEquals(jobId.workflow().name(), info.workflow());
    assertEquals(jobId.jobName().toString(), info.jobName().toString());
  }
}
