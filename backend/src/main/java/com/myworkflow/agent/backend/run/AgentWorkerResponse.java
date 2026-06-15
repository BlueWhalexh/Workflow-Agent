package com.myworkflow.agent.backend.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentWorkerResponse(
    String schemaVersion,
    String runId,
    String status,
    String outputKind,
    String displayText,
    boolean requiresConfirmation,
    boolean requiresApproval,
    List<String> artifactRefs,
    boolean wroteWorkspace,
    List<String> targetWorkspacePaths
) {
}
