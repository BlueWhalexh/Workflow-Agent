package com.myworkflow.agent.backend.approval;

import java.time.Instant;
import java.util.List;

public record ApprovalRequestRecord(
    String approvalId,
    String runId,
    String workspaceId,
    String requestedByUserId,
    String decidedByUserId,
    ApprovalDecision decision,
    ApprovalStatus status,
    String artifactRef,
    List<String> targetWorkspacePaths,
    Instant createdAt,
    Instant decidedAt
) {
}
