package com.myworkflow.agent.backend.run;

import java.time.Instant;

public record RunEventRecord(
    String eventId,
    String runId,
    String eventType,
    AgentRunStatus status,
    String message,
    Instant createdAt
) {
}
