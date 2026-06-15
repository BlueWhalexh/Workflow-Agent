package com.myworkflow.agent.backend.run;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class AgentRunController {

  private final AgentRunService agentRunService;

  public AgentRunController(AgentRunService agentRunService) {
    this.agentRunService = agentRunService;
  }

  @PostMapping(
      path = "/v1/workspaces/{workspaceId}/agent-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE
  )
  public ApiEnvelope<AgentRunResponse> createRun(
      @PathVariable String workspaceId,
      @Valid @RequestBody CreateAgentRunRequest request
  ) {
    return ApiEnvelope.ok(toResponse(agentRunService.startRun(
        workspaceId,
        request.userMessage(),
        request.mode(),
        Boolean.TRUE.equals(request.execute()),
        Boolean.TRUE.equals(request.autoApprove()),
        request.providerRuntimeRef()
    )));
  }

  @GetMapping("/v1/agent-runs/{runId}")
  public ApiEnvelope<AgentRunResponse> getRun(@PathVariable String runId) {
    return ApiEnvelope.ok(toResponse(agentRunService.getRun(runId)));
  }

  @PostMapping("/v1/agent-runs/{runId}/cancel")
  public ApiEnvelope<AgentRunResponse> cancelRun(@PathVariable String runId) {
    return ApiEnvelope.ok(toResponse(agentRunService.cancelRun(runId)));
  }

  private static AgentRunResponse toResponse(AgentRunRecord run) {
    return new AgentRunResponse(
        run.runId(),
        run.workspaceId(),
        run.status(),
        run.outputKind(),
        run.displayText(),
        run.requiresConfirmation(),
        run.requiresApproval(),
        run.artifactRefs(),
        run.wroteWorkspace(),
        run.targetWorkspacePaths(),
        run.errorCode(),
        run.createdAt(),
        run.updatedAt()
    );
  }

  public record CreateAgentRunRequest(
      @NotBlank String userMessage,
      String mode,
      Boolean execute,
      Boolean autoApprove,
      String providerRuntimeRef
  ) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AgentRunResponse(
      String runId,
      String workspaceId,
      AgentRunStatus status,
      String outputKind,
      String displayText,
      boolean requiresConfirmation,
      boolean requiresApproval,
      List<String> artifactRefs,
      boolean wroteWorkspace,
      List<String> targetWorkspacePaths,
      String errorCode,
      Instant createdAt,
      Instant updatedAt
  ) {
  }
}
