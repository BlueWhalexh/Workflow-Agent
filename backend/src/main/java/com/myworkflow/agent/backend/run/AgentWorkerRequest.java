package com.myworkflow.agent.backend.run;

import java.util.Map;

public record AgentWorkerRequest(
    String runId,
    String workspaceRoot,
    String userMessage,
    String mode,
    boolean execute,
    boolean autoApprove,
    Map<String, Object> providerRuntime
) {
}
