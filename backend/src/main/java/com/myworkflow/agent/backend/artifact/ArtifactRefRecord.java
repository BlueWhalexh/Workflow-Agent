package com.myworkflow.agent.backend.artifact;

import java.time.Instant;

public record ArtifactRefRecord(
    String artifactId,
    String runId,
    String workspaceId,
    String artifactRef,
    String kind,
    String redactionStatus,
    String contentType,
    int sortOrder,
    Instant createdAt
) {
}
