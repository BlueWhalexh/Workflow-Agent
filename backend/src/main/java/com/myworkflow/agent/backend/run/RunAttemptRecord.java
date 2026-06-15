package com.myworkflow.agent.backend.run;

import java.time.Instant;

public record RunAttemptRecord(
    String attemptId,
    String runId,
    String jobId,
    String workerKind,
    Instant startedAt,
    Instant finishedAt,
    AgentJobStatus status,
    String errorCode
) {
}
