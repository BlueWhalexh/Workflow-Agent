# SDK And Tool Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the workflow as a stable backend SDK while keeping CLI as a thin verification wrapper and internal tools behind controlled boundaries.

**Architecture:** Add a public SDK facade above the existing LangGraph workflow. CLI commands call the SDK, not internal runtime modules directly. Internal tools are typed and classified, but workspace writes remain constrained by PatchBundle, MergeGuard, Validator, and Publisher.

**Tech Stack:** TypeScript, LangGraph, Vitest, existing filesystem `.agent-runs` artifacts.

---

## File Structure

- Create `src/sdk/knowledge-workflow-agent.ts`: public SDK facade and result contracts.
- Create `src/sdk/run-inspector.ts`: artifact-backed run inspection and resume summary helpers.
- Create `src/tools/internal-tool-registry.ts`: internal tool type and initial registry metadata.
- Modify `src/index.ts`: export SDK API and stable types.
- Modify `src/cli/organize.ts`: call SDK facade.
- Modify `src/cli/resume.ts`: call SDK inspection helper.
- Add `tests/unit/sdk.test.ts`: SDK exports, default runId, response contract.
- Add `tests/unit/internal-tool-registry.test.ts`: registry names, risk classes, no unsafe public publish tool.
- Modify `tests/integration/cli-smoke.test.ts`: CLI output remains compatible after SDK wrapper.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`: append Phase 24 delivery result after implementation.

## Task 1: Public SDK Facade

**Files:**
- Create: `src/sdk/knowledge-workflow-agent.ts`
- Modify: `src/index.ts`
- Test: `tests/unit/sdk.test.ts`

- [ ] **Step 1: Write RED tests for SDK exports**

Create `tests/unit/sdk.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import {
  createKnowledgeWorkflowAgent,
  runOrganize,
  type RunOrganizeRequest,
  type RunOrganizeResult
} from "../../src/index.js";

describe("public SDK surface", () => {
  it("exports stable organize SDK functions and types", () => {
    expect(typeof createKnowledgeWorkflowAgent).toBe("function");
    expect(typeof runOrganize).toBe("function");
    const request: RunOrganizeRequest = {
      workspaceRoot: "/tmp/workspace",
      instruction: "整理全部知识库",
      autoApprove: false
    };
    expect(request.autoApprove).toBe(false);
    const result = null as RunOrganizeResult | null;
    expect(result).toBeNull();
  });
});
```

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/sdk.test.ts
```

Expected: FAIL because SDK exports do not exist.

- [ ] **Step 2: Implement SDK facade**

Create:

```ts
export interface RunOrganizeRequest {
  workspaceRoot: string;
  instruction: string;
  runId?: string;
  autoApprove: boolean;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}

export interface RunOrganizeResult {
  runId: string;
  status: "WAITING_PLAN_APPROVAL" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  planPath?: string;
  reportPath?: string;
  lastError?: string;
  artifactRoot: string;
}
```

Implement:

```ts
export function createKnowledgeWorkflowAgent(config?: KnowledgeWorkflowAgentConfig): KnowledgeWorkflowAgent
export async function runOrganize(request: RunOrganizeRequest): Promise<RunOrganizeResult>
```

Rules:

- default `runId` to `run-${Date.now()}`;
- call existing `runOrganizeWorkflow`;
- map GraphState to `RunOrganizeResult`;
- expose artifactRoot as `.agent-runs/<runId>`.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/sdk.test.ts
```

Expected: PASS.

## Task 2: SDK Integration Behavior

**Files:**
- Modify: `tests/unit/sdk.test.ts`

- [ ] **Step 1: Write RED test for SDK workflow**

Use temp fixture workspace:

```ts
const result = await runOrganize({
  workspaceRoot: tempRoot,
  instruction: "整理全部知识库",
  runId: "run-sdk",
  autoApprove: true,
  providerRuntime: { provider: "fake", timeoutMs: 30000 }
});

expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
expect(result.artifactRoot).toBe(".agent-runs/run-sdk");
```

Expected: FAIL until facade maps workflow result.

- [ ] **Step 2: Implement missing mapping**

Ensure result contains:

- runId;
- status;
- planPath;
- reportPath;
- artifactRoot;
- lastError when failed.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/sdk.test.ts
```

Expected: PASS.

