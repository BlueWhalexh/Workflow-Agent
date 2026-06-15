# Agent SDK MVP Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first backend-embeddable Agent SDK surface so one public SDK call can route a user instruction, execute the selected runtime, and return normalized output plus artifact evidence.

**Architecture:** Add `runAgent()` to the existing SDK facade and keep current lower-level methods as advanced APIs. `runAgent()` delegates to existing router/runtime paths, then normalizes status, output, artifact refs, workspace-write evidence, and diagnostics into `agent-sdk-run.v1`.

**Tech Stack:** TypeScript, Vitest, existing LangGraph OpenAgentGraph, existing fixed workflow, `.agent-runs` artifact storage, fake/injected provider harness.

---

## File Structure

- Modify `src/sdk/knowledge-workflow-agent.ts`: add public `runAgent()` request/result contracts and facade method.
- Modify `src/index.ts`: export the new SDK types and function.
- Create `src/sdk/agent-run-normalizer.ts`: map `HandleCommandResult`, fixed workflow result, deterministic open-agent result, and StateGraph result into `AgentSdkRunResult`.
- Modify `src/sdk/command-router.ts`: add only the minimal mode override support needed by `runAgent()`, if the facade cannot compose it cleanly.
- Add `tests/unit/agent-sdk-run.test.ts`: public contract, status mapping, data-layer artifact evidence, no-write candidate checks.
- Add `tests/integration/agent-sdk-backend-sim.test.ts`: backend-like smoke using the unified SDK entry and temp workspace.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`: append Phase 35 delivery evidence after implementation.
- Existing change note: `docs/changes/2026-06-13-agent-sdk-mvp-phase1.md`.

## Task 1: RED Tests For Public SDK Entry

**Files:**
- Create: `tests/unit/agent-sdk-run.test.ts`
- Modify in Task 2: `src/sdk/knowledge-workflow-agent.ts`
- Modify in Task 2: `src/index.ts`

- [ ] **Step 1: Add failing SDK export test**

Create `tests/unit/agent-sdk-run.test.ts`:

```ts
import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  createKnowledgeWorkflowAgent,
  runAgent,
  type AgentSdkOutputKind,
  type AgentSdkRunResult,
  type AgentSdkRunStatus,
  type RunAgentRequest
} from "../../src/index.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "agent-sdk-run-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("agent SDK unified run entry", () => {
  it("exports runAgent and stable public types", () => {
    expect(typeof createKnowledgeWorkflowAgent).toBe("function");
    expect(typeof runAgent).toBe("function");

    const request: RunAgentRequest = {
      workspaceRoot: tempRoot,
      message: "总结当前知识库",
      mode: "deterministic-open-agent"
    };
    const status: AgentSdkRunStatus = "SUCCEEDED";
    const outputKind: AgentSdkOutputKind = "answer";
    const result = null as AgentSdkRunResult | null;

    expect(request.mode).toBe("deterministic-open-agent");
    expect(status).toBe("SUCCEEDED");
    expect(outputKind).toBe("answer");
    expect(result).toBeNull();
  });
});
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: FAIL because `runAgent` and the new public types are not exported.

## Task 2: Add Public Types And Facade Stub

**Files:**
- Modify: `src/sdk/knowledge-workflow-agent.ts`
- Modify: `src/index.ts`
- Test: `tests/unit/agent-sdk-run.test.ts`

- [ ] **Step 1: Add public type definitions**

In `src/sdk/knowledge-workflow-agent.ts`, add:

```ts
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

export interface RunAgentRequest {
  workspaceRoot: string;
  message: string;
  runId?: string;
  methodologyId?: string;
  execute?: boolean;
  autoApprove?: boolean;
  mode?: AgentSdkRunMode;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}

export interface AgentSdkRunResult {
  schemaVersion: "agent-sdk-run.v1";
  runId: string;
  status: AgentSdkRunStatus;
  route: HandleCommandResult["route"];
  capabilityId: string;
  outputKind: AgentSdkOutputKind;
  output?: {
    answer?: string;
    draftArtifact?: DraftArtifact;
    candidatePatch?: CandidatePatchProposal;
    workflow?: RunOrganizeResult;
    confirmation?: HandleCommandResult["confirmation"];
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
```

