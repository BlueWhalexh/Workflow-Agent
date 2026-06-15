import type { CommandConfirmation, CommandRoute } from "../../sdk/command-router.js";
import type {
  CandidatePatchProposal,
  DraftArtifact,
  OpenAgentOutputPolicy,
  OpenAgentToolCall
} from "../../sdk/open-agent-runtime.js";

export type OpenAgentGraphStatus =
  | "RUNNING"
  | "SUCCEEDED"
  | "NEEDS_CONFIRMATION"
  | "FAILED_POLICY"
  | "FAILED_PROVIDER"
  | "FAILED_VALIDATION"
  | "FAILED_BUDGET";

export interface OpenAgentLoopBudget {
  maxIterations: number;
  maxToolCalls: number;
  timeoutMs: number;
}

export interface OpenAgentGraphStep {
  name:
    | "RUNNER"
    | "POLICY_GATE"
    | "PLAN"
    | "CONTEXT_GATHER"
    | "TOOL_LOOP"
    | "SYNTHESIZE"
    | "SELF_CHECK"
    | "ARTIFACT"
    | "HANDOFF";
  status: "SUCCEEDED" | "FAILED";
  summary: string;
}

export interface OpenAgentLoopTraceEvent {
  iteration: number;
  action: "READ_CONTEXT" | "SOLVED" | "REQUEST_WRITE_CONFIRMATION";
  toolName?: string;
  observationRef?: string;
  status: "SUCCEEDED" | "FAILED";
  summary: string;
}

export interface OpenAgentPlan {
  objective: string;
  outputPolicy: OpenAgentOutputPolicy;
  steps: string[];
  contextHints: string[];
}

export interface OpenAgentNextAction {
  action: "READ_CONTEXT" | "SOLVED" | "REQUEST_WRITE_CONFIRMATION";
  toolName?: string;
  summary: string;
}

export interface OpenAgentSynthesisInput {
  objective: string;
  outputPolicy: OpenAgentOutputPolicy;
  methodologyId: string;
  groundingRefs: string[];
  contextDigest: Array<{
    path: string;
    excerpt: string;
  }>;
}

export interface OpenAgentContextDigestEntry {
  path: string;
  excerpt: string;
}

export type OpenAgentSynthesisOutput =
  | { kind: "ANSWER"; answer: string; groundingRefs: string[] }
  | { kind: "DRAFT_ARTIFACT"; title: string; content: string; groundingRefs: string[] }
  | { kind: "CANDIDATE_PATCH"; title: string; content: string; targetPath: string; groundingRefs: string[] };

export interface OpenAgentProvider {
  plan(input: { objective: string; outputPolicy: OpenAgentOutputPolicy }): Promise<OpenAgentPlan | string>;
  nextAction?(input: { iteration: number; plan: OpenAgentPlan; groundingRefs: string[] }): Promise<OpenAgentNextAction>;
  synthesize?(input: OpenAgentSynthesisInput): Promise<OpenAgentSynthesisOutput | string>;
}

export interface OpenAgentRawProviderRef {
  providerCallId: string;
  requestPath: string;
  responsePath: string;
}

export interface OpenAgentSynthesisMetadata {
  providerBacked: boolean;
  providerCallId?: string;
  outputKind: "ANSWER" | "DRAFT_ARTIFACT" | "CANDIDATE_PATCH";
  groundingRefs: string[];
}

export interface OpenAgentRunnerMetadata {
  kind: "LANGGRAPH_STATEGRAPH";
  version: 1;
}

export interface OpenAgentGraphState {
  workspaceRoot: string;
  taskId: string;
  message: string;
  methodologyId: string;
  route: CommandRoute;
  status: OpenAgentGraphStatus;
  outputPolicy: OpenAgentOutputPolicy;
  allowedToolNames: string[];
  blockedToolNames: string[];
  loopBudget: OpenAgentLoopBudget;
  steps: OpenAgentGraphStep[];
  toolCalls: OpenAgentToolCall[];
  traceEvents: OpenAgentLoopTraceEvent[];
  groundingRefs: string[];
  contextDigest: OpenAgentContextDigestEntry[];
  rawFiles: string[];
  knowledgePages: string[];
  providerCalls: number;
  realExternalCall: boolean;
  rawProviderRefs: OpenAgentRawProviderRef[];
  loopIterations: number;
  runner: OpenAgentRunnerMetadata;
  synthesis?: OpenAgentSynthesisMetadata;
  plan?: OpenAgentPlan;
  answer?: string;
  draftArtifact?: DraftArtifact;
  candidatePatch?: CandidatePatchProposal;
  confirmation?: CommandConfirmation;
  artifactPath: string;
  tracePath: string;
}

export const DEFAULT_OPEN_AGENT_LOOP_BUDGET: OpenAgentLoopBudget = {
  maxIterations: 3,
  maxToolCalls: 8,
  timeoutMs: 30000
};
