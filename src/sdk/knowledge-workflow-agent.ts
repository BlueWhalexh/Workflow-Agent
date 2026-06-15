import path from "node:path";
import { promises as fs } from "node:fs";
import type { ProviderRuntimeDependencies } from "../runtime/provider/provider-registry.js";
import type { ProviderRuntimeConfig } from "../runtime/provider/provider-runtime-config.js";
import { runOrganizeWorkflow } from "../runtime/langgraph/graph.js";
import type { GraphState } from "../runtime/langgraph/state.js";
import {
  runOpenAgentGraph,
  type RunOpenAgentGraphRequest,
  type RunOpenAgentGraphResult
} from "../runtime/open-agent/open-agent-graph.js";
import {
  inspectOpenAgentRealSmoke,
  runOpenAgentRealSmoke,
  type OpenAgentRealSmokeResult,
  type OpenAgentRealSmokeProvider
} from "../runtime/provider/open-agent-real-smoke.js";
import { internalTools } from "../tools/internal-tool-registry.js";
import { assertSafeArtifactSlug } from "../storage/artifact-slug.js";
import {
  classifyCommand,
  handleCommand,
  type CommandConfirmation,
  type CommandRoute,
  type HandleCommandRequest,
  type HandleCommandResult
} from "./command-router.js";
import {
  runOpenAgentTask,
  type CandidatePatchProposal,
  type DraftArtifact,
  type OpenAgentOutputPolicy,
  type RunOpenAgentTaskRequest,
  type RunOpenAgentTaskResult
} from "./open-agent-runtime.js";
import { inspectRun, type InspectRunRequest, type InspectRunResult } from "./run-inspector.js";

export interface KnowledgeWorkflowAgentConfig {
  defaultMethodologyId?: string;
}

export interface RunOrganizeRequest {
  workspaceRoot: string;
  instruction: string;
  runId?: string;
  methodologyId?: string;
  autoApprove: boolean;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}

export interface RunOrganizeResult {
  runId: string;
  status: "WAITING_PLAN_APPROVAL" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  methodologyId: string;
  planPath?: string;
  reportPath?: string;
  lastError?: string;
  artifactRoot: string;
}

export type AgentSdkRunMode = "auto" | "deterministic-open-agent" | "llm-open-agent" | "fixed-workflow";

export type AgentSdkRunStatus =
  | "SUCCEEDED"
  | "SUCCEEDED_WITH_WARNINGS"
  | "WAITING_APPROVAL"
  | "NEEDS_CONFIRMATION"
  | "FAILED"
  | "FAILED_ROUTE"
  | "FAILED_PROVIDER"
  | "FAILED_POLICY";

export type AgentSdkOutputKind =
  | "answer"
  | "draft"
  | "candidate-patch"
  | "workflow-report"
  | "confirmation"
  | "route-preview"
  | "none";

export type AgentSdkProviderRuntimeName =
  | "fake"
  | "deepseek-fixture"
  | "deepseek-real"
  | "claude-code-fixture"
  | "mimo-vllm-fixture"
  | "mimo-real"
  | "weak-relations-fixture"
  | "timeout-fixture"
  | "invalid-content-fixture";

export interface AgentSdkProviderRuntimeConfig {
  provider: AgentSdkProviderRuntimeName;
  timeoutMs: number;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  baseUrl?: string;
  apiKeyEnvName?: string;
}

export interface AgentSdkProviderRuntimeDependencies {
  env?: Record<string, string | undefined>;
  fetch?: typeof fetch;
}

export interface RunAgentRequest {
  workspaceRoot: string;
  message: string;
  runId?: string;
  methodologyId?: string;
  execute?: boolean;
  autoApprove?: boolean;
  mode?: AgentSdkRunMode;
  providerRuntime?: AgentSdkProviderRuntimeConfig;
  providerRuntimeDependencies?: AgentSdkProviderRuntimeDependencies;
}

