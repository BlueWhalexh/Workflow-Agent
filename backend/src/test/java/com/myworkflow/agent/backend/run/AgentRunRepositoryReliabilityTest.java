package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunRepositoryReliabilityTest {

  @Test
  void completeDoesNotOverwriteCanceledRun() {
    InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
    Instant now = Instant.parse("2026-06-14T04:00:00Z");
    repository.create(
        AgentRunRecord.queued(
            "run_cancel_guard",
            "ws_cancel_guard",
            "user_cancel_guard",
            "cancel",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_cancel_guard", "run_cancel_guard", now, 3)
    );
    repository.markRunning("run_cancel_guard", "job_cancel_guard", "LOCAL_TS_WORKER", now.plusSeconds(1));
    repository.cancel("run_cancel_guard", now.plusSeconds(2));
    repository.complete(
        "run_cancel_guard",
        "job_cancel_guard",
        "LOCAL_TS_WORKER",
        new AgentWorkerResponse(
            "agent-backend-response.v1",
            "run_cancel_guard",
            "SUCCEEDED",
            "answer",
            "late success",
            false,
            false,
            List.of(".agent-runs/run_cancel_guard/artifact.json"),
            false,
            List.of()
        ),
        AgentRunStatus.SUCCEEDED,
        now.plusSeconds(3)
    );

    assertThat(repository.findRun("run_cancel_guard").orElseThrow().status())
        .isEqualTo(AgentRunStatus.CANCELED);
    assertThat(repository.findJob("job_cancel_guard").orElseThrow().status())
        .isEqualTo(AgentJobStatus.CANCELED);
    assertThat(repository.findAttempts("run_cancel_guard"))
        .extracting(RunAttemptRecord::status)
        .containsExactly(AgentJobStatus.CANCELED);
  }

  @Test
  void retryClosesCurrentAttemptAndRequeuesJob() {
    InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
    Instant now = Instant.parse("2026-06-14T05:00:00Z");
    repository.create(
        AgentRunRecord.queued(
            "run_retry_guard",
            "ws_retry_guard",
            "user_retry_guard",
            "retry",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_retry_guard", "run_retry_guard", now, 3)
    );
    repository.markRunning("run_retry_guard", "job_retry_guard", "LOCAL_TS_WORKER", now.plusSeconds(1));
    repository.retry("run_retry_guard", "job_retry_guard", "WORKER_FAILED", now.plusSeconds(2));

    assertThat(repository.findRun("run_retry_guard").orElseThrow().status())
        .isEqualTo(AgentRunStatus.QUEUED);
    AgentJobRecord job = repository.findJob("job_retry_guard").orElseThrow();
    assertThat(job.status()).isEqualTo(AgentJobStatus.QUEUED);
    assertThat(job.lockedUntil()).isNull();
    assertThat(repository.findAttempts("run_retry_guard"))
        .extracting(RunAttemptRecord::status)
        .containsExactly(AgentJobStatus.FAILED);
    assertThat(repository.findAttempts("run_retry_guard"))
        .extracting(RunAttemptRecord::errorCode)
        .containsExactly("WORKER_FAILED");
  }

  @Test
  void retryDoesNotOverwriteCanceledRun() {
    InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
    Instant now = Instant.parse("2026-06-14T06:00:00Z");
    repository.create(
        AgentRunRecord.queued(
            "run_retry_cancel_guard",
            "ws_retry_cancel_guard",
            "user_retry_cancel_guard",
            "retry cancel",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_retry_cancel_guard", "run_retry_cancel_guard", now, 3)
    );
    repository.markRunning(
        "run_retry_cancel_guard",
        "job_retry_cancel_guard",
        "LOCAL_TS_WORKER",
        now.plusSeconds(1)
    );
    repository.cancel("run_retry_cancel_guard", now.plusSeconds(2));
    repository.retry(
        "run_retry_cancel_guard",
        "job_retry_cancel_guard",
        "WORKER_FAILED",
        now.plusSeconds(3)
    );

    assertThat(repository.findRun("run_retry_cancel_guard").orElseThrow().status())
        .isEqualTo(AgentRunStatus.CANCELED);
    assertThat(repository.findJob("job_retry_cancel_guard").orElseThrow().status())
        .isEqualTo(AgentJobStatus.CANCELED);
    assertThat(repository.findAttempts("run_retry_cancel_guard"))
        .extracting(RunAttemptRecord::status)
        .containsExactly(AgentJobStatus.CANCELED);
  }

  @Test
  void failStaleRunningJobsClosesExpiredLockAndAttempt() {
    InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
    Instant now = Instant.parse("2026-06-14T07:00:00Z");
    repository.create(
        AgentRunRecord.queued(
            "run_stale_guard",
            "ws_stale_guard",
            "user_stale_guard",
            "stale",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_stale_guard", "run_stale_guard", now, 3)
    );
    repository.markRunning("run_stale_guard", "job_stale_guard", "LOCAL_TS_WORKER", now.plusSeconds(1));

    int recovered = repository.failStaleRunningJobs(now.plusSeconds(62), "STALE_LOCK", now.plusSeconds(63));

    assertThat(recovered).isEqualTo(1);
    assertThat(repository.findRun("run_stale_guard").orElseThrow().status())
        .isEqualTo(AgentRunStatus.FAILED);
    assertThat(repository.findRun("run_stale_guard").orElseThrow().errorCode())
        .isEqualTo("STALE_LOCK");
    assertThat(repository.findJob("job_stale_guard").orElseThrow().status())
        .isEqualTo(AgentJobStatus.FAILED);
    assertThat(repository.findJob("job_stale_guard").orElseThrow().lockedUntil())
        .isNull();
    RunAttemptRecord attempt = repository.findAttempts("run_stale_guard").get(0);
    assertThat(attempt.status()).isEqualTo(AgentJobStatus.FAILED);
    assertThat(attempt.errorCode()).isEqualTo("STALE_LOCK");
    assertThat(attempt.finishedAt()).isEqualTo(now.plusSeconds(63));
  }

  @Test
  void failStaleRunningJobsLeavesFreshLocksUnchanged() {
    InMemoryAgentRunRepository repository = new InMemoryAgentRunRepository();
    Instant now = Instant.parse("2026-06-14T08:00:00Z");
    repository.create(
        AgentRunRecord.queued(
            "run_fresh_guard",
            "ws_fresh_guard",
            "user_fresh_guard",
            "fresh",
            "deterministic-open-agent",
            false,
            false,
            now
        ),
        AgentJobRecord.queued("job_fresh_guard", "run_fresh_guard", now, 3)
    );
    repository.markRunning("run_fresh_guard", "job_fresh_guard", "LOCAL_TS_WORKER", now.plusSeconds(1));

    int recovered = repository.failStaleRunningJobs(now.plusSeconds(30), "STALE_LOCK", now.plusSeconds(31));

    assertThat(recovered).isEqualTo(0);
    assertThat(repository.findRun("run_fresh_guard").orElseThrow().status())
        .isEqualTo(AgentRunStatus.RUNNING);
    assertThat(repository.findJob("job_fresh_guard").orElseThrow().status())
        .isEqualTo(AgentJobStatus.RUNNING);
    assertThat(repository.findAttempts("run_fresh_guard"))
        .extracting(RunAttemptRecord::status)
        .containsExactly(AgentJobStatus.RUNNING);
  }
}
