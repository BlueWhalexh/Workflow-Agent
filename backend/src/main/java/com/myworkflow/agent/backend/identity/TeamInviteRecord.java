package com.myworkflow.agent.backend.identity;

import java.time.Instant;

public record TeamInviteRecord(
    String id,
    String teamId,
    String inviteeUserId,
    String displayName,
    TeamRole role,
    TeamInviteStatus status,
    String createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {
}
