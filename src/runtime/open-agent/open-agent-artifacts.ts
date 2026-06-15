import { promises as fs } from "node:fs";
import path from "node:path";
import { redactProviderEnvelope } from "../../domain/llm-provider/redaction.js";
import { stableJson } from "../../storage/json-schema.js";
import type { OpenAgentGraphState } from "./open-agent-state.js";

export function openAgentGraphArtifactPath(taskId: string): string {
  return path.posix.join(".agent-runs", "open-agent", `${taskId}.json`);
}

export function openAgentGraphTracePath(taskId: string): string {
  return path.posix.join(".agent-runs", "open-agent", "traces", `${taskId}.jsonl`);
}

export function openAgentRawProviderRequestPath(taskId: string, providerCallId: string): string {
  return path.posix.join(".agent-runs", "open-agent", "raw-provider", taskId, providerCallId, "request.json");
}

export function openAgentRawProviderResponsePath(taskId: string, providerCallId: string): string {
  return path.posix.join(".agent-runs", "open-agent", "raw-provider", taskId, providerCallId, "response.json");
}

export async function writeOpenAgentRawProviderArtifacts(input: {
  workspaceRoot: string;
  taskId: string;
  providerCallId: string;
  envelope: {
    request?: unknown;
    response?: unknown;
  };
}): Promise<{ requestPath: string; responsePath: string }> {
  const requestPath = openAgentRawProviderRequestPath(input.taskId, input.providerCallId);
  const responsePath = openAgentRawProviderResponsePath(input.taskId, input.providerCallId);
  const absoluteRequestPath = path.join(input.workspaceRoot, requestPath);
  const absoluteResponsePath = path.join(input.workspaceRoot, responsePath);
  await fs.mkdir(path.dirname(absoluteRequestPath), { recursive: true });
  await fs.writeFile(absoluteRequestPath, stableJson(redactProviderEnvelope(input.envelope.request ?? null)), "utf8");
  await fs.writeFile(absoluteResponsePath, stableJson(redactProviderEnvelope(input.envelope.response ?? null)), "utf8");
  return { requestPath, responsePath };
}

export async function writeOpenAgentGraphArtifacts(state: OpenAgentGraphState): Promise<void> {
  const reportPath = path.join(state.workspaceRoot, state.artifactPath);
  const tracePath = path.join(state.workspaceRoot, state.tracePath);
  await fs.mkdir(path.dirname(reportPath), { recursive: true });
  await fs.mkdir(path.dirname(tracePath), { recursive: true });
  await fs.writeFile(reportPath, stableJson(toOpenAgentGraphReport(state)), "utf8");
  await fs.writeFile(
    tracePath,
    state.traceEvents.map((event) => stableJson(event).trimEnd()).join("\n") + "\n",
    "utf8"
  );
}

export function toOpenAgentGraphReport(state: OpenAgentGraphState): Record<string, unknown> {
  return {
    schemaVersion: "open-agent-graph.v1",
    taskId: state.taskId,
    status: state.status,
    route: state.route,
    runner: state.runner,
    methodologyId: state.methodologyId,
    message: state.message,
    outputPolicy: state.outputPolicy,
    loopBudget: state.loopBudget,
    steps: state.steps,
    toolCalls: state.toolCalls,
    traceEvents: state.traceEvents,
    groundingRefs: state.groundingRefs,
    contextDigest: state.contextDigest,
    rawFiles: state.rawFiles,
    knowledgePages: state.knowledgePages,
    providerCalls: state.providerCalls,
    realExternalCall: state.realExternalCall,
    rawProviderRefs: state.rawProviderRefs,
    synthesis: state.synthesis,
    loopIterations: state.loopIterations,
    answer: state.answer,
    draftArtifact: state.draftArtifact,
    candidatePatch: state.candidatePatch,
    confirmation: state.confirmation,
    artifactPath: state.artifactPath,
    tracePath: state.tracePath
  };
}