## Task 3: Artifact Inspection SDK

**Files:**
- Create: `src/sdk/run-inspector.ts`
- Modify: `src/sdk/knowledge-workflow-agent.ts`
- Test: `tests/unit/sdk.test.ts`

- [ ] **Step 1: Write RED test for inspectRun**

After a fake SDK run:

```ts
const inspected = await agent.inspectRun({ workspaceRoot: tempRoot, runId: "run-sdk" });
expect(inspected.runId).toBe("run-sdk");
expect(inspected.workItemStatuses["quality-review"]).toBe("SUCCEEDED");
expect(inspected.eval?.agentLoop.missingArtifacts).toEqual([]);
```

Expected: FAIL because inspectRun does not exist.

- [ ] **Step 2: Implement artifact inspector**

Read:

- `plan.json`;
- `eval.json`;
- `work-items/*.json`;
- `report.md` path.

Do not execute workflow.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/sdk.test.ts
```

Expected: PASS.

## Task 4: Internal Tool Registry Metadata

**Files:**
- Create: `src/tools/internal-tool-registry.ts`
- Test: `tests/unit/internal-tool-registry.test.ts`

- [ ] **Step 1: Write RED registry tests**

Create tests:

```ts
expect(internalTools.map((tool) => tool.name)).toContain("workspace.scan");
expect(internalTools.find((tool) => tool.name === "patch.publish")?.risk).toBe("WORKSPACE_WRITE");
expect(publiclyExposedToolNames).not.toContain("patch.publish");
```

Expected: FAIL because registry does not exist.

- [ ] **Step 2: Implement metadata registry**

Create:

```ts
export type InternalToolRisk = "READ_ONLY" | "ARTIFACT_WRITE" | "WORKSPACE_WRITE";
export interface InternalToolMetadata {
  name: string;
  description: string;
  risk: InternalToolRisk;
  publicExposure: "SDK_ONLY" | "INTERNAL_ONLY";
}
```

First registry:

- `workspace.scan`
- `plan.createOrganizePlan`
- `patch.checkMerge`
- `patch.validate`
- `patch.publish`
- `report.aggregateEval`

Rules:

- `patch.publish` must be `INTERNAL_ONLY`.
- no provider raw envelope writer in public tool list.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/internal-tool-registry.test.ts
```

Expected: PASS.

## Task 5: CLI Uses SDK

**Files:**
- Modify: `src/cli/organize.ts`
- Modify: `src/cli/resume.ts`
- Modify: `tests/integration/cli-smoke.test.ts`

- [ ] **Step 1: Write RED compatibility test**

Existing CLI tests should continue passing. Add assertion that CLI JSON includes `artifactRoot`:

```ts
const payload = JSON.parse(result.stdout);
expect(payload.artifactRoot).toBe(".agent-runs/run-cli");
```

Expected: FAIL because CLI currently prints GraphState without artifactRoot.

- [ ] **Step 2: Refactor CLI to SDK**

`organize.ts` should:

- parse argv;
- enforce real provider guard;
- call `runOrganize`;
- print SDK result JSON.

`resume.ts` should:

- call `inspectRun` where possible;
- preserve existing output contract unless tests are updated intentionally.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/cli-smoke.test.ts
```

Expected: PASS.

## Task 6: Full Verification And Delivery

- [ ] **Step 1: Focused tests**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/sdk.test.ts tests/unit/internal-tool-registry.test.ts tests/integration/cli-smoke.test.ts
```

- [ ] **Step 2: Full suite**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
```

- [ ] **Step 3: Typecheck**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
```

- [ ] **Step 4: Diff check**

```bash
git diff --check
```

- [ ] **Step 5: Optional real workflow smoke**

If token is available and user approves, run:

```bash
printf '%s' "$MIMO_API_KEY" | \
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/node --import tsx src/cli/real-workflow-smoke.ts \
  --provider mimo-real \
  --execute-real \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5
```

Record whether it passed. Do not record token.

## Self Review

- Spec coverage: covers public SDK, backend integration boundary, CLI wrapper role, internal tools, unsafe write restrictions, and verification.
- Placeholder scan: no TODO/TBD placeholders.
- Type consistency: `RunOrganizeRequest`, `RunOrganizeResult`, `KnowledgeWorkflowAgent`, `inspectRun`, and `InternalToolMetadata` are used consistently.
