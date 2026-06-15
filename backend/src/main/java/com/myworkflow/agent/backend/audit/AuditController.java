package com.myworkflow.agent.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.api.ApiEnvelope;
import com.myworkflow.agent.backend.api.ApiError;
import com.myworkflow.agent.backend.workspace.WorkspaceForbiddenException;
import com.myworkflow.agent.backend.workspace.WorkspaceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class AuditController {

  private static final MediaType NDJSON_MEDIA_TYPE = MediaType.valueOf("application/x-ndjson");

  private final AuditQueryService auditQueryService;
  private final ObjectMapper objectMapper;

  public AuditController(AuditQueryService auditQueryService, ObjectMapper objectMapper) {
    this.auditQueryService = auditQueryService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/v1/workspaces/{workspaceId}/audit-events")
  public ApiEnvelope<List<AuditEventResponse>> listWorkspaceAuditEvents(
      @PathVariable String workspaceId,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String runId
  ) {
    AuditEventQuery query = new AuditEventQuery(limit, offset, eventType, runId);
    return ApiEnvelope.ok(auditQueryService.listWorkspaceAuditEvents(workspaceId, query).stream()
        .map(AuditController::toResponse)
        .toList());
  }

  @GetMapping(
      path = "/v1/workspaces/{workspaceId}/audit-events/export",
      produces = "application/x-ndjson"
  )
  public ResponseEntity<String> exportWorkspaceAuditEvents(
      @PathVariable String workspaceId,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String runId
  ) {
    try {
      AuditEventQuery query = new AuditEventQuery(limit, offset, eventType, runId);
      List<AuditEventResponse> events = auditQueryService.listWorkspaceAuditEvents(workspaceId, query).stream()
          .map(AuditController::toResponse)
          .toList();
      return ResponseEntity.ok()
          .contentType(NDJSON_MEDIA_TYPE)
          .header(
              HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"%s-audit-events.ndjson\"".formatted(workspaceId)
          )
          .body(toNdjson(events));
    } catch (IllegalArgumentException exception) {
      return jsonError(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_ERROR",
          "Request validation failed"
      );
    } catch (WorkspaceForbiddenException exception) {
      return jsonError(
          HttpStatus.FORBIDDEN,
          "WORKSPACE_FORBIDDEN",
          "The current user cannot access this workspace"
      );
    } catch (WorkspaceNotFoundException exception) {
      return jsonError(
          HttpStatus.NOT_FOUND,
          "WORKSPACE_NOT_FOUND",
          "Workspace not found"
      );
    }
  }

  private ResponseEntity<String> jsonError(HttpStatus status, String code, String message) {
    ApiEnvelope<Void> envelope = ApiEnvelope.failure(new ApiError(code, message, false));
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(toJson(envelope));
  }

  private String toNdjson(List<AuditEventResponse> events) {
    return events.stream()
        .map(this::toJsonLine)
        .reduce("", (left, right) -> left + right + "\n");
  }

  private String toJsonLine(AuditEventResponse event) {
    return toJson(event);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize audit export response", exception);
    }
  }

  private static AuditEventResponse toResponse(AuditEventRecord event) {
    return new AuditEventResponse(
        event.auditEventId(),
        event.actorUserId(),
        event.teamId(),
        event.workspaceId(),
        event.runId(),
        event.eventType(),
        event.message(),
        event.createdAt(),
        recordDigest(event)
    );
  }

  private static String recordDigest(AuditEventRecord event) {
    String canonical = String.join("\n",
        nullToEmpty(event.auditEventId()),
        nullToEmpty(event.actorUserId()),
        nullToEmpty(event.teamId()),
        nullToEmpty(event.workspaceId()),
        nullToEmpty(event.runId()),
        nullToEmpty(event.eventType()),
        nullToEmpty(event.message()),
        event.createdAt().toString()
    );
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
    }
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  public record AuditEventResponse(
      String auditEventId,
      String actorUserId,
      String teamId,
      String workspaceId,
      String runId,
      String eventType,
      String message,
      Instant createdAt,
      String recordDigest
  ) {
  }
}
