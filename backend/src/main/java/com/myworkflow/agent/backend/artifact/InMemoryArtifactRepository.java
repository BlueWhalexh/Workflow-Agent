package com.myworkflow.agent.backend.artifact;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!jdbc")
public class InMemoryArtifactRepository implements ArtifactRepository {

  private final Map<String, ArtifactRefRecord> artifacts = new LinkedHashMap<>();

  @Override
  public synchronized void registerRunArtifacts(
      String runId,
      String workspaceId,
      List<String> artifactRefs,
      Instant now
  ) {
    for (ArtifactRefRecord artifact : ArtifactRefFactory.create(runId, workspaceId, artifactRefs, now)) {
      artifacts.put(artifact.artifactId(), artifact);
    }
  }

  @Override
  public synchronized List<ArtifactRefRecord> findByRunId(String runId) {
    return artifacts.values().stream()
        .filter((artifact) -> artifact.runId().equals(runId))
        .toList();
  }

  @Override
  public synchronized Optional<ArtifactRefRecord> findById(String artifactId) {
    return Optional.ofNullable(artifacts.get(artifactId));
  }
}