export interface AgentSdkRunResult {
  schemaVersion: "agent-sdk-run.v1";
  runId: string;
  status: AgentSdkRunStatus;
  route: CommandRoute;
  capabilityId: string;
  outputKind: AgentSdkOutputKind;
  output?: {
    answer?: string;
    draftArtifact?: DraftArtifact;
    candidatePatch?: CandidatePatchProposal;
    workflow?: RunOrganizeResult;
    confirmation?: CommandConfirmation;
  };
  artifacts: {
    artifactRoot?: string;
    artifactPath?: string;
    reportPath?: string;
    tracePath?: string;
    rawProviderRefs: string[];
    wroteWorkspace: boolean;
    targetWorkspacePaths: string[];
  };
  diagnostics: {
    methodologyId: string;
    providerBacked: boolean;
    providerRuntime?: string;
    warnings: string[];
    error?: string;
  };
}

export interface KnowledgeWorkflowAgent {
  runAgent(request: RunAgentRequest): Promise<AgentSdkRunResult>;
  runOrganize(request: RunOrganizeRequest): Promise<RunOrganizeResult>;
  inspectRun(request: InspectRunRequest): Promise<InspectRunResult>;
  handleCommand(request: HandleCommandRequest): Promise<HandleCommandResult>;
  runOpenAgentTask(request: RunOpenAgentTaskRequest): Promise<RunOpenAgentTaskResult>;
  runOpenAgentGraph(request: RunOpenAgentGraphRequest): Promise<RunOpenAgentGraphResult>;
  runOpenAgentRealSmoke(request: {
    provider: OpenAgentRealSmokeProvider;
    workspaceRoot: string;
    env: Record<string, string | undefined>;
    executeReal: boolean;
    fetch?: typeof fetch;
  }): Promise<OpenAgentRealSmokeResult>;
}

function defaultRunId(): string {
  return `run-${Date.now()}`;
}

async function snapshotKnowledgeBase(workspaceRoot: string): Promise<Map<string, string>> {
  const root = path.join(workspaceRoot, "knowledge-base");
  const snapshot = new Map<string, string>();

  async function walk(directory: string): Promise<void> {
    const entries = await fs.readdir(directory, { withFileTypes: true }).catch((error: unknown) => {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") {
        return [];
      }
      throw error;
    });
    for (const entry of entries) {
      const absolutePath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        await walk(absolutePath);
      } else if (entry.isFile()) {
        const relativePath = path.relative(workspaceRoot, absolutePath).split(path.sep).join(path.posix.sep);
        snapshot.set(relativePath, await fs.readFile(absolutePath, "utf8"));
      }
    }
  }

  await walk(root);
  return snapshot;
}

function snapshotChanged(before: Map<string, string>, after: Map<string, string>): boolean {
  if (before.size !== after.size) {
    return true;
  }
  for (const [filePath, beforeContent] of before) {
    if (after.get(filePath) !== beforeContent) {
      return true;
    }
  }
  return false;
}

function toPublicRunStatus(status: GraphState["status"]): RunOrganizeResult["status"] {
  if (status === "WAITING_PLAN_APPROVAL" || status === "SUCCEEDED_WITH_WARNINGS" || status === "FAILED") {
    return status;
  }
  throw new Error(`Unexpected workflow terminal status: ${status}`);
}

function inferSdkOutputPolicy(message: string): OpenAgentOutputPolicy {
  if (/落库|写入|保存|publish|persist|write/i.test(message)) {
    return "CANDIDATE_PATCH";
  }
  if (/生成|草稿|清单|题目|问题|计划|draft|generate|list/i.test(message)) {
    return "DRAFT_ARTIFACT";
  }
  return "ANSWER_ONLY";
}

function readToolNames(): string[] {
  return internalTools
    .filter((tool) => tool.risk === "READ_ONLY" && tool.publicExposure === "SDK_ONLY")
    .map((tool) => tool.name);
}

function blockedToolNames(): string[] {
  return internalTools
    .filter((tool) => tool.risk === "WORKSPACE_WRITE" || tool.publicExposure === "INTERNAL_ONLY")
    .map((tool) => tool.name);
}

function forcedOpenAgentRoute(outputPolicy: OpenAgentOutputPolicy): CommandRoute {
  if (outputPolicy === "ANSWER_ONLY") {
    return {
      lane: "OPEN_AGENT_TASK",
      capabilityId: "agent.openTask",
      risk: "READ_ONLY",
      confidence: "MEDIUM",
      reason: "forced open-agent mode"
    };
  }
  return {
    lane: "OPEN_AGENT_TASK",
    capabilityId: "agent.draftArtifact",
    risk: "DRAFT_ONLY",
    confidence: "MEDIUM",
    reason: "forced open-agent mode"
  };
}

