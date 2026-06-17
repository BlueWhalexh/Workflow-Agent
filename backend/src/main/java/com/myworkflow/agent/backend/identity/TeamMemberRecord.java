package com.myworkflow.agent.backend.identity;

public record TeamMemberRecord(
    String teamId,
    String userId,
    String displayName,
    TeamRole role,
    TeamMemberStatus status
) {
}
