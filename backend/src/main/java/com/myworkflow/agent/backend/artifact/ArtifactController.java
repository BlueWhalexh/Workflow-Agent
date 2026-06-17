package com.myworkflow.agent.backend.artifact;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ArtifactController {

  private final ArtifactService artifactService;

  public ArtifactController(ArtifactService artifactService) {
    this.artifactService = artifactService;
  }

  @GetMapping("/v1/agent-runs/{runId}/artifacts")
  public ApiEnvelope<List<ArtifactResponse>> listRunArtifacts(@PathVariable String runId) {
    return ApiEnvelope.ok(artifactService.listRunArtifacts(runId).stream()
        .map(ArtifactController::toResponse)
        .toList());
  }

  @GetMapping("/v1/artifacts/{artifactId}")
  public ApiEnvelope<ArtifactReadResponse> readArtifact(@PathVariable String artifactId) {
    ArtifactService.ArtifactReadResult result = artifactService.readArtifact(artifactId);
    ArtifactRefRecord artifact = result.artifact();
    return ApiEnvelope.ok(new ArtifactReadResponse(
        artifact.artifactId(),
        artifact.runId(),
        artifact.artifactRef(),
        artifact.kind(),
        artifact.redactionStatus(),
        artifact.contentType(),
        artifact.createdAt(),
        result.content()
    ));
  }

  @PostMapping(
      path = "/v1/agent-runs/{runId}/artifacts",
      consumes = MediaType.APPLICATION_JSON_VALUE
  )
  public ApiEnvelope<ArtifactUploadResponse> uploadRunArtifact(
      @PathVariable String runId,
      @Valid @RequestBody ArtifactUploadRequest request
  ) {
    ArtifactService.ArtifactUploadResult result = artifactService.uploadRunArtifact(
        runId,
        request.schemaVersion(),
        request.artifactRef(),
        request.content()
    );
    return ApiEnvelope.ok(new ArtifactUploadResponse(
        result.runId(),
        result.artifactRef(),
        result.uploadedAt()
    ));
  }

  private static ArtifactResponse toResponse(ArtifactRefRecord artifact) {
    return new ArtifactResponse(
        artifact.artifactId(),
        artifact.runId(),
        artifact.artifactRef(),
        artifact.kind(),
        artifact.redactionStatus(),
        artifact.contentType(),
        artifact.createdAt()
    );
  }

  public record ArtifactResponse(
      String artifactId,
      String runId,
      String artifactRef,
      String kind,
      String redactionStatus,
      String contentType,
      Instant createdAt
  ) {
  }

  public record ArtifactReadResponse(
      String artifactId,
      String runId,
      String artifactRef,
      String kind,
      String redactionStatus,
      String contentType,
      Instant createdAt,
      String content
  ) {
  }

  public record ArtifactUploadRequest(
      @NotBlank String schemaVersion,
      @NotBlank String artifactRef,
      @NotNull String content
  ) {
  }

  public record ArtifactUploadResponse(
      String runId,
      String artifactRef,
      Instant uploadedAt
  ) {
  }
}