function statusFromOpenAgentTask(status: RunOpenAgentTaskResult["status"]): AgentSdkRunStatus {
  return status === "FAILED_POLICY" ? "FAILED_POLICY" : "SUCCEEDED";
}

function outputKindFromOpenAgentTask(result: RunOpenAgentTaskResult): AgentSdkOutputKind {
  if (result.candidatePatch) return "candidate-patch";
  if (result.draftArtifact) return "draft";
  if (result.answer) return "answer";
  return "none";
}

function normalizeOpenAgentTask(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
  result: RunOpenAgentTaskResult;
}): AgentSdkRunResult {
  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status: statusFromOpenAgentTask(input.result.status),
    route: input.route,
    capabilityId: input.route.capabilityId,
    outputKind: outputKindFromOpenAgentTask(input.result),
    output: {
      answer: input.result.answer,
      draftArtifact: input.result.draftArtifact,
      candidatePatch: input.result.candidatePatch
    },
    artifacts: {
      artifactRoot: input.result.artifactRoot,
      artifactPath: input.result.artifactPath,
      reportPath: input.result.artifactPath,
      rawProviderRefs: [],
      wroteWorkspace: false,
      targetWorkspacePaths: input.result.candidatePatch?.targetPaths ?? []
    },
    diagnostics: {
      methodologyId: input.methodologyId,
      providerBacked: false,
      warnings: []
    }
  };
}

function statusFromOpenAgentGraph(status: RunOpenAgentGraphResult["status"]): AgentSdkRunStatus {
  if (status === "SUCCEEDED") return "SUCCEEDED";
  if (status === "NEEDS_CONFIRMATION") return "NEEDS_CONFIRMATION";
  if (status === "FAILED_PROVIDER") return "FAILED_PROVIDER";
  return "FAILED";
}

function normalizeOpenAgentGraph(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
  providerRuntime?: string;
  result: RunOpenAgentGraphResult;
}): AgentSdkRunResult {
  const candidatePatch = input.result.candidatePatch;
  const outputKind: AgentSdkOutputKind = candidatePatch
    ? "candidate-patch"
    : input.result.draftArtifact
      ? "draft"
      : input.result.answer
        ? "answer"
        : input.result.confirmation
          ? "confirmation"
          : "none";
  const rawProviderRefs = input.result.rawProviderRefs.flatMap((ref) => [ref.requestPath, ref.responsePath]);

  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status: statusFromOpenAgentGraph(input.result.status),
    route: input.route,
    capabilityId: input.route.capabilityId,
    outputKind,
    output: {
      answer: input.result.answer,
      draftArtifact: input.result.draftArtifact,
      candidatePatch,
      confirmation: input.result.confirmation
    },
    artifacts: {
      artifactRoot: ".agent-runs/open-agent",
      artifactPath: input.result.artifactPath,
      reportPath: input.result.artifactPath,
      tracePath: input.result.tracePath,
      rawProviderRefs,
      wroteWorkspace: false,
      targetWorkspacePaths: candidatePatch?.targetPaths ?? []
    },
    diagnostics: {
      methodologyId: input.methodologyId,
      providerBacked: input.result.synthesis?.providerBacked ?? input.result.providerCalls > 0,
      providerRuntime: input.providerRuntime,
      warnings: [],
      error:
        input.result.status === "SUCCEEDED" || input.result.status === "NEEDS_CONFIRMATION"
          ? undefined
          : `open-agent graph ended with ${input.result.status}`
    }
  };
}

function normalizeConfirmation(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
  confirmation: CommandConfirmation;
}): AgentSdkRunResult {
  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status: "NEEDS_CONFIRMATION",
    route: input.route,
    capabilityId: input.route.capabilityId,
    outputKind: "confirmation",
    output: { confirmation: input.confirmation },
    artifacts: {
      rawProviderRefs: [],
      wroteWorkspace: false,
      targetWorkspacePaths: []
    },
    diagnostics: {
      methodologyId: input.methodologyId,
      providerBacked: false,
      warnings: []
    }
  };
}

