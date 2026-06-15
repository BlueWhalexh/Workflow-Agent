import { Annotation, END, START, StateGraph } from "@langchain/langgraph";
import { assertSafeArtifactSlug } from "../../storage/artifact-slug.js";
import type { CommandRoute } from "../../sdk/command-router.js";
import type {
  CandidatePatchProposal,
  DraftArtifact,
  OpenAgentOutputPolicy,
  OpenAgentToolCall
} from "../../sdk/open-agent-runtime.js";
import type { ProviderRuntimeDependencies } from "../provider/provider-registry.js";
import { normalizeProviderRuntimeConfig, type ProviderRuntimeConfig } from "../provider/provider-runtime-config.js";
import { ProviderRuntimeError } from "../../domain/llm-provider/provider-error.js";
import {
  openAgentGraphArtifactPath,
  openAgentGraphTracePath,
  writeOpenAgentRawProviderArtifacts
} from "./open-agent-artifacts.js";
import { selectOpenAgentProvider } from "./open-agent-provider.js";
import { runArtifactNode } from "./nodes/artifact-node.js";
import { runContextGatherNode } from "./nodes/context-gather-node.js";
import { runPlanNode } from "./nodes/plan-node.js";
import { runPolicyGateNode } from "./nodes/policy-gate-node.js";
import { runSelfCheckNode } from "./nodes/self-check-node.js";
import { runSynthesizeNode } from "./nodes/synthesize-node.js";
import { runToolLoopNode } from "./nodes/tool-loop-node.js";
import {
  DEFAULT_OPEN_AGENT_LOOP_BUDGET,
  type OpenAgentGraphState,
  type OpenAgentGraphStatus,
  type OpenAgentGraphStep,
  type OpenAgentContextDigestEntry,
  type OpenAgentLoopBudget,
  type OpenAgentLoopTraceEvent,
  type OpenAgentProvider,
  type OpenAgentRawProviderRef,
  type OpenAgentSynthesisMetadata
} from "./open-agent-state.js";

const OpenAgentGraphAnnotation = Annotation.Root({
  workspaceRoot: Annotation<string>,
  taskId: Annotation<string>,
  message: Annotation<string>,
  methodologyId: Annotation<string>,
  route: Annotation<OpenAgentGraphState["route"]>,
  status: Annotation<OpenAgentGraphState["status"]>,
  outputPolicy: Annotation<OpenAgentGraphState["outputPolicy"]>,
  allowedToolNames: Annotation<string[]>,
  blockedToolNames: Annotation<string[]>,
  loopBudget: Annotation<OpenAgentGraphState["loopBudget"]>,
  steps: Annotation<OpenAgentGraphState["steps"]>,
  toolCalls: Annotation<OpenAgentGraphState["toolCalls"]>,
  traceEvents: Annotation<OpenAgentGraphState["traceEvents"]>,
  groundingRefs: Annotation<string[]>,
  contextDigest: Annotation<OpenAgentGraphState["contextDigest"]>,
  rawFiles: Annotation<string[]>,
  knowledgePages: Annotation<string[]>,
  providerCalls: Annotation<number>,
  realExternalCall: Annotation<boolean>,
  rawProviderRefs: Annotation<OpenAgentGraphState["rawProviderRefs"]>,
  loopIterations: Annotation<number>,
  runner: Annotation<OpenAgentGraphState["runner"]>,
  synthesis: Annotation<OpenAgentGraphState["synthesis"] | undefined>,
  plan: Annotation<OpenAgentGraphState["plan"] | undefined>,
  answer: Annotation<string | undefined>,
  draftArtifact: Annotation<OpenAgentGraphState["draftArtifact"] | undefined>,
  candidatePatch: Annotation<OpenAgentGraphState["candidatePatch"] | undefined>,
  confirmation: Annotation<OpenAgentGraphState["confirmation"] | undefined>,
  artifactPath: Annotation<string>,
  tracePath: Annotation<string>
});

export interface RunOpenAgentGraphRequest {
  workspaceRoot: string;
  taskId?: string;
  message: string;
  methodologyId?: string;
  outputPolicy?: OpenAgentOutputPolicy;
  allowedToolNames?: string[];
  blockedToolNames?: string[];
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
  openAgentProvider?: OpenAgentProvider;
  loopBudget?: OpenAgentLoopBudget;
}