Import `DraftArtifact` and `CandidatePatchProposal` from `./open-agent-runtime.js`.

- [ ] **Step 2: Add the facade method and temporary implementation**

Extend `KnowledgeWorkflowAgent`:

```ts
runAgent(request: RunAgentRequest): Promise<AgentSdkRunResult>;
```

Add a temporary exported function:

```ts
export async function runAgent(_request: RunAgentRequest): Promise<AgentSdkRunResult> {
  throw new Error("runAgent is not implemented");
}
```

Wire `createKnowledgeWorkflowAgent()`:

```ts
runAgent: (request) =>
  runAgent({
    ...request,
    methodologyId: request.methodologyId ?? config.defaultMethodologyId
  }),
```

- [ ] **Step 3: Export from `src/index.ts`**

Add `runAgent` to value exports and the new types to type exports.

- [ ] **Step 4: Verify the export test is GREEN**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: PASS for the export test. No behavior tests exist yet.

## Task 3: Normalize Deterministic Open-agent Results

**Files:**
- Create: `src/sdk/agent-run-normalizer.ts`
- Modify: `src/sdk/knowledge-workflow-agent.ts`
- Modify: `src/sdk/command-router.ts`
- Modify: `tests/unit/agent-sdk-run.test.ts`

- [ ] **Step 1: Add RED tests for answer, draft, and candidate outputs**

Append to `tests/unit/agent-sdk-run.test.ts`:

```ts
it("returns a normalized read-only answer from deterministic open-agent", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-answer",
    message: "总结当前知识库",
    mode: "deterministic-open-agent"
  });

  expect(result).toMatchObject({
    schemaVersion: "agent-sdk-run.v1",
    runId: "sdk-answer",
    status: "SUCCEEDED",
    outputKind: "answer",
    capabilityId: "agent.openTask"
  });
  expect(result.output?.answer).toContain("Sources:");
  expect(result.artifacts).toMatchObject({
    artifactRoot: ".agent-runs/open-agent",
    artifactPath: ".agent-runs/open-agent/sdk-answer.json",
    rawProviderRefs: [],
    wroteWorkspace: false,
    targetWorkspacePaths: []
  });
  expect(result.diagnostics.providerBacked).toBe(false);
});

it("returns a normalized draft artifact from deterministic open-agent", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-draft",
    message: "生成 agent loop 改进草稿",
    mode: "deterministic-open-agent"
  });

  expect(result.status).toBe("SUCCEEDED");
  expect(result.outputKind).toBe("draft");
  expect(result.output?.draftArtifact?.content).toContain("Draft only");
  expect(result.artifacts.wroteWorkspace).toBe(false);
});

it("returns a candidate patch proposal without writing the target workspace file", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-candidate",
    message: "准备 agent loop 改进候选落库",
    mode: "llm-open-agent",
    providerRuntime: { provider: "fake", timeoutMs: 30000 }
  });

  expect(result.status).toBe("NEEDS_CONFIRMATION");
  expect(result.outputKind).toBe("candidate-patch");
  expect(result.output?.candidatePatch?.publishable).toBe(false);
  expect(result.artifacts.wroteWorkspace).toBe(false);
  expect(result.artifacts.targetWorkspacePaths).toEqual(["knowledge-base/drafts/sdk-candidate.md"]);
});
```

- [ ] **Step 2: Run focused test and confirm RED**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: FAIL because `runAgent()` throws or does not normalize outputs.

- [ ] **Step 3: Implement deterministic mapping helper**

Create `src/sdk/agent-run-normalizer.ts`:

```ts
import type { CommandRoute, CommandConfirmation } from "./command-router.js";
import type { CandidatePatchProposal, DraftArtifact, RunOpenAgentTaskResult } from "./open-agent-runtime.js";
import type {
  AgentSdkOutputKind,
  AgentSdkRunResult,
  AgentSdkRunStatus,
  RunOrganizeResult
} from "./knowledge-workflow-agent.js";

export function statusFromOpenAgentTask(status: RunOpenAgentTaskResult["status"]): AgentSdkRunStatus {
  return status === "FAILED_POLICY" ? "FAILED_POLICY" : "SUCCEEDED";
}

export function outputKindFromOpenAgentTask(result: RunOpenAgentTaskResult): AgentSdkOutputKind {
  if (result.candidatePatch) return "candidate-patch";
  if (result.draftArtifact) return "draft";
  if (result.answer) return "answer";
  return result.status === "FAILED_POLICY" ? "none" : "none";
}

export function normalizeOpenAgentTask(input: {
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
```

