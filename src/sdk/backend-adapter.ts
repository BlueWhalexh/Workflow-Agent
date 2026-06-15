import {
  createKnowledgeWorkflowAgent,
  type AgentSdkOutputKind,
  type AgentSdkProviderRuntimeConfig,
  type AgentSdkProviderRuntimeDependencies,
  type AgentSdkRunMode,
  type AgentSdkRunResult,
  type AgentSdkRunStatus
} from "./knowledge-workflow-agent.js";

export interface BackendAgentRequest {
  workspaceRoot: string;
  userMessage: string;
  runId?: string;
  mode?: AgentSdkRunMode;
  execute?: boolean;
  autoApprove?: boolean;
  providerRuntime?: AgentSdkProviderRuntimeConfig;
  providerRuntimeDependencies?: AgentSdkProviderRuntimeDependencies;
}

export interface BackendAgentResponse {
  schemaVersion: "agent-backend-response.v1";
  runId: string;
  status: AgentSdkRunStatus;
  outputKind: AgentSdkOutputKind;
  displayText: string | null;
  requiresConfirmation: boolean;
  requiresApproval: boolean;
  artifactRefs: string[];
  wroteWorkspace: boolean;
  targetWorkspacePaths: string[];
  source: AgentSdkRunResult;
}

function firstParagraph(content: string | undefined): string | undefined {
  return content?.trim().split(/\n\s*\n/u)[0]?.trim();
}

function displayTextFromRun(result: AgentSdkRunResult): string | null {
  if (result.outputKind === "answer") {
    return result.output?.answer ?? null;
  }
  if (result.outputKind === "draft") {
    return result.output?.draftArtifact?.content ?? null;
  }
  if (result.outputKind === "candidate-patch") {
    const candidatePatch = result.output?.candidatePatch;
    return candidatePatch?.rationale ?? firstParagraph(candidatePatch?.files[0]?.content) ?? null;
  }
  if (result.outputKind === "confirmation") {
    return result.output?.confirmation?.questions.join("\n") ?? null;
  }
  return null;
}

function artifactRefsFromRun(result: AgentSdkRunResult): string[] {
  return [
    result.artifacts.artifactPath,
    result.artifacts.reportPath,
    result.artifacts.tracePath,
    ...result.artifacts.rawProviderRefs
  ].filter((ref): ref is string => Boolean(ref));
}

export function toBackendAgentResponse(result: AgentSdkRunResult): BackendAgentResponse {
  return {
    schemaVersion: "agent-backend-response.v1",
    runId: result.runId,
    status: result.status,
    outputKind: result.outputKind,
    displayText: displayTextFromRun(result),
    requiresConfirmation: result.outputKind === "confirmation",
    requiresApproval: result.outputKind === "candidate-patch" || result.outputKind === "route-preview",
    artifactRefs: artifactRefsFromRun(result),
    wroteWorkspace: result.artifacts.wroteWorkspace,
    targetWorkspacePaths: result.artifacts.targetWorkspacePaths,
    source: result
  };
}

export async function runBackendAgent(request: BackendAgentRequest): Promise<BackendAgentResponse> {
  const agent = createKnowledgeWorkflowAgent();
  const result = await agent.runAgent({
    workspaceRoot: request.workspaceRoot,
    message: request.userMessage,
    runId: request.runId,
    mode: request.mode,
    execute: request.execute,
    autoApprove: request.autoApprove,
    providerRuntime: request.providerRuntime,
    providerRuntimeDependencies: request.providerRuntimeDependencies
  });

  return toBackendAgentResponse(result);
}