export interface RunOpenAgentGraphResult {
  taskId: string;
  status: Exclude<OpenAgentGraphStatus, "RUNNING">;
  route: CommandRoute;
  outputPolicy: OpenAgentOutputPolicy;
  answer?: string;
  draftArtifact?: DraftArtifact;
  candidatePatch?: CandidatePatchProposal;
  confirmation?: OpenAgentGraphState["confirmation"];
  artifactPath: string;
  tracePath: string;
  providerCalls: number;
  realExternalCall: boolean;
  rawProviderRefs: OpenAgentRawProviderRef[];
  synthesis?: OpenAgentSynthesisMetadata;
  loopIterations: number;
  groundingRefs: string[];
  contextDigest: OpenAgentContextDigestEntry[];
  toolCalls: OpenAgentToolCall[];
  traceEvents: OpenAgentLoopTraceEvent[];
  steps: OpenAgentGraphStep[];
}

function defaultTaskId(): string {
  return `open-agent-graph-${Date.now()}`;
}

function routeForMessage(message: string, outputPolicy: OpenAgentOutputPolicy): CommandRoute {
  return {
    lane: "OPEN_AGENT_TASK",
    capabilityId: outputPolicy === "DRAFT_ARTIFACT" || outputPolicy === "CANDIDATE_PATCH" ? "agent.draftArtifact" : "agent.openTask",
    risk: outputPolicy === "ANSWER_ONLY" ? "READ_ONLY" : "DRAFT_ONLY",
    confidence: "MEDIUM",
    reason: "message is handled by the LLM-backed open agent graph"
  };
}

function inferOutputPolicy(message: string): OpenAgentOutputPolicy {
  if (/落库|写入|保存|publish|persist|write/i.test(message)) {
    return "CANDIDATE_PATCH";
  }
  if (/生成|草稿|清单|题目|问题|计划|draft|generate|list/i.test(message)) {
    return "DRAFT_ARTIFACT";
  }
  return "ANSWER_ONLY";
}

function toResult(state: OpenAgentGraphState): RunOpenAgentGraphResult {
  const status = state.status === "RUNNING" ? "SUCCEEDED" : state.status;
  return {
    taskId: state.taskId,
    status,
    route: state.route,
    outputPolicy: state.outputPolicy,
    answer: state.answer,
    draftArtifact: state.draftArtifact,
    candidatePatch: state.candidatePatch,
    confirmation: state.confirmation,
    artifactPath: state.artifactPath,
    tracePath: state.tracePath,
    providerCalls: state.providerCalls,
    realExternalCall: state.realExternalCall,
    rawProviderRefs: state.rawProviderRefs,
    synthesis: state.synthesis,
    loopIterations: state.loopIterations,
    groundingRefs: state.groundingRefs,
    contextDigest: state.contextDigest,
    toolCalls: state.toolCalls,
    traceEvents: state.traceEvents,
    steps: state.steps
  };
}

function runnerNode(state: OpenAgentGraphState): OpenAgentGraphState {
  state.steps.push({
    name: "RUNNER",
    status: "SUCCEEDED",
    summary: "Open agent graph invoked through LangGraph StateGraph."
  });
  return state;
}

function finalizeStatusNode(state: OpenAgentGraphState): OpenAgentGraphState {
  if (state.status === "RUNNING") {
    state.status = "SUCCEEDED";
  }
  return state;
}

function routeRunningOrArtifact(state: OpenAgentGraphState): "planStep" | "artifact" {
  return state.status === "RUNNING" ? "planStep" : "artifact";
}

function routeAfterPlan(state: OpenAgentGraphState): "contextGather" | "artifact" {
  return state.status === "RUNNING" ? "contextGather" : "artifact";
}

function routeAfterToolLoop(state: OpenAgentGraphState): "synthesize" | "artifact" {
  if (state.status !== "RUNNING" && state.status !== "NEEDS_CONFIRMATION") {
    return "artifact";
  }
  return "synthesize";
}

