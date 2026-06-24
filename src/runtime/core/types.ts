import { z } from "zod";

export const AgentRuntimeNameSchema = z.enum(["native-loop", "claude-agent-sdk"]);
export type AgentRuntimeName = z.infer<typeof AgentRuntimeNameSchema>;

export const ExecutionEvidenceSchema = z.enum(["mock", "recorded", "live"]);
export type ExecutionEvidence = z.infer<typeof ExecutionEvidenceSchema>;

export const AgentModelSelectionSchema = z.object({
  provider: z.string().min(1),
  model: z.string().min(1)
}).strict();
export type AgentModelSelection = z.infer<typeof AgentModelSelectionSchema>;

export const AgentRunBudgetSchema = z.object({
  maxModelCalls: z.number().int().positive(),
  maxToolCalls: z.number().int().positive(),
  wallClockMs: z.number().int().positive(),
  toolErrorRetry: z.number().int().min(0),
  consecutiveAssistantNoToolLimit: z.number().int().positive()
}).strict();
export type AgentRunBudget = z.infer<typeof AgentRunBudgetSchema>;

export const AgentRunRequestSchema = z.object({
  workspaceId: z.string().min(1),
  workspaceRevision: z.string().min(1),
  engine: AgentRuntimeNameSchema.optional(),
  model: AgentModelSelectionSchema.optional(),
  evidence: ExecutionEvidenceSchema.optional(),
  toolProfile: z.string().min(1).optional(),
  message: z.string().min(1),
  budget: AgentRunBudgetSchema.optional()
}).strict();
export type AgentRunRequest = z.infer<typeof AgentRunRequestSchema>;

export interface Workspace {
  id: string;
  name: string;
  currentRevision: RevisionId;
  vaultId: string;
  rulesetVersion: RulesetVersionId;
}

export interface Vault {
  id: string;
  workspaceId: string;
  kind: "obsidian-compatible-markdown-vault";
  rootRef: string;
}

export interface Note {
  path: NotePath;
  contentHash: ContentHash;
  revision: RevisionId;
  frontmatter?: Record<string, unknown>;
}

export interface Ruleset {
  version: RulesetVersionId;
  writableRoots: string[];
  requiredFrontmatter: string[];
  maxOperationsPerChangeset: number;
  maxBytesPerChangeset: number;
}

export type RevisionId = string;
export type WorkspaceRevisionId = RevisionId;
export type RulesetVersionId = string;
export type NotePath = string;
export type ContentHash = string;

export type ChangesetStatus =
  | "CANDIDATE"
  | "AWAITING_APPROVAL"
  | "REJECTED_BY_VALIDATOR"
  | "REJECTED_BY_USER"
  | "APPLIED"
  | "STALE";

export interface SourceRef {
  notePath: NotePath;
  range: {
    startLine: number;
    endLine: number;
  };
}

export interface ValidatorReport {
  passed: boolean;
  failures: ValidatorFailure[];
}

export interface ValidatorFailure {
  ruleId:
    | "path-whitelist"
    | "frontmatter-schema"
    | "link-integrity"
    | "source-attribution"
    | "budget-limit"
    | "trash-policy"
    | "rename-conflict";
  opIndex: number;
  severity: "error" | "warning";
  message: string;
}

export type ChangesetOperation =
  | {
      kind: "create";
      path: NotePath;
      content: string;
      frontmatter?: Record<string, unknown>;
    }
  | {
      kind: "update";
      path: NotePath;
      baseContentHash: ContentHash;
      content: string;
      frontmatter?: Record<string, unknown>;
    }
  | {
      kind: "rename";
      path: NotePath;
      baseContentHash: ContentHash;
      targetPath: NotePath;
    }
  | {
      kind: "trash";
      path: NotePath;
      baseContentHash: ContentHash;
      reason: string;
    };

export interface KnowledgeChangeset {
  id: string;
  workspaceId: string;
  sessionId: string;
  runId: string;
  baseRevision: WorkspaceRevisionId;
  rulesetVersion: RulesetVersionId;
  proposedAt: string;
  proposedByEngine: AgentRuntimeName;
  status: ChangesetStatus;
  operations: ChangesetOperation[];
  validatorReport: ValidatorReport;
  sources: SourceRef[];
  appliedAt?: string;
  appliedRevision?: RevisionId;
}

export interface RuntimeContext {
  workspaceId: string;
  workspaceRevision: WorkspaceRevisionId;
  sessionId: string;
  runId: string;
  budget?: AgentRunBudget;
}

export interface RuntimeHooks {
  emitEvent(event: unknown): void | Promise<void>;
  callTool(spec: unknown): Promise<unknown>;
  requestApproval(request: unknown): Promise<unknown>;
  shouldCancel(): boolean;
}

export interface RunOutcome {
  terminalEvent: "run_completed" | "run_failed" | "run_cancelled" | "run_paused_for_approval";
  candidateChangesetIds: string[];
  error?: {
    category:
      | "provider_error"
      | "tool_error"
      | "policy_violation"
      | "validator_error"
      | "budget_exhausted"
      | "cancelled"
      | "internal_error";
    message: string;
    cause?: string;
  };
}

export interface AgentRuntimeAdapter {
  readonly engine: AgentRuntimeName;
  run(ctx: RuntimeContext, request: AgentRunRequest, hooks: RuntimeHooks): Promise<RunOutcome>;
}
