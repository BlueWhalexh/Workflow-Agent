# Open Agent Checkpoint Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight LangGraph checkpoint boundary to `OpenAgentGraph` without changing output semantics, provider safety boundaries, or publish/resume truth source.

**Architecture:** Reuse the existing `RuntimeCheckpointStore` / `MemorySaver` boundary from fixed workflow. The checkpointer is passed only to graph compile options, while state/report stores only serializable checkpoint audit metadata. `taskId` becomes the stable LangGraph `thread_id`.

**Tech Stack:** TypeScript, Vitest, `@langchain/langgraph` `StateGraph`/`MemorySaver`, existing open-agent graph nodes, existing `.agent-runs/open-agent` artifacts.

---

## Preflight Gate

- [ ] **Step 1: Confirm Phase 34 real smoke status**

Read `docs/reports/runtime-work-item-execution-resume-delivery.md`.

Expected current state if no token/env is available:

```text
MIMO_API_KEY_MISSING
MIMO_BASE_URL_MISSING
MIMO_MODEL_MISSING
```

If transient env/token or configured macOS Keychain values are available, run Phase 34 real MiMo `ANSWER_ONLY` llm-graph smoke before claiming Phase 35 completion. Do not put token values in command args, docs, fixtures, reports, stdout, or stderr.

One-time local Keychain setup:

```text
read -rs MIMO_API_KEY
printf '%s\n' "$MIMO_API_KEY" | /Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --import tsx src/cli/open-agent-smoke.ts --provider mimo-open-agent-smoke --configure-mimo-keychain --api-key-stdin --base-url https://token-plan-cn.xiaomimimo.com/v1 --model mimo-v2.5
unset MIMO_API_KEY
```

- [ ] **Step 2: Route with adaptive-dev-workflow**

Expected route: Medium, because this adds optional public request surface and runtime orchestration metadata.

Stop before implementation if the optional public field is not approved.

## File Structure

- Modify `src/runtime/open-agent/open-agent-state.ts`
  - Add serializable `OpenAgentCheckpointMetadata`.
  - Add `checkpoint` field to `OpenAgentGraphState`.
- Modify `src/runtime/open-agent/open-agent-graph.ts`
  - Import `RuntimeCheckpointStore`.
  - Add optional `checkpointStore` to `RunOpenAgentGraphRequest`.
  - Add optional `checkpointStore` to `compileOpenAgentStateGraph`.
  - Compile with optional checkpointer.
  - Invoke with `{ configurable: { thread_id: taskId } }`.
- Modify `src/runtime/open-agent/open-agent-artifacts.ts`
  - Include checkpoint metadata in report.
- Modify `tests/unit/open-agent-graph-nodes.test.ts`
  - Assert no-checkpoint metadata and unchanged success/failure step order.
- Modify `tests/integration/open-agent-graph.test.ts`
  - Add checkpoint-enabled report metadata test.
- Modify `tests/unit/open-agent-graph-policy.test.ts`
  - Ensure policy failure still writes artifact with checkpoint metadata and does not call provider.
- Modify `tests/unit/open-agent-real-smoke.test.ts`
  - Keep injected llm-graph smoke green after request type change.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Add Phase 35 evidence after implementation.
- Modify `docs/architecture/runtime-phase-sop.md`
  - Update current phase status after implementation.

## Task 1: Checkpoint Metadata Red Test

**Files:**
- Modify: `tests/integration/open-agent-graph.test.ts`
- Modify: `src/runtime/open-agent/open-agent-state.ts`
- Modify: `src/runtime/open-agent/open-agent-artifacts.ts`

- [ ] **Step 1: Add failing checkpoint metadata test**

Add to `tests/integration/open-agent-graph.test.ts`:

```ts
import { createMemoryCheckpointStore } from "../../src/runtime/langgraph/checkpoint-store.js";
```

Add test:

```ts
it("records checkpoint metadata when open agent graph uses a checkpoint store", async () => {
  const checkpointStore = createMemoryCheckpointStore();
  const result = await runOpenAgentGraph({
    workspaceRoot: tempRoot,
    taskId: "graph-checkpoint-metadata",
    message: "根据知识库总结 AI agent 架构",
    outputPolicy: "ANSWER_ONLY",
    checkpointStore
  });

  expect(result.status).toBe("SUCCEEDED");
  const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as {
    checkpoint?: { enabled?: boolean; threadId?: string; kind?: string };
  };
  expect(report.checkpoint).toEqual({
    enabled: true,
    threadId: "graph-checkpoint-metadata",
    kind: "LANGGRAPH_MEMORY"
  });
});
```

