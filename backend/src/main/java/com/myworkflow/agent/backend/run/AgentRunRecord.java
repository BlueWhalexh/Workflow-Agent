package com.myworkflow.agent.backend.run;

import java.time.Instant;
import java.util.List;

public record AgentRunRecord(
    String runId,
    String workspaceId,
    String requestedByUserId,
    String userMessage,
    String mode,
    boolean execute,
    boolean autoApprove,
    AgentRunStatus status,
    String outputKind,
    String displayText,
    boolean requiresApproval,
    boolean requiresConfirmation,
    boolean wroteWorkspace,
    List<String> artifactRefs,
    List<String> targetWorkspacePaths,
    String errorCode,
    Instant createdAt,
    Instant updatedAt
) {

  public static AgentRunRecord queued(
      String runId,
      String workspaceId,
      String requestedByUserId,
      String userMessage,
      String mode,
      boolean execute,
      boolean autoApprove,
      Instant now
  ) {
    return new AgentRunRecord(
        runId,
        workspaceId,
        requestedByUserId,
        userMessage,
        mode,
        execute,
        autoApprove,
        AgentRunStatus.QUEUED,
        null,
        null,
        false,
        false,
        false,
        List.of(),
        List.of(),
        null,
        now,
        now
    );
  }

  public AgentRunRecord withStatus(AgentRunStatus nextStatus, Instant now) {
    return new AgentRunRecord(
        runId,
        workspaceId,
        requestedByUserId,
        userMessage,
        mode,
        execute,
        autoApprove,
        nextStatus,
        outputKind,
        displayText,
        requiresApproval,
        requiresConfirmation,
        wroteWorkspace,
        artifactRefs,
        targetWorkspacePaths,
        errorCode,
        createdAt,
        now
    );
  }

  public AgentRunRecord completed(
      AgentRunStatus nextStatus,
      AgentWorkerResponse response,
      Instant now
  ) {
    return new AgentRunRecord(
        runId,
        workspaceId,
        requestedByUserId,
        userMessage,
        mode,
        execute,
        autoApprove,
        nextStatus,
        response.outputKind(),
        response.displayText(),
        response.requiresApproval(),
        response.requiresConfirmation(),
        response.wroteWorkspace(),
        response.artifactRefs(),
        response.targetWorkspacePaths(),
        null,
        createdAt,
        now
    );
  }

  public AgentRunRecord failed(String nextErrorCode, Instant now) {
    return new AgentRunRecord(
        runId,
        workspaceId,
        requestedByUserId,
        userMessage,
        mode,
        execute,
        autoApprove,
        AgentRunStatus.FAILED,
        outputKind,
        displayText,
        requiresApproval,
        requiresConfirmation,
        wroteWorkspace,
        artifactRefs,
        targetWorkspacePaths,
        nextErrorCode,
        createdAt,
        now
    );
  }

  public AgentRunRecord canceled(Instant now) {
    return new AgentRunRecord(
        runId,
        workspaceId,
        requestedByUserId,
        userMessage,
        mode,
        execute,
        autoApprove,
        AgentRunStatus.CANCELED,
        outputKind,
        displayText,
        requiresApproval,
        requiresConfirmation,
        wroteWorkspace,
        artifactRefs,
        targetWorkspacePaths,
        errorCode,
        createdAt,
        now
    );
  }
}
