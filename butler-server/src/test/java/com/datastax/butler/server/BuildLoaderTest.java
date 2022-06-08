/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.butler.commons.dev.Branch;
import com.datastax.butler.commons.jenkins.JobId;
import com.datastax.butler.commons.jenkins.WorkflowId;
import com.datastax.butler.server.db.BuildsDb;
import com.datastax.butler.server.db.JobsDb;
import com.datastax.butler.server.db.UpstreamWorflowsDb;
import com.datastax.butler.server.service.BuildsService;
import com.datastax.butler.server.tools.BuildLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class BuildLoaderTest {

  private final JobsDb jobsDb = Mockito.mock(JobsDb.class);
  private final BuildsDb buildsDb = Mockito.mock(BuildsDb.class);
  private final BuildsService buildsService = Mockito.mock(BuildsService.class);
  private final UpstreamWorflowsDb upstreamWorkflowsDb = Mockito.mock(UpstreamWorflowsDb.class);

  private BuildLoader createBuildLoader(ExecutorService executors) {
    return new BuildLoader(jobsDb, buildsDb, buildsService, upstreamWorkflowsDb, executors);
  }

  private JobId mainCI() {
    return new JobId(WorkflowId.of("ci"), Branch.fromString("main"));
  }

  @Test
  void shouldSubmitJobAndExposeItsStatus() {
    BuildLoader loader = createBuildLoader(Executors.newSingleThreadExecutor());
    UUID taskId = loader.submitLoad(mainCI(), 1);
    assertNotNull(taskId);
    Optional<BuildLoader.Status> status = loader.taskStatus(taskId);
    assertTrue(status.isPresent());
  }

  @Test
  void shouldReturnTaskInProgressWhenSubmittingSameJobId() {
    List<Runnable> scheduledTasks = new ArrayList<>();
    ExecutorService executor = Mockito.mock(ExecutorService.class);
    Mockito.when(executor.submit(ArgumentMatchers.any(Runnable.class)))
        .then(
            i -> {
              scheduledTasks.add(i.getArgument(0));
              return null;
            });

    BuildLoader loader = createBuildLoader(executor);
    UUID taskId = loader.submitLoad(mainCI(), 1);
    assertNotNull(taskId);
    Optional<BuildLoader.Status> status = loader.taskStatus(taskId);
    assertTrue(status.isPresent());
    // submit again should return same task (task is waiting on paused executor)
    UUID againTaskId = loader.submitLoad(mainCI(), 1);
    assertEquals(againTaskId, taskId);
    // status should be not started
    status = loader.taskStatus(taskId);
    assertTrue(status.isPresent());
    assertFalse(status.get().started());
    // now un-pause execution and it should be eventually started and finished
    scheduledTasks.forEach(Runnable::run);
    assertTrue(loader.taskStatus(againTaskId).get().started());
    assertTrue(loader.taskStatus(againTaskId).get().finished());
    // finally status should include only 1 entry
    assertEquals(1, loader.allStatus().size());
  }
}
