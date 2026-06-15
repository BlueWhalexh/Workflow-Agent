package com.myworkflow.agent.backend.artifact;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.run.AgentRunRecord;
import com.myworkflow.agent.backend.run.AgentRunService;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ArtifactService {

  private final AgentRunService agentRunService;
  private final WorkspaceService workspaceService;
  private final ArtifactRepository artifactRepository;
  private final AuditService auditService;

  public ArtifactService(
      AgentRunService agentRunService,
      WorkspaceService workspaceService,
      ArtifactRepository artifactRepository,
      AuditService auditService
  ) {
    this.agentRunService = agentRunService;
    this.workspaceService = workspaceService;
    this.artifactRepository = artifactRepository;
    this.auditService = auditService;
  }

  public List<ArtifactRefRecord> listRunArtifacts(String runId) {
    agentRunService.getRun(runId);
    return artifactRepository.findByRunId(runId);
  }

  public ArtifactReadResult readArtifact(String artifactId) {
    ArtifactRefRecord artifact = artifactRepository.findById(artifactId)
        .orElseThrow(() -> new ArtifactNotFoundException(artifactId));
    AgentRunRecord run = agentRunService.getRun(artifact.runId());
    Path artifactPath = workspaceService.resolveContentPath(run.workspaceId(), artifact.artifactRef());
    if (!Files.isRegularFile(artifactPath)) {
      throw new ArtifactNotFoundException(artifactId);
    }
    try {
      String content = Files.readString(artifactPath);
      auditService.record(run.workspaceId(), run.runId(), "ARTIFACT_READ", "Artifact read");
      return new ArtifactReadResult(artifact, content);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  public record ArtifactReadResult(
      ArtifactRefRecord artifact,
      String content
  ) {
  }
}