Keep unused imports out of the final file if TypeScript reports them.

- [ ] **Step 4: Implement `runAgent()` deterministic path**

First export the existing router tool policy helpers from `src/sdk/command-router.ts` so forced open-agent modes do not duplicate policy lists:

```ts
export function readToolNames(): string[] {
  return internalTools
    .filter((tool) => tool.risk === "READ_ONLY" && tool.publicExposure === "SDK_ONLY")
    .map((tool) => tool.name);
}

export function blockedToolNames(): string[] {
  return internalTools
    .filter((tool) => tool.risk === "WORKSPACE_WRITE" || tool.publicExposure === "INTERNAL_ONLY")
    .map((tool) => tool.name);
}
```

In `src/sdk/knowledge-workflow-agent.ts`, add:

```ts
function inferSdkOutputPolicy(message: string): OpenAgentOutputPolicy {
  if (/落库|写入|保存|publish|persist|write/i.test(message)) {
    return "CANDIDATE_PATCH";
  }
  if (/生成|草稿|清单|题目|问题|计划|draft|generate|list/i.test(message)) {
    return "DRAFT_ARTIFACT";
  }
  return "ANSWER_ONLY";
}

function forcedOpenAgentRoute(outputPolicy: OpenAgentOutputPolicy): CommandRoute {
  return outputPolicy === "ANSWER_ONLY"
    ? {
        lane: "OPEN_AGENT_TASK",
        capabilityId: "agent.openTask",
        risk: "READ_ONLY",
        confidence: "MEDIUM",
        reason: "forced open-agent mode"
      }
    : {
        lane: "OPEN_AGENT_TASK",
        capabilityId: "agent.draftArtifact",
        risk: "DRAFT_ONLY",
        confidence: "MEDIUM",
        reason: "forced open-agent mode"
      };
}

export async function runAgent(request: RunAgentRequest): Promise<AgentSdkRunResult> {
  const runId = request.runId ?? defaultRunId();
  const methodologyId = request.methodologyId ?? "lmwiki-v1";

  if (request.mode === "deterministic-open-agent") {
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

  const routed = await handleCommand({
    workspaceRoot: request.workspaceRoot,
    message: request.message,
    runId,
    methodologyId,
    autoApprove: request.autoApprove,
    execute: request.execute ?? true,
    providerRuntime: request.providerRuntime,
    providerRuntimeDependencies: request.providerRuntimeDependencies,
    openAgentMode: request.mode === "llm-open-agent" ? "llm-graph" : "deterministic"
  });

  if (routed.openAgent) {
    return normalizeOpenAgentTask({
      runId,
      route: routed.route,
      methodologyId,
      result: routed.openAgent
    });
  }

  throw new Error(`runAgent could not normalize route ${routed.route.lane}`);
}
```

This is intentionally incomplete; Tasks 4 and 5 add forced graph, confirmation, and fixed workflow branches.

- [ ] **Step 5: Verify deterministic cases**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: deterministic answer and draft pass. Candidate graph case may still fail until Task 4.

## Task 4: Normalize StateGraph Open-agent Results

**Files:**
- Modify: `src/sdk/agent-run-normalizer.ts`
- Modify: `src/sdk/knowledge-workflow-agent.ts`
- Modify: `tests/unit/agent-sdk-run.test.ts`

- [ ] **Step 1: Add RED injected-provider test for raw refs and redaction boundary**

Append:

