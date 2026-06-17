package com.myworkflow.agent.backend.runner;

import java.time.Instant;
import java.util.List;

public record RemoteRunnerRecord(
    String runnerRecordId,
    String workspaceId,
    String runnerRef,
    String displayName,
    String endpointUrl,
    RemoteRunnerStatus status,
    List<String> capabilities,
    Instant lastSeenAt,
    String leaseOwner,
    Instant leaseExpiresAt,
    Instant createdAt,
    Instant updatedAt
) {
}
