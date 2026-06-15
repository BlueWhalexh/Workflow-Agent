package com.myworkflow.agent.backend.audit;

import java.time.Instant;

public record AuditEventRecord(
    String auditEventId,
    String actorUserId,
    String teamId,
    String workspaceId,
    String runId,
    String eventType,
    String message,
    Instant createdAt
) {
}
