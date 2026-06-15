package com.myworkflow.agent.backend.workspace;

public record WorkspaceMemberRecord(
    String workspaceId,
    String userId,
    String teamId,
    WorkspaceRole role
) {
}