function normalizeRoutePreview(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
}): AgentSdkRunResult {
  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status: "WAITING_APPROVAL",
    route: input.route,
    capabilityId: input.route.capabilityId,
    outputKind: "route-preview",
    artifacts: {
      rawProviderRefs: [],
      wroteWorkspace: false,
      targetWorkspacePaths: []
    },
    diagnostics: {
      methodologyId: input.methodologyId,
      providerBacked: false,
      warnings: []
    }
  };
}

function normalizeWorkflow(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
  workspaceWriteAllowed: boolean;
  result: RunOrganizeResult;
}): AgentSdkRunResult {
  const status =
    input.result.status === "WAITING_PLAN_APPROVAL"
      ? "WAITING_APPROVAL"
      : input.result.status === "FAILED"
        ? "FAILED"
        : "SUCCEEDED_WITH_WARNINGS";
  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status,
    route: input.route,
    capabilityId: input.route.capabilityId,
    outputKind: "workflow-report",
    output: { workflow: input.result },
    artifacts: {
      artifactRoot: input.result.artifactRoot,
      reportPath: input.result.reportPath,
      rawProviderRefs: [],
      wroteWorkspace: input.workspaceWriteAllowed && input.result.status === "SUCCEEDED_WITH_WARNINGS",
      targetWorkspacePaths: []
    },
    diagnostics: {
      methodologyId: input.methodologyId,
      providerBacked: false,
      warnings: input.result.status === "SUCCEEDED_WITH_WARNINGS" ? ["workflow completed with warnings"] : [],
      error: input.result.lastError
    }
  };
}

function normalizeFailedRoute(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
  reason: string;
}): AgentSdkRunResult {
  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status: "FAILED_ROUTE",
    route: input.route,
    capabilityId: input.route.capabilityId,
    outputKind: "none",
    artifacts: {
      rawProviderRefs: [],
      wroteWorkspace: false,
      targetWorkspacePaths: []
    },
    diagnostics: {
      methodologyId: input.methodologyId,
      providerBacked: false,
      warnings: [],
      error: input.reason
    }
  };
}

export function createKnowledgeWorkflowAgent(config: KnowledgeWorkflowAgentConfig = {}): KnowledgeWorkflowAgent {
  return {
    runAgent: (request) =>
      runAgent({
        ...request,
        methodologyId: request.methodologyId ?? config.defaultMethodologyId
      }),
    runOrganize: (request) =>
      runOrganize({
        ...request,
        methodologyId: request.methodologyId ?? config.defaultMethodologyId
      }),
    inspectRun,
    handleCommand: (request) =>
      handleCommand({
        ...request,
        methodologyId: request.methodologyId ?? config.defaultMethodologyId
      }),
    runOpenAgentTask: (request) =>
      runOpenAgentTask({
        ...request,
        methodologyId: request.methodologyId ?? config.defaultMethodologyId
      }),
    runOpenAgentGraph: (request) =>
      runOpenAgentGraph({
        ...request,
        methodologyId: request.methodologyId ?? config.defaultMethodologyId
      }),
    runOpenAgentRealSmoke
  };
}