```ts
it("returns provider-backed graph diagnostics and raw provider refs", async () => {
  const responses = [
    {
      rationale: "mock provider plan",
      steps: [{ id: "read", action: "READ_CONTEXT", input: { query: "agent loop" } }]
    },
    {
      selectedAction: "SYNTHESIZE",
      rationale: "context is enough"
    },
    {
      content: "Agent loop 已经具备 SDK facade、StateGraph runner 和 artifact evidence。\n\nSources:\n- raw/ai.md",
      confidence: "medium"
    }
  ];
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-provider-answer",
    message: "总结当前 agent loop 的状态",
    mode: "llm-open-agent",
    providerRuntime: {
      provider: "mimo-real",
      baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
      model: "mimo-v2.5",
      apiKeyEnv: "MIMO_API_KEY",
      timeoutMs: 30000
    },
    providerRuntimeDependencies: {
      env: { MIMO_API_KEY: "test-api-key" },
      fetch: async () => {
        const next = responses.shift();
        if (!next) {
          throw new Error("unexpected extra provider call");
        }
        return new Response(
          JSON.stringify({
            id: "chatcmpl-test",
            choices: [{ message: { role: "assistant", content: JSON.stringify(next) } }]
          }),
          { status: 200, headers: { "content-type": "application/json" } }
        );
      }
    }
  });

  expect(result.status).toBe("SUCCEEDED");
  expect(result.outputKind).toBe("answer");
  expect(result.diagnostics.providerBacked).toBe(true);
  expect(result.diagnostics.providerRuntime).toBe("mimo-real");
  expect(result.artifacts.rawProviderRefs.length).toBeGreaterThan(0);
  expect(result.artifacts.wroteWorkspace).toBe(false);
});
```

- [ ] **Step 2: Run focused test and confirm RED**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: FAIL until graph normalization maps raw refs and provider diagnostics.

- [ ] **Step 3: Add graph normalizer**

In `src/sdk/agent-run-normalizer.ts`, add:

```ts
import type { RunOpenAgentGraphResult } from "../runtime/open-agent/open-agent-graph.js";

export function statusFromOpenAgentGraph(status: RunOpenAgentGraphResult["status"]): AgentSdkRunStatus {
  if (status === "SUCCEEDED") return "SUCCEEDED";
  if (status === "NEEDS_CONFIRMATION") return "NEEDS_CONFIRMATION";
  if (status === "FAILED_PROVIDER") return "FAILED_PROVIDER";
  return "FAILED";
}

export function normalizeOpenAgentGraph(input: {
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
      rawProviderRefs: input.result.rawProviderRefs ?? [],
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
```

- [ ] **Step 4: Wire graph normalization in `runAgent()`**

In `runAgent()`, add this branch before the generic `handleCommand()` call so `mode: "llm-open-agent"` can intentionally produce a candidate patch even when the message contains `落库`:

```ts
if (request.mode === "llm-open-agent") {
  const result = await runOpenAgentGraph({
    workspaceRoot: request.workspaceRoot,
    taskId: runId,
    message: request.message,
    methodologyId,
    outputPolicy: inferSdkOutputPolicy(request.message),
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
```

- [ ] **Step 5: Verify graph cases**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: candidate and provider-backed graph tests pass.

## Task 5: Normalize Confirmation And Fixed Workflow

**Files:**
- Modify: `src/sdk/agent-run-normalizer.ts`
- Modify: `src/sdk/knowledge-workflow-agent.ts`
- Modify: `tests/unit/agent-sdk-run.test.ts`

- [ ] **Step 1: Add RED tests for confirmation, route preview, and fixed workflow execution**

Append:

```ts
it("returns confirmation when the command implies workspace write without fixed scope", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-confirm",
    message: "把这个整理一下落库"
  });

  expect(result.status).toBe("NEEDS_CONFIRMATION");
  expect(result.outputKind).toBe("confirmation");
  expect(result.output?.confirmation?.required).toBe(true);
  expect(result.artifacts.wroteWorkspace).toBe(false);
});

it("returns a fixed workflow route preview without executing", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-preview",
    message: "整理全部知识库",
    mode: "fixed-workflow",
    execute: false
  });

  expect(result.status).toBe("WAITING_APPROVAL");
  expect(result.outputKind).toBe("route-preview");
  expect(result.route.lane).toBe("FIXED_WORKFLOW");
  expect(result.artifacts.wroteWorkspace).toBe(false);
});

it("executes fixed workflow through the unified SDK entry", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-fixed",
    message: "整理全部知识库",
    mode: "fixed-workflow",
    execute: true,
    autoApprove: true,
    providerRuntime: { provider: "fake", timeoutMs: 30000 }
  });

  expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
  expect(result.outputKind).toBe("workflow-report");
  expect(result.output?.workflow?.reportPath).toBe(".agent-runs/sdk-fixed/report.md");
  expect(result.artifacts.artifactRoot).toBe(".agent-runs/sdk-fixed");
  expect(result.artifacts.reportPath).toBe(".agent-runs/sdk-fixed/report.md");
  expect(result.artifacts.wroteWorkspace).toBe(true);
});

it("fails fixed-workflow mode when the message does not match a fixed workflow", async () => {
  const result = await runAgent({
    workspaceRoot: tempRoot,
    runId: "sdk-fixed-route-fail",
    message: "总结当前知识库",
    mode: "fixed-workflow"
  });

  expect(result.status).toBe("FAILED_ROUTE");
  expect(result.outputKind).toBe("none");
  expect(result.artifacts.wroteWorkspace).toBe(false);
});
```

