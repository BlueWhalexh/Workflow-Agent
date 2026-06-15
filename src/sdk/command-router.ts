import type { ProviderRuntimeDependencies } from "../runtime/provider/provider-registry.js";
import type { ProviderRuntimeConfig } from "../runtime/provider/provider-runtime-config.js";
import { runOpenAgentGraph, type RunOpenAgentGraphResult } from "../runtime/open-agent/open-agent-graph.js";
import { getKnowledgeMethodology } from "../domain/methodology/knowledge-methodology.js";
import { internalTools } from "../tools/internal-tool-registry.js";
import { runOrganize, type RunOrganizeResult } from "./knowledge-workflow-agent.js";
import {
  runOpenAgentTask,
  type FixedWorkflowHandoff,
  type OpenAgentOutputPolicy,
  type RunOpenAgentTaskResult
} from "./open-agent-runtime.js";

export type ExecutionLane = "FIXED_WORKFLOW" | "OPEN_AGENT_TASK" | "CONFIRMATION_REQUIRED";
export type CommandRisk = "READ_ONLY" | "DRAFT_ONLY" | "WORKSPACE_WRITE";
export type RouteConfidence = "HIGH" | "MEDIUM" | "LOW";

export interface CommandRoute {
  lane: ExecutionLane;
  capabilityId: string;
  risk: CommandRisk;
  confidence: RouteConfidence;
  reason: string;
}

export interface OpenAgentTaskEnvelope {
  objective: string;
  risk: "READ_ONLY" | "DRAFT_ONLY";
  outputPolicy: OpenAgentOutputPolicy;
  allowedToolNames: string[];
  blockedToolNames: string[];
}

export interface CommandConfirmation {
  required: boolean;
  questions: string[];
  handoff: FixedWorkflowHandoff;
}

export interface HandleCommandRequest {
  workspaceRoot: string;
  message: string;
  runId?: string;
  methodologyId?: string;
  autoApprove?: boolean;
  execute?: boolean;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
  openAgentMode?: "deterministic" | "llm-graph";
}

export interface HandleCommandResult {
  route: CommandRoute;
  workflow?: RunOrganizeResult;
  agentTask?: OpenAgentTaskEnvelope;
  openAgent?: RunOpenAgentTaskResult;
  openAgentGraph?: RunOpenAgentGraphResult;
  confirmation?: CommandConfirmation;
}

const fixedOrganizePatterns = [
  /整理(全部|整个|全量)?知识库/i,
  /organize\s+(the\s+)?(whole\s+)?(workspace|knowledge\s*base)/i
];

const workspaceWritePatterns = [/落库/, /保存/, /写入/, /发布/, /导入/, /persist/i, /publish/i, /write/i, /import/i];
const draftPatterns = [/生成/, /草稿/, /清单/, /题目/, /问题/, /计划/, /draft/i, /generate/i, /list/i];

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

function includesAny(message: string, patterns: RegExp[]): boolean {
  return patterns.some((pattern) => pattern.test(message));
}

export function classifyCommand(input: { message: string }): CommandRoute {
  const message = input.message.trim();
  if (includesAny(message, fixedOrganizePatterns)) {
    return {
      lane: "FIXED_WORKFLOW",
      capabilityId: "workflow.organizeWorkspace",
      risk: "WORKSPACE_WRITE",
      confidence: "HIGH",
      reason: "message explicitly requests organizing the whole knowledge workspace"
    };
  }

  if (includesAny(message, workspaceWritePatterns)) {
    return {
      lane: "CONFIRMATION_REQUIRED",
      capabilityId: "confirmation.workspaceWrite",
      risk: "WORKSPACE_WRITE",
      confidence: "MEDIUM",
      reason: "message implies a workspace write but lacks a confirmed fixed workflow scope"
    };
  }

  if (includesAny(message, draftPatterns)) {
    return {
      lane: "OPEN_AGENT_TASK",
      capabilityId: "agent.draftArtifact",
      risk: "DRAFT_ONLY",
      confidence: "MEDIUM",
      reason: "message asks for generated output without confirmed workspace write"
    };
  }

  return {
    lane: "OPEN_AGENT_TASK",
    capabilityId: "agent.openTask",
    risk: "READ_ONLY",
    confidence: "LOW",
    reason: "message does not match a fixed workflow and is handled as an open agent task"
  };
}

function buildAgentTask(request: HandleCommandRequest, route: CommandRoute): OpenAgentTaskEnvelope {
  const risk = route.risk === "DRAFT_ONLY" ? "DRAFT_ONLY" : "READ_ONLY";
  return {
    objective: request.message,
    risk,
    outputPolicy: risk === "DRAFT_ONLY" ? "DRAFT_ARTIFACT" : "ANSWER_ONLY",
    allowedToolNames: readToolNames(),
    blockedToolNames: blockedToolNames()
  };
}

function buildConfirmation(input: { methodologyId: string; instruction: string }): CommandConfirmation {
  return {
    required: true,
    questions: ["请确认要写入的对象、范围和目标 methodology。"],
    handoff: {
      type: "FIXED_WORKFLOW",
      capabilityId: "workflow.organizeWorkspace",
      executeRequired: true,
      confirmationRequired: true,
      methodologyId: input.methodologyId,
      instruction: input.instruction
    }
  };
}

export async function handleCommand(request: HandleCommandRequest): Promise<HandleCommandResult> {
  const methodology = getKnowledgeMethodology(request.methodologyId);
  const route = classifyCommand({ message: request.message });

  if (route.lane === "FIXED_WORKFLOW") {
    if (!request.execute) {
      return { route };
    }
    return {
      route,
      workflow: await runOrganize({
        workspaceRoot: request.workspaceRoot,
        instruction: request.message,
        runId: request.runId,
        methodologyId: methodology.id,
        autoApprove: request.autoApprove ?? false,
        providerRuntime: request.providerRuntime,
        providerRuntimeDependencies: request.providerRuntimeDependencies
      })
    };
  }

  if (route.lane === "CONFIRMATION_REQUIRED") {
    return {
      route,
      confirmation: buildConfirmation({ methodologyId: methodology.id, instruction: request.message })
    };
  }

  const agentTask = buildAgentTask(request, route);
  if (request.openAgentMode === "llm-graph") {
    return {
      route,
      agentTask,
      openAgentGraph: await runOpenAgentGraph({
        workspaceRoot: request.workspaceRoot,
        taskId: request.runId,
        message: request.message,
        methodologyId: methodology.id,
        outputPolicy: agentTask.outputPolicy,
        allowedToolNames: agentTask.allowedToolNames,
        blockedToolNames: agentTask.blockedToolNames,
        providerRuntime: request.providerRuntime,
        providerRuntimeDependencies: request.providerRuntimeDependencies
      })
    };
  }

  return {
    route,
    agentTask,
    openAgent: await runOpenAgentTask({
      workspaceRoot: request.workspaceRoot,
      taskId: request.runId,
      methodologyId: methodology.id,
      objective: request.message,
      risk: route.risk === "DRAFT_ONLY" ? "DRAFT_ONLY" : "READ_ONLY",
      outputPolicy: route.risk === "DRAFT_ONLY" ? "DRAFT_ARTIFACT" : "ANSWER_ONLY",
      allowedToolNames: agentTask.allowedToolNames,
      blockedToolNames: agentTask.blockedToolNames
    })
  };
}
