package com.myworkflow.agent.backend.workspace;

import java.time.Instant;

public record WorkspaceRecord(
    String workspaceId,
    String teamId,
    String name,
    String serverStorageRef,
    String defaultBranch,
    WorkspaceStatus status,
    Instant createdAt
) {
}
