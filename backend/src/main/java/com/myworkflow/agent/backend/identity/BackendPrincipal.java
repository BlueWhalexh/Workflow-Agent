package com.myworkflow.agent.backend.identity;

public record BackendPrincipal(
    String userId,
    String teamId,
    String displayName
) {
}