export async function runAgent(request: RunAgentRequest): Promise<AgentSdkRunResult> {
  const runId = assertSafeArtifactSlug(request.runId ?? defaultRunId());
  const methodologyId = request.methodologyId ?? "lmwiki-v1";
  const mode = request.mode ?? "auto";

  if (mode === "deterministic-open-agent") {
    const outputPolicy = inferSdkOutputPolicy(request.message);
    const result = await runOpenAgentTask({
      workspaceRoot: request.workspaceRoot,
      taskId: runId,
      methodologyId,
      objective: request.message,
      risk: outputPolicy === "ANSWER_ONLY" ? "READ_ONLY" : "DRAFT_ONLY",
      outputPolicy,
      allowedToolNames: readToolNames(),
      blockedToolNames: blockedToolNames()
    });
    return normalizeOpenAgentTask({
      runId,
      route: forcedOpenAgentRoute(outputPolicy),
      methodologyId,
      result
    });
  }

  if (mode === "llm-open-agent") {
    const result = await runOpenAgentGraph({
      workspaceRoot: request.workspaceRoot,
      taskId: runId,
      message: request.message,
      methodologyId,
      outputPolicy: inferSdkOutputPolicy(request.message),
      allowedToolNames: readToolNames(),
      blockedToolNames: blockedToolNames(),
      providerRuntime: request.providerRuntime,
      providerRuntimeDependencies: request.providerRuntimeDependencies
    });
    return normalizeOpenAgentGraph({
      runId,
      route: result.route,
      methodologyId,
      providerRuntime: request.providerRuntime?.provider,
      result
    });
  }

  if (mode === "fixed-workflow") {
    const route = classifyCommand({ message: request.message });
    if (route.lane !== "FIXED_WORKFLOW") {
      return normalizeFailedRoute({
        runId,
        route,
        methodologyId,
        reason: "fixed-workflow mode requires a fixed workflow command"
      });
    }
  }

  const shouldSnapshotWorkspace = (mode === "auto" || mode === "fixed-workflow") && request.execute !== false;
  const beforeKnowledgeBase = shouldSnapshotWorkspace ? await snapshotKnowledgeBase(request.workspaceRoot) : undefined;
  const routed = await handleCommand({
    workspaceRoot: request.workspaceRoot,
    message: request.message,
    runId,
    methodologyId,
    autoApprove: request.autoApprove,
    execute: request.execute ?? true,
    providerRuntime: request.providerRuntime,
    providerRuntimeDependencies: request.providerRuntimeDependencies
  });
  const wroteKnowledgeBase =
    beforeKnowledgeBase && routed.workflow
      ? snapshotChanged(beforeKnowledgeBase, await snapshotKnowledgeBase(request.workspaceRoot))
      : false;

  if (routed.openAgent) {
    return normalizeOpenAgentTask({
      runId,
      route: routed.route,
      methodologyId,
      result: routed.openAgent
    });
  }

  if (routed.openAgentGraph) {
    return normalizeOpenAgentGraph({
      runId,
      route: routed.route,
      methodologyId,
      providerRuntime: request.providerRuntime?.provider,
      result: routed.openAgentGraph
    });
  }

  if (routed.confirmation) {
    return normalizeConfirmation({
      runId,
      route: routed.route,
      methodologyId,
      confirmation: routed.confirmation
    });
  }

  if (routed.workflow) {
    return normalizeWorkflow({
      runId,
      route: routed.route,
      methodologyId,
      workspaceWriteAllowed: wroteKnowledgeBase,
      result: routed.workflow
    });
  }

  if (routed.route.lane === "FIXED_WORKFLOW" && request.execute === false) {
    return normalizeRoutePreview({
      runId,
      route: routed.route,
      methodologyId
    });
  }

  return normalizeFailedRoute({
    runId,
    route: routed.route,
    methodologyId,
    reason: `runAgent could not normalize route ${routed.route.lane}`
  });
}

export async function runOrganize(request: RunOrganizeRequest): Promise<RunOrganizeResult> {
  const runId = assertSafeArtifactSlug(request.runId ?? defaultRunId());
  const methodologyId = request.methodologyId ?? "lmwiki-v1";
  const state = await runOrganizeWorkflow({
    workspaceRoot: request.workspaceRoot,
    instruction: request.instruction,
    runId,
    methodologyId,
    autoApprove: request.autoApprove,
    providerRuntime: request.providerRuntime,
    providerRuntimeDependencies: request.providerRuntimeDependencies
  });

  return {
    runId: state.runId,
    status: toPublicRunStatus(state.status),
    methodologyId: state.methodologyId,
    planPath: state.planPath,
    reportPath: state.reportPath,
    lastError: state.lastError,
    artifactRoot: path.posix.join(".agent-runs", state.runId)
  };
}

export { inspectRun };
export type { InspectRunRequest, InspectRunResult };
export { runOpenAgentTask };
export type { RunOpenAgentTaskRequest, RunOpenAgentTaskResult };
export { runOpenAgentGraph };
export type { RunOpenAgentGraphRequest, RunOpenAgentGraphResult };
export { runOpenAgentRealSmoke };
export { inspectOpenAgentRealSmoke };
export type { OpenAgentRealSmokeProvider, OpenAgentRealSmokeResult };
