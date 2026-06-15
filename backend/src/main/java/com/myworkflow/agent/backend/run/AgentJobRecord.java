package com.myworkflow.agent.backend.run;

import java.time.Instant;

public record AgentJobRecord(
    String jobId,
    String runId,
    AgentJobStatus status,
    int priority,
    Instant availableAt,
    String lockedBy,
    Instant lockedUntil,
    int attemptCount,
    int maxAttempts,
    Instant createdAt,
    Instant updatedAt
) {

  public static AgentJobRecord queued(String jobId, String runId, Instant now, int maxAttempts) {
    return new AgentJobRecord(
        jobId,
        runId,
        AgentJobStatus.QUEUED,
        0,
        now,
        null,
        null,
        0,
        maxAttempts,
        now,
        now
    );
  }

  public AgentJobRecord running(String workerKind, Instant now) {
    return new AgentJobRecord(
        jobId,
        runId,
        AgentJobStatus.RUNNING,
        priority,
        availableAt,
        workerKind,
        now.plusSeconds(60),
        attemptCount + 1,
        maxAttempts,
        createdAt,
        now
    );
  }

  public AgentJobRecord completed(AgentJobStatus nextStatus, Instant now) {
    return new AgentJobRecord(
        jobId,
        runId,
        nextStatus,
        priority,
        availableAt,
        lockedBy,
        null,
        attemptCount,
        maxAttempts,
        createdAt,
        now
    );
  }

  public AgentJobRecord retry(Instant now) {
    return new AgentJobRecord(
        jobId,
        runId,
        AgentJobStatus.QUEUED,
        priority,
        now,
        null,
        null,
        attemptCount,
        maxAttempts,
        createdAt,
        now
    );
  }
}
