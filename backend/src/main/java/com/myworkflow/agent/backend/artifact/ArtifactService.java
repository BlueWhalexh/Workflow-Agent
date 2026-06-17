package com.myworkflow.agent.backend.artifact;

import com.myworkflow.agent.backend.audit.AuditService;
import com.myworkflow.agent.backend.run.AgentRunRecord;
import com.myworkflow.agent.backend.run.AgentRunService;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import com.myworkflow.agent.backend.workspace.WorkspaceRole;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ArtifactService {

  private static final String REMOTE_RUNNER_ARTIFACT_UPLOAD_SCHEMA = "remote-runner-artifact-upload.v1";
  private static final int MAX_REMOTE_ARTIFACT_CONTENT_CHARS = 1_000_000;

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

  public ArtifactUploadResult uploadRunArtifact(
      String runId,
      String schemaVersion,
      String artifactRef,
      String content
  ) {
    if (!REMOTE_RUNNER_ARTIFACT_UPLOAD_SCHEMA.equals(schemaVersion)) {
      throw new IllegalArgumentException("Unsupported artifact upload schema");
    }
    if (artifactRef == null || artifactRef.isBlank()) {
      throw new IllegalArgumentException("Artifact ref is required");
    }
    if (content == null) {
      throw new IllegalArgumentException("Artifact content is required");
    }
    if (content.length() > MAX_REMOTE_ARTIFACT_CONTENT_CHARS) {
      throw new IllegalArgumentException("Artifact content is too large");
    }

    AgentRunRecord run = agentRunService.getRun(runId);
    workspaceService.requireWorkspaceRole(run.workspaceId(), WorkspaceRole.WORKSPACE_EDITOR);
    if (!isRunScopedArtifactRef(run.runId(), artifactRef)) {
      throw new IllegalArgumentException("Artifact ref must be scoped to the run");
    }

    Path artifactPath = workspaceService.resolveContentPath(run.workspaceId(), artifactRef);
    try {
      Files.createDirectories(artifactPath.getParent());
      Files.writeString(
          artifactPath,
          content,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
      );
      auditService.record(run.workspaceId(), run.runId(), "ARTIFACT_UPLOADED", "Remote runner artifact uploaded");
      return new ArtifactUploadResult(run.runId(), artifactRef, Instant.now());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static boolean isRunScopedArtifactRef(String runId, String artifactRef) {
    Path normalized = Path.of(artifactRef).normalize();
    if (normalized.isAbsolute()) {
      return false;
    }
    Path requiredPrefix = Path.of(".agent-runs", runId).normalize();
    return normalized.startsWith(requiredPrefix) && !normalized.equals(requiredPrefix);
  }

  public record ArtifactReadResult(
      ArtifactRefRecord artifact,
      String content
  ) {
  }

  public record ArtifactUploadResult(
      String runId,
      String artifactRef,
      Instant uploadedAt
  ) {
  }
}