- [ ] **Step 2: Run focused test and confirm RED**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: FAIL until confirmation and workflow normalization are implemented.

- [ ] **Step 3: Add confirmation and workflow normalizers**

In `src/sdk/agent-run-normalizer.ts`, add:

```ts
export function normalizeConfirmation(input: {
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

export function normalizeRoutePreview(input: {
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

export function normalizeWorkflow(input: {
  runId: string;
  route: CommandRoute;
  methodologyId: string;
  workspaceWriteAllowed: boolean;
  result: RunOrganizeResult;
}): AgentSdkRunResult {
  return {
    schemaVersion: "agent-sdk-run.v1",
    runId: input.runId,
    status:
      input.result.status === "WAITING_PLAN_APPROVAL"
        ? "WAITING_APPROVAL"
        : input.result.status === "FAILED"
          ? "FAILED"
          : "SUCCEEDED_WITH_WARNINGS",
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

export function normalizeFailedRoute(input: {
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
```

- [ ] **Step 4: Wire missing branches in `runAgent()`**

Add this fixed-mode precheck before calling `handleCommand()`:

```ts
if (request.mode === "fixed-workflow") {
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
```

Add these branches after `handleCommand()` returns:

```ts
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
    workspaceWriteAllowed: request.autoApprove === true,
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
```

- [ ] **Step 5: Verify focused SDK test**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
```

Expected: PASS.

## Task 6: Backend Simulation Integration

**Files:**
- Create: `tests/integration/agent-sdk-backend-sim.test.ts`

- [ ] **Step 1: Add integration test that records the data-layer chain**

Create `tests/integration/agent-sdk-backend-sim.test.ts`:

```ts
import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { createKnowledgeWorkflowAgent } from "../../src/index.js";

async function pathExists(absolutePath: string): Promise<boolean> {
  return access(absolutePath).then(() => true, () => false);
}

describe("agent SDK backend simulation", () => {
  it("runs user message through SDK, runtime, artifact store, and response envelope", async () => {
    const workspaceRoot = await mkdtemp(path.join(os.tmpdir(), "agent-sdk-backend-sim-"));
    try {
      await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), workspaceRoot, {
        recursive: true
      });
      const agent = createKnowledgeWorkflowAgent();

      const answer = await agent.runAgent({
        workspaceRoot,
        runId: "sim-answer",
        message: "总结当前知识库",
        mode: "deterministic-open-agent"
      });
      const draft = await agent.runAgent({
        workspaceRoot,
        runId: "sim-draft",
        message: "生成 agent loop 改进草稿",
        mode: "deterministic-open-agent"
      });
      const confirmation = await agent.runAgent({
        workspaceRoot,
        runId: "sim-confirm",
        message: "把这个整理一下落库"
      });

      expect(answer.outputKind).toBe("answer");
      expect(draft.outputKind).toBe("draft");
      expect(confirmation.outputKind).toBe("confirmation");

      const answerArtifact = path.join(workspaceRoot, answer.artifacts.artifactPath ?? "");
      expect(await pathExists(answerArtifact)).toBe(true);
      const artifactBody = await readFile(answerArtifact, "utf8");
      expect(artifactBody).toContain("open-agent-runtime.v1");
      expect(answer.artifacts.wroteWorkspace).toBe(false);
      expect(draft.artifacts.wroteWorkspace).toBe(false);
      expect(confirmation.artifacts.wroteWorkspace).toBe(false);
    } finally {
      await rm(workspaceRoot, { recursive: true, force: true });
    }
  });
});
```

- [ ] **Step 2: Run integration test**

Run:

```bash
npm test -- tests/integration/agent-sdk-backend-sim.test.ts
```

Expected: PASS.

## Task 7: Secret And No-write Evidence

**Files:**
- Modify: `tests/unit/agent-sdk-run.test.ts`

- [ ] **Step 1: Add no-write candidate assertion if missing**

Ensure candidate patch test checks:

```ts
const targetPath = path.join(tempRoot, "knowledge-base/drafts/sdk-candidate.md");
await expect(access(targetPath)).rejects.toThrow();
```

Import `access` from `node:fs/promises`.

- [ ] **Step 2: Add token scan command to verification notes**

Run after tests:

```bash
rg -n "t[p]-[a-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" src tests docs
```

Expected: no matches for real token patterns.

## Task 8: Documentation And Delivery Report

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Optional create: `docs/changes/2026-06-13-agent-sdk-mvp-phase1.md`

- [ ] **Step 1: Update delivery report**

Append a Phase 35 section containing:

```md
## Phase 35 - Agent SDK MVP Phase 1

