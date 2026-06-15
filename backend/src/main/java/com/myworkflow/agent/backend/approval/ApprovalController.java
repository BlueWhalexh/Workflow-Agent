package com.myworkflow.agent.backend.approval;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ApprovalController {

  private final ApprovalService approvalService;

  public ApprovalController(ApprovalService approvalService) {
    this.approvalService = approvalService;
  }

  @GetMapping("/v1/agent-runs/{runId}/approvals")
  public ApiEnvelope<List<ApprovalResponse>> listRunApprovals(@PathVariable String runId) {
    return ApiEnvelope.ok(approvalService.listRunApprovals(runId).stream()
        .map(ApprovalController::toResponse)
        .toList());
  }

  @PostMapping(
      path = "/v1/agent-runs/{runId}/approvals",
      consumes = MediaType.APPLICATION_JSON_VALUE
  )
  public ApiEnvelope<ApprovalResponse> decide(
      @PathVariable String runId,
      @Valid @RequestBody ApprovalDecisionRequest request
  ) {
    return ApiEnvelope.ok(toResponse(approvalService.decide(
        runId,
        request.approvalId(),
        request.decision()
    )));
  }

  private static ApprovalResponse toResponse(ApprovalRequestRecord approval) {
    return new ApprovalResponse(
        approval.approvalId(),
        approval.runId(),
        approval.status(),
        approval.decision(),
        approval.artifactRef(),
        approval.targetWorkspacePaths(),
        approval.requestedByUserId(),
        approval.decidedByUserId(),
        approval.createdAt(),
        approval.decidedAt()
    );
  }

  public record ApprovalDecisionRequest(
      String approvalId,
      @NotNull ApprovalDecision decision
  ) {
  }

  public record ApprovalResponse(
      String approvalId,
      String runId,
      ApprovalStatus status,
      ApprovalDecision decision,
      String artifactRef,
      List<String> targetWorkspacePaths,
      String requestedByUserId,
      String decidedByUserId,
      Instant createdAt,
      Instant decidedAt
  ) {
  }
}