function routeAfterSynthesize(state: OpenAgentGraphState): "selfCheck" | "artifact" {
  return state.status === "RUNNING" ? "selfCheck" : "artifact";
}

export function compileOpenAgentStateGraph(input: { provider: OpenAgentProvider }) {
  return new StateGraph(OpenAgentGraphAnnotation)
    .addNode("runnerStart", runnerNode)
    .addNode("policyGate", runPolicyGateNode)
    .addNode("planStep", (state) => runPlanNode(state, input.provider))
    .addNode("contextGather", runContextGatherNode)
    .addNode("toolLoop", (state) => runToolLoopNode(state, input.provider))
    .addNode("synthesize", (state) => runSynthesizeNode(state, input.provider))
    .addNode("selfCheck", runSelfCheckNode)
    .addNode("finalizeStatus", finalizeStatusNode)
    .addNode("artifact", runArtifactNode)
    .addEdge(START, "runnerStart")
    .addEdge("runnerStart", "policyGate")
    .addConditionalEdges("policyGate", routeRunningOrArtifact)
    .addConditionalEdges("planStep", routeAfterPlan)
    .addEdge("contextGather", "toolLoop")
    .addConditionalEdges("toolLoop", routeAfterToolLoop)
    .addConditionalEdges("synthesize", routeAfterSynthesize)
    .addEdge("selfCheck", "finalizeStatus")
    .addEdge("finalizeStatus", "artifact")
    .addEdge("artifact", END)
    .compile();
}

export async function runOpenAgentGraph(request: RunOpenAgentGraphRequest): Promise<RunOpenAgentGraphResult> {
  const taskId = assertSafeArtifactSlug(request.taskId ?? defaultTaskId());
  const outputPolicy = request.outputPolicy ?? inferOutputPolicy(request.message);
  const providerRuntime = normalizeProviderRuntimeConfig(request.providerRuntime);
  const state: OpenAgentGraphState = {
    workspaceRoot: request.workspaceRoot,
    taskId,
    message: request.message,
    methodologyId: request.methodologyId ?? "lmwiki-v1",
    route: routeForMessage(request.message, outputPolicy),
    status: "RUNNING",
    outputPolicy,
    allowedToolNames: request.allowedToolNames ?? ["workspace.scan", "artifact.readEval", "patch.validate"],
    blockedToolNames: request.blockedToolNames ?? ["patch.publish"],
    loopBudget: request.loopBudget ?? DEFAULT_OPEN_AGENT_LOOP_BUDGET,
    steps: [],
    toolCalls: [],
    traceEvents: [],
    groundingRefs: [],
    contextDigest: [],
    rawFiles: [],
    knowledgePages: [],
    providerCalls: 0,
    realExternalCall: providerRuntime.provider === "mimo-real" || providerRuntime.provider === "deepseek-real",
    rawProviderRefs: [],
    loopIterations: 0,
    runner: {
      kind: "LANGGRAPH_STATEGRAPH",
      version: 1
    },
    artifactPath: openAgentGraphArtifactPath(taskId),
    tracePath: openAgentGraphTracePath(taskId)
  };

  let provider: OpenAgentProvider;
  try {
    provider =
      request.openAgentProvider ??
      selectOpenAgentProvider({
        config: providerRuntime,
        dependencies: request.providerRuntimeDependencies,
        onRawEnvelope: async ({ providerCallId, envelope }) => {
          const refs = await writeOpenAgentRawProviderArtifacts({
            workspaceRoot: state.workspaceRoot,
            taskId: state.taskId,
            providerCallId,
            envelope
          });
          state.rawProviderRefs.push({
            providerCallId,
            ...refs
          });
        }
      });
  } catch (error) {
    if (!(error instanceof ProviderRuntimeError)) {
      throw error;
    }
    state.status = "FAILED_PROVIDER";
    state.steps.push({
      name: "RUNNER",
      status: "FAILED",
      summary: error.message
    });
    await runArtifactNode(state);
    return toResult(state);
  }
  const compiled = compileOpenAgentStateGraph({ provider });
  const finalState = (await compiled.invoke(state)) as OpenAgentGraphState;
  return toResult(finalState);
}