- Public SDK entry: `agent.runAgent(request)` / `runAgent(request)`.
- Result schema: `agent-sdk-run.v1`.
- Mock/fake coverage: answer, draft, candidate patch, confirmation, fixed workflow preview/execution.
- Provider coverage: injected provider graph test; real MiMo smoke only if explicitly run.
- Safety evidence: candidate no-write, raw provider refs only, token scan no match.
- Verification:
  - `npm test -- tests/unit/agent-sdk-run.test.ts`
  - `npm test -- tests/integration/agent-sdk-backend-sim.test.ts`
  - `npm test`
  - `npm run typecheck`
  - `git diff --check`
```

- [ ] **Step 2: Add change note if public contract changed beyond this spec**

If implementation changes field names, status values, route semantics, or workspace-write interpretation, create:

```md
# Agent SDK MVP Phase 1 Change Note

Date: 2026-06-13

## Context

Phase 35 turns the SDK from multiple lower-level runtime methods into a backend-facing unified run contract.

## Decision

Backend callers should use `runAgent()` and consume `agent-sdk-run.v1`.

## Consequences

- Runtime internals remain behind SDK facade.
- `.agent-runs` remains the Phase 1 persistence layer.
- Backend integration can distinguish answer, draft, candidate patch, confirmation, workflow report, and route preview without deep runtime imports.

## Affected Docs

- `docs/architecture/agent-sdk-mvp-phase1-spec.md`
- `docs/superpowers/plans/2026-06-13-agent-sdk-mvp-phase1.md`

## Follow-up Phase

Backend adapter can bind this SDK result to HTTP/DB once storage and approval UI are designed.
```

## Task 9: Full Verification

**Files:**
- No new files unless verification reveals a targeted fix.

- [ ] **Step 1: Run focused tests**

Run:

```bash
npm test -- tests/unit/agent-sdk-run.test.ts
npm test -- tests/integration/agent-sdk-backend-sim.test.ts
```

Expected: PASS.

- [ ] **Step 2: Run full verification**

Run:

```bash
npm test
npm run typecheck
git diff --check
rg -n "t[p]-[a-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" src tests docs
```

Expected:

- all tests pass;
- typecheck passes;
- diff check prints no whitespace errors;
- token scan finds no real token pattern.

- [ ] **Step 3: Optional real MiMo smoke**

Only run if the user explicitly asks or the active phase requires it:

```bash
node --import tsx src/cli/open-agent-smoke.ts \
  --provider mimo-open-agent-smoke \
  --workspace-root /private/tmp/<fixture-workspace> \
  --execute-real
```

Credentials must come from hidden stdin, Keychain, or ignored local env. The delivery report must label this as `real external call` and include redaction, token scan, and no-write evidence.

## Review Checklist

- Public SDK callers do not need to import from `src/runtime/*`.
- `AgentSdkRunResult.status` cannot confuse confirmation, provider failure, policy failure, and successful answer.
- `artifacts.wroteWorkspace` reflects actual side effects, not desired target paths.
- OpenAgent candidate patch remains artifact-only.
- Provider-backed evidence exposes raw refs, not secrets.
- Mock/fake/injected/real validation is clearly labeled in delivery notes.
