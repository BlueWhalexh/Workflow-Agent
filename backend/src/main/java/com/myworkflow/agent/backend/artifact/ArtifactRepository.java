package com.myworkflow.agent.backend.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArtifactRepository {

  void registerRunArtifacts(String runId, String workspaceId, List<String> artifactRefs, Instant now);

  List<ArtifactRefRecord> findByRunId(String runId);

  Optional<ArtifactRefRecord> findById(String artifactId);
}