- [ ] **Step 2: Run red test**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/integration/open-agent-graph.test.ts
```

Expected: TypeScript compile failure or runtime failure because `checkpointStore` is not part of `RunOpenAgentGraphRequest`, and report has no `checkpoint`.

- [ ] **Step 3: Add metadata type**

In `src/runtime/open-agent/open-agent-state.ts`, add:

```ts
export interface OpenAgentCheckpointMetadata {
  enabled: boolean;
  threadId: string;
  kind?: "LANGGRAPH_MEMORY";
}
```

Add to `OpenAgentGraphState`:

```ts
checkpoint: OpenAgentCheckpointMetadata;
```

- [ ] **Step 4: Add report field**

In `toOpenAgentGraphReport`, include:

```ts
checkpoint: state.checkpoint,
```

Do not include `checkpointStore`, `MemorySaver`, provider object, fetch dependency, env, API key, or raw provider body.

## Task 2: Compile With Optional Checkpointer

**Files:**
- Modify: `src/runtime/open-agent/open-agent-graph.ts`
- Modify: `tests/integration/open-agent-graph.test.ts`

- [ ] **Step 1: Extend request type**

In `src/runtime/open-agent/open-agent-graph.ts`, import:

```ts
import type { RuntimeCheckpointStore } from "../langgraph/checkpoint-store.js";
```

Add optional request field:

```ts
checkpointStore?: RuntimeCheckpointStore;
```

- [ ] **Step 2: Extend graph builder input**

Change builder signature:

```ts
export function compileOpenAgentStateGraph(input: {
  provider: OpenAgentProvider;
  checkpointStore?: RuntimeCheckpointStore;
}) {
```

Change compile call:

```ts
.compile({
  checkpointer: input.checkpointStore?.checkpointer
});
```

- [ ] **Step 3: Initialize checkpoint metadata**

In initial `OpenAgentGraphState`:

```ts
checkpoint: {
  enabled: Boolean(request.checkpointStore),
  threadId: taskId,
  kind: request.checkpointStore ? "LANGGRAPH_MEMORY" : undefined
},
```

- [ ] **Step 4: Invoke with stable thread id**

Replace invoke call:

```ts
const compiled = compileOpenAgentStateGraph({
  provider,
  checkpointStore: request.checkpointStore
});
const finalState = (await compiled.invoke(state, {
  configurable: {
    thread_id: taskId
  }
})) as OpenAgentGraphState;
```

- [ ] **Step 5: Run integration test**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/integration/open-agent-graph.test.ts
```

Expected: checkpoint metadata test passes; existing no-write/report tests still pass.

## Task 3: No-checkpoint And Failure-path Regression

**Files:**
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`
- Modify: `tests/unit/open-agent-graph-policy.test.ts`

- [ ] **Step 1: Add no-checkpoint report assertion**

In a successful graph test, read report and assert:

```ts
const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as {
  checkpoint?: { enabled?: boolean; threadId?: string; kind?: string };
};
expect(report.checkpoint).toEqual({
  enabled: false,
  threadId: "graph-answer"
});
```

Expected red if Task 1/2 did not add default metadata.

- [ ] **Step 2: Strengthen policy failure test**

In `tests/unit/open-agent-graph-policy.test.ts`, for the unknown methodology case:

```ts
expect(result.steps.find((step) => step.name === "POLICY_GATE")?.status).toBe("FAILED");
expect(result.steps.at(-1)?.name).toBe("ARTIFACT");
expect(result.providerCalls).toBe(0);
```

Read report:

```ts
const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as {
  checkpoint?: { enabled?: boolean; threadId?: string };
};
expect(report.checkpoint).toEqual({
  enabled: false,
  threadId: "graph-policy-unknown-methodology"
});
```

If `readFile` is not imported, update import:

```ts
import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
```

- [ ] **Step 3: Run focused tests**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-graph-policy.test.ts
```

Expected: both files pass.

## Task 4: Provider Boundary Regression

**Files:**
- Modify: `tests/unit/open-agent-real-smoke.test.ts`
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`

- [ ] **Step 1: Keep injected llm-graph smoke assertions**

Ensure `tests/unit/open-agent-real-smoke.test.ts` still asserts:

```ts
expect(result.openAgentGraph?.providerCalls).toBe(3);
expect(result.openAgentGraph?.synthesis).toMatchObject({
  providerBacked: true,
  providerCallId: "open-agent-synthesize-3",
  outputKind: "ANSWER"
});
expect(result.openAgentGraph?.rawProviderRefs.map((ref) => ref.providerCallId)).toEqual([
  "open-agent-plan-1",
  "open-agent-next-action-2",
  "open-agent-synthesize-3"
]);
expect(JSON.stringify(result)).not.toContain("test-api-key");
```

- [ ] **Step 2: Assert checkpoint metadata does not leak dependencies**

In the MiMo-compatible providerRuntime graph test, after reading a report:

```ts
const reportText = await readFile(path.join(tempRoot, result.artifactPath), "utf8");
expect(reportText).toContain("\"checkpoint\"");
expect(reportText).not.toContain("test-api-key");
expect(reportText).not.toContain("fetch");
expect(reportText).not.toContain("providerRuntimeDependencies");
```

- [ ] **Step 3: Run provider focused tests**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts tests/unit/open-agent-provider.test.ts
```

Expected: all pass. This remains injected fake/fetch evidence, not a real external provider call.

## Task 5: Full Verification And Report

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/architecture/runtime-phase-sop.md`

- [ ] **Step 1: Run full test suite**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
```

Expected: all test files pass.

- [ ] **Step 2: Run typecheck**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
```

Expected: exit code 0.

- [ ] **Step 3: Run diff check**

```text
git diff --check
```

Expected: exit code 0.

- [ ] **Step 4: Update delivery report**

Add Phase 35 section recording:

- checkpoint metadata contract；
- focused test results；
- full test/typecheck/diff results；
- injected fake/fetch provider boundary evidence；
- whether Phase 34 real MiMo smoke was completed or remains blocked by env/token；
- remaining boundary that `MemorySaver` is same-process only and not durable cross-process resume。

- [ ] **Step 5: Update SOP current phase status**

In `docs/architecture/runtime-phase-sop.md`, update current phase status to show Phase 35 implementation state and next recommended phase.

- [ ] **Step 6: Consistency scan**

```text
rg -n "Phase 35|checkpoint|MemorySaver|thread_id|T[O]DO|T[B]D" docs/reports/runtime-work-item-execution-resume-delivery.md docs/architecture/runtime-phase-sop.md docs/architecture/open-agent-checkpoint-resume-spec.md docs/superpowers/plans/2026-06-13-open-agent-checkpoint-resume.md
```

Expected: no placeholder markers; checkpoint claims distinguish same-process MemorySaver from durable cross-process resume.

## Task 6: Real Smoke Gate

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: If env/token is available, run Phase 34 real smoke**

Use hidden stdin or transient process env only. Do not put token in args.

Command shape:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --import tsx src/cli/open-agent-smoke.ts --provider mimo-open-agent-smoke --mode llm-graph --workspace-root "$tmp" --base-url https://token-plan-cn.xiaomimimo.com/v1 --model mimo-v2.5 --output-policy ANSWER_ONLY --execute-real --api-key-stdin
```

Expected:

- `status: "PASSED"`；
- `openAgentGraph.status: "SUCCEEDED"`；
- `openAgentGraph.providerCalls >= 3`；
- `openAgentGraph.synthesis.providerBacked === true`。

- [ ] **Step 2: Redaction and no-write checks**

Run token search and no-write checks as described in `docs/architecture/runtime-phase-sop.md`.

Expected:

- raw synthesize request has `Authorization: "[REDACTED]"`；
- token search returns no match；
- `knowledge-base/drafts/mimo-open-agent-smoke.md` does not exist。

- [ ] **Step 3: If env/token is unavailable**

Record explicit Stop Condition in delivery report:

```text
Real MiMo smoke not executed: transient MIMO_API_KEY/MIMO_BASE_URL/MIMO_MODEL unavailable.
```

Do not describe injected fake/fetch evidence as a real external call.

## Review Focus

- Optional public request field is acceptable.
- Checkpointer is used only in graph compile options.
- `thread_id` equals `taskId`.
- Checkpoint metadata is serializable and secret-free.
- Provider dependencies remain outside state/report/checkpoint metadata.
- Terminal artifact routes from Phase 34 remain intact.
- MemorySaver evidence is not overstated as durable resume.
