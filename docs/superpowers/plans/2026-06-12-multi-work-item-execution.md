# Multi Work Item Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LangGraph execute phase runs planned note, topic-index, MOC, and quality-review work items, records per-item artifacts, and reports eval from actual artifacts instead of hard-coded demo values.

**Architecture:** Keep provider adapters behind `LlmNoteProvider`; do not add real external calls. `executePhaseNode` remains the runtime orchestrator for this slice, while agents emit `PatchBundle`, `MergeGuard` and `Validator` gate publishing, and `reportNode` summarizes persisted artifacts.

**Tech Stack:** TypeScript, Vitest, LangGraph `StateGraph`, existing fake and fixture providers.

---

## File Structure

- Modify `tests/integration/langgraph-workflow.test.ts`: add the failing acceptance test for multi-work-item execution and artifact-backed eval.
- Modify `src/runtime/langgraph/nodes/execute-phase-node.ts`: execute Phase A note items, Phase B topic-index items, and Phase C global review items from the plan.
- Modify `src/agents/mock-topic-index-agent.ts`: produce index content from the actual target path instead of a hard-coded tools page.
- Modify `src/runtime/langgraph/nodes/report-node.ts`: build `eval.json` and `report.md` from persisted plan, work-item, patch, and validation artifacts.
- Keep real provider support out of scope; fake and fixture providers remain the verification boundary.

## Task 1: Red Test For Multi Work Item Execution

**Files:**
- Modify: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Add an integration test that expects multiple work items**

Add assertions after an auto-approved fake-provider run:

```ts
const plan = JSON.parse(await readFile(path.join(tempRoot, ".agent-runs/run-multi-work-items/plan.json"), "utf8"));
expect(plan.workItems.filter((item: { type: string }) => item.type === "CREATE_TOPIC_NOTE")).toHaveLength(2);
expect(plan.workItems.filter((item: { type: string }) => item.type === "REWRITE_TOPIC_NOTE")).toHaveLength(1);
expect(await exists(path.join(tempRoot, ".agent-runs/run-multi-work-items/patches/create-go-go-基础语法.patch.json"))).toBe(true);
expect(await exists(path.join(tempRoot, ".agent-runs/run-multi-work-items/patches/maintain-go-index.patch.json"))).toBe(true);
expect(await exists(path.join(tempRoot, ".agent-runs/run-multi-work-items/patches/maintain-moc.patch.json"))).toBe(true);
const evalReport = JSON.parse(await readFile(path.join(tempRoot, ".agent-runs/run-multi-work-items/eval.json"), "utf8"));
expect(evalReport.rawCoverage).toEqual({ total: 3, seen: 3 });
expect(evalReport.pagesRewritten).toBe(3);
expect(evalReport.rawMirrorConverted).toBe(1);
expect(evalReport.workItemStatuses["maintain-moc"]).toBe("PUBLISHED");
expect(evalReport.workItemStatuses["quality-review"]).toBe("SUCCEEDED");
```

- [ ] **Step 2: Run the focused test to verify RED**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: FAIL because only one note work item is executed and eval is hard-coded.

## Task 2: Execute Planned Note And Topic Index Items

**Files:**
- Modify: `src/runtime/langgraph/nodes/execute-phase-node.ts`
- Modify: `src/agents/mock-topic-index-agent.ts`

- [ ] **Step 1: Add a shared `runPatchWorkItem` helper inside `execute-phase-node.ts`**

The helper must:

- read source content for note items;
- call the selected note provider only for note items;
- call `runMockTopicIndexAgent` for `MAINTAIN_TOPIC_INDEX`;
- write `patches/<workItemId>.patch.json`;
- run `checkMerge` and `validateBundle`;
- write `validation/<workItemId>.json`;
- publish allowed bundles;
- write updated `work-items/<workItemId>.json`.

- [ ] **Step 2: Iterate plan phases**

In `executePhaseNode`, run:

```ts
const executableItems = plan.workItems.filter((item) =>
  item.phase === "phase-a-notes" || item.phase === "phase-b-indexes"
);
```

Return `FAILED` only when at least one executable item fails or is blocked; otherwise continue to report.

- [ ] **Step 3: Make topic index content target-aware**

For `knowledge-base/topics/go/index.md`, emit a heading and link for the `go` topic. For nested topics, derive the topic label from the path segment before `index.md`.

- [ ] **Step 4: Run the focused test to verify GREEN**

Run the same focused integration command. Expected: PASS.

## Task 3: Artifact-Backed Report

**Files:**
- Modify: `src/runtime/langgraph/nodes/report-node.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Read actual artifacts in `reportNode`**

When auto-approved and not failed, read `plan.json`, `patches/*.patch.json`, `validation/*.json`, and `work-items/*.json`. Derive:

- `rawCoverage.total` from `plan.workspaceSnapshot.rawCount`;
- `rawCoverage.seen` from patch eval `rawFilesSeen`;
- `pagesRewritten` from published note bundles;
- `rawMirrorConverted` from patch eval;
- `qualityIssues` from validation artifacts;
- `workItemStatuses` from stored work-item artifacts.

- [ ] **Step 2: Keep failure report behavior unchanged**

If `state.status === "FAILED"`, write the existing failed report and do not overwrite status.

- [ ] **Step 3: Extend integration assertions**

Assert that `eval.json.workItemStatuses["maintain-go-index"] === "PUBLISHED"` and report text includes `Pages rewritten: 3`.

- [ ] **Step 4: Run focused integration test**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 4: Execute Phase C Global Review

**Files:**
- Modify: `src/runtime/langgraph/nodes/execute-phase-node.ts`
- Modify: `src/runtime/langgraph/nodes/report-node.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Extend `runPatchWorkItem` for `MAINTAIN_MOC`**

Build a `PatchBundle` for `knowledge-base/moc.md` from published topic index paths. It must still pass through `checkMerge`, `validateBundle`, and `publishBundle`.

- [ ] **Step 2: Execute `QUALITY_REVIEW` as a non-writing work item**

Read published note target contents, call `runQualityReviewAgent`, write `quality/<workItemId>.json`, and write `work-items/<workItemId>.json` with status `SUCCEEDED`.

- [ ] **Step 3: Add quality findings into eval**

`reportNode` must include issues from both validation artifacts and `quality/*.json`.

- [ ] **Step 4: Run focused integration test**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 5: Full Verification

**Files:**
- No additional source files.

- [ ] **Step 1: Run full unit and integration suite**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
```

Expected: all Vitest test files pass.

- [ ] **Step 2: Run TypeScript typecheck**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
```

Expected: exit 0.

- [ ] **Step 3: Run whitespace check**

```bash
git diff --check
```

Expected: no output and exit 0.

## Task 6: Runtime Work Item Resume And Needs-Replan

**Files:**
- Modify: `src/runtime/langgraph/nodes/plan-node.ts`
- Modify: `src/runtime/langgraph/nodes/execute-phase-node.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Add RED test for same-run skip**

Run the workflow twice with the same `runId`. Read `.agent-runs/<runId>/traces/create-go-go-基础语法.jsonl` before and after the second run. Expected current failure before implementation: trace length increases because provider is called again.

- [ ] **Step 2: Reuse existing plan for same `runId`**

In `planNode`, if `plan.json` already exists, reuse it and only refresh plan approval status. Do not rewrite `work-items/*.json`.

- [ ] **Step 3: Add runtime resume decision before patch execution**

In `execute-phase-node.ts`, inspect stored work item, patch content sha, and current target sha before running a work item:

- `SKIP`: return success without provider call.
- `NEEDS_REPLAN`: write work item status `NEEDS_REPLAN` and fail current run with `WORK_ITEM_NEEDS_REPLAN`.
- `RUN`: execute normally.

- [ ] **Step 4: Add RED/GREEN test for user-edited target**

After a successful run, manually edit `knowledge-base/topics/go/Go 基础语法.md`, rerun same `runId`, and assert:

- result status is `FAILED`;
- lastError is `WORK_ITEM_NEEDS_REPLAN`;
- trace length did not increase;
- work item status is `NEEDS_REPLAN`;
- user edit remains in the target file.

## Task 7: Replan Failure Report

**Files:**
- Modify: `src/runtime/langgraph/nodes/report-node.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Add RED test for actionable failure report**

Extend the user-edited target rerun test to assert that `report.md` contains the `NEEDS_REPLAN` work item id.

- [ ] **Step 2: Read failed work-item artifacts in failure report path**

In `reportNode`, when `state.status === "FAILED"`, read `work-items/*.json` if present and list any item with status `NEEDS_REPLAN`.

- [ ] **Step 3: Run focused integration test**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 8: Retryable Failed Work Item Resume

**Files:**
- Modify: `src/runtime/langgraph/nodes/execute-phase-node.ts`
- Modify: `tests/integration/provider-failure.test.ts`

- [ ] **Step 1: Add RED test for retrying timeout failure**

Run same `runId` twice:

1. First run with `provider: "timeout-fixture"` and expect `FAILED_TIMEOUT`.
2. Second run with `provider: "fake"` and expect `SUCCEEDED_WITH_WARNINGS`.

Then assert the previously failed work item:

- status is `PUBLISHED`;
- attempts contains first `FAILED_TIMEOUT`;
- attempts contains final `PUBLISHED`;
- target note no longer contains bootstrap `Raw mirror: true`.

Expected current failure before implementation: retry can publish, but attempts from the failed run are not preserved.

- [ ] **Step 2: Preserve stored attempts during retry**

Before executing a work item, read the existing `work-items/<workItemId>.json` artifact if present. Use its `attempts` as the base audit trail instead of the attempts from `plan.json`.

- [ ] **Step 3: Append success attempt after publish**

When a work item publishes successfully, write it with:

```ts
attempts: [
  ...previousAttempts,
  {
    attempt: previousAttempts.length + 1,
    status: "PUBLISHED",
    message: "published"
  }
]
```

- [ ] **Step 4: Preserve attempts on provider failure and validator block**

Provider failure and validator block must append to the stored attempt list instead of replacing it.

- [ ] **Step 5: Run focused tests**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/provider-failure.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 9: DeepSeek Real Adapter Smoke, Skip By Default

**Files:**
- Create: `src/domain/llm-provider/deepseek-real-provider.ts`
- Modify: `src/runtime/provider/real-smoke.ts`
- Modify: `tests/unit/provider-smoke.test.ts`
- Modify: `docs/architecture/deepseek-real-adapter-spec.md`

- [ ] **Step 1: Add RED test for fetch-based adapter mapping**

Test `createDeepSeekRealNoteProvider` with injected fake fetch. Assert:

- request URL is `https://api.deepseek.com/chat/completions`;
- request method is `POST`;
- Authorization header is present;
- model is `deepseek-v4-pro`;
- result provider is `deepseek`;
- result content maps from `choices[0].message.content`.

- [ ] **Step 2: Implement `createDeepSeekRealNoteProvider`**

Use fetch injection. Do not import OpenAI SDK or add dependencies. Parse OpenAI-compatible response and return `LlmNoteProviderResult`.

- [ ] **Step 3: Wire real smoke through adapter**

`runRealProviderSmoke` should call the adapter only when `executeReal=true` and required env exists. It must use `${DEEPSEEK_BASE_URL}/chat/completions` through the adapter.

- [ ] **Step 4: Keep smoke skip-by-default**

Existing CLI smoke without env must remain `SKIPPED` and `realExternalCall=false`.

- [ ] **Step 5: Run verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS without API key and without network.

## Task 10: DeepSeek Redacted Raw Envelope Capture

**Files:**
- Modify: `src/domain/llm-provider/deepseek-real-provider.ts`
- Modify: `tests/unit/provider-smoke.test.ts`
- Modify: `docs/architecture/deepseek-real-adapter-spec.md`

- [ ] **Step 1: Add RED test for redacted raw capture**

Use `createDeepSeekRealNoteProvider` with injected fake fetch and `onRawEnvelope`. Assert:

- captured request headers include `Authorization: "[REDACTED]"`;
- captured request body keeps `model`;
- captured response body keeps `usage`;
- captured envelope does not contain `test-api-key`.

- [ ] **Step 2: Implement `onRawEnvelope` config**

In the adapter, build the request envelope before fetch, parse response JSON once, and call `onRawEnvelope` with `redactProviderEnvelope({ request, response })`.

- [ ] **Step 3: Keep default behavior unchanged**

If `onRawEnvelope` is not provided, adapter returns the same `LlmNoteProviderResult` and does not expose raw envelope.

- [ ] **Step 4: Run verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS without API key and without network.

## Task 11: DeepSeek Raw Envelope Artifacts And Trace Ref

**Files:**
- Create: `src/domain/llm-trace/raw-envelope-writer.ts`
- Modify: `src/runtime/provider/real-smoke.ts`
- Modify: `tests/unit/provider-smoke.test.ts`
- Modify: `docs/architecture/deepseek-real-adapter-spec.md`

- [ ] **Step 1: Add RED test for raw artifacts**

Call `runRealProviderSmoke` with:

- complete DeepSeek env;
- `executeReal: true`;
- injected fake fetch;
- `AgentRunsStore` for a temp workspace/run.

Assert:

- `raw-provider/deepseek-real-smoke/request.json` exists;
- `raw-provider/deepseek-real-smoke/response.json` exists;
- raw files do not contain `test-api-key`;
- trace file contains `llm.provider.raw_ref`.

- [ ] **Step 2: Implement raw envelope writer**

Create `writeRawEnvelopeArtifacts` that:

- redacts envelope defensively;
- writes request and response JSON artifacts;
- appends `llm.provider.raw_ref` trace event.

- [ ] **Step 3: Wire smoke store into adapter hook**

When `runRealProviderSmoke` receives a store, pass `onRawEnvelope` to `createDeepSeekRealNoteProvider` and write artifacts through the writer.

- [ ] **Step 4: Run verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS without API key and without network.

## Task 12: Opt-In DeepSeek Runtime Provider Registry

**Files:**
- Modify: `src/runtime/provider/provider-runtime-config.ts`
- Modify: `src/runtime/provider/provider-registry.ts`
- Modify: `tests/unit/provider-registry.test.ts`
- Modify: `docs/architecture/provider-runtime-spec.md`
- Modify: `docs/architecture/deepseek-real-adapter-spec.md`

- [ ] **Step 1: Add RED test for registry selection**

Call `selectNoteProvider` with:

```ts
{
  provider: "deepseek-real",
  timeoutMs: 30000,
  model: "deepseek-v4-pro",
  baseUrl: "https://api.deepseek.com",
  apiKeyEnvName: "TEST_DEEPSEEK_API_KEY"
}
```

Pass dependencies:

```ts
{
  env: { TEST_DEEPSEEK_API_KEY: "test-api-key" },
  fetch: fakeFetch
}
```

Assert provider result maps to `provider: "deepseek"` and fake fetch receives Authorization header.

- [ ] **Step 2: Extend `ProviderRuntimeConfig`**

Add `deepseek-real`, `baseUrl`, and `apiKeyEnvName`. Do not add raw API key to config.

- [ ] **Step 3: Extend provider registry**

For `deepseek-real`, read API key from injected env or `process.env`. Throw `ProviderRuntimeError("auth", "MISSING_DEEPSEEK_API_KEY", false)` when missing.

- [ ] **Step 4: Keep defaults safe**

Default provider remains `fake`. CLI behavior does not change in this phase.

- [ ] **Step 5: Run verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-registry.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS without API key and without network.

## Task 13: CLI Guard And Workflow Injection For DeepSeek Real

**Files:**
- Modify: `src/cli/organize.ts`
- Modify: `src/runtime/langgraph/graph.ts`
- Modify: `src/runtime/langgraph/nodes/execute-phase-node.ts`
- Modify: `tests/integration/cli-smoke.test.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Add RED CLI guard test**

Run:

```bash
node --import tsx src/cli/organize.ts <workspace> "整理全部知识库" --auto-approve --provider deepseek-real
```

Expected: non-zero exit and stderr contains `requires --allow-real-provider`.

- [ ] **Step 2: Add RED workflow injection test**

Call `runOrganizeWorkflow` with `providerRuntime.provider = "deepseek-real"` and injected fake env/fetch. Assert result is `SUCCEEDED_WITH_WARNINGS` and fake fetch was called. Expected current failure: execute node does not receive provider dependencies.

- [ ] **Step 3: Implement CLI guard**

`organize` should include `deepseek-real` in recognized providers, but block it unless `--allow-real-provider` is present. With allow flag, preflight `DEEPSEEK_API_KEY`.

- [ ] **Step 4: Inject provider dependencies without storing them in state**

Add optional `providerRuntimeDependencies` to `runOrganizeWorkflow`. Capture it in the execute node closure:

```ts
.addNode("execute", (state) => executePhaseNode(state, input.providerRuntimeDependencies))
```

Do not add dependencies to `GraphState`.

- [ ] **Step 5: Run verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/cli-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS without API key and without network.

## Task 14: MiMo vLLM Fixture Runtime

**Files:**
- Create: `docs/architecture/mimo-vllm-fixture-provider-spec.md`
- Create: `src/domain/llm-provider/mimo-vllm-fixture-provider.ts`
- Modify: `src/runtime/provider/provider-runtime-config.ts`
- Modify: `src/runtime/provider/provider-registry.ts`
- Modify: `src/cli/organize.ts`
- Modify: `tests/unit/fixture-providers.test.ts`
- Modify: `tests/unit/provider-registry.test.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`
- Modify: `tests/integration/cli-smoke.test.ts`
- Modify: `docs/architecture/provider-runtime-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Add RED tests for token-free MiMo fixture runtime**

Add tests that expect:

- `createMimoVllmFixtureNoteProvider` maps `generated_text`, `finish_reason`, `prompt_token_ids`, and `output_token_ids` to `LlmNoteProviderResult`;
- `selectNoteProvider({ provider: "mimo-vllm-fixture" })` returns provider `"mimo-vllm"`;
- `runOrganizeWorkflow` with `providerRuntime.provider = "mimo-vllm-fixture"` succeeds without env/token/network;
- CLI `--provider mimo-vllm-fixture` succeeds without `--allow-real-provider`.

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/fixture-providers.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
```

Expected: FAIL because `mimo-vllm-fixture` is not implemented or recognized.

- [ ] **Step 2: Implement MiMo fixture provider**

Create `src/domain/llm-provider/mimo-vllm-fixture-provider.ts` with:

```ts
export interface MimoVllmFixtureOutput {
  model: string;
  generated_text?: string;
  finish_reason?: string | null;
  prompt_token_ids?: number[];
  output_token_ids?: number[];
}
```

The provider returns:

- `providerCallId: "<workItemId>:mimo-vllm-fixture"`;
- `provider: "mimo-vllm"`;
- default `model: "XiaomiMiMo/MiMo-7B-RL-0530"`;
- usage counts from token id array lengths;
- valid topic-note content by default.

- [ ] **Step 3: Wire runtime registry and CLI**

Add `"mimo-vllm-fixture"` to `ProviderRuntimeName`, `selectNoteProvider`, `traceProviderForRuntime`, CLI supported providers, and CLI usage text. Do not require `--allow-real-provider`.

- [ ] **Step 4: Run focused verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/fixture-providers.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
```

Expected: PASS without token and without network.

- [ ] **Step 5: Run full verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS.

## Task 15: MiMo Real Adapter And Opt-In Runtime

**Files:**
- Create: `docs/architecture/mimo-real-adapter-spec.md`
- Create: `src/domain/llm-provider/mimo-real-provider.ts`
- Modify: `src/runtime/provider/provider-runtime-config.ts`
- Modify: `src/runtime/provider/provider-registry.ts`
- Modify: `src/runtime/provider/real-smoke.ts`
- Modify: `src/cli/organize.ts`
- Modify: `src/cli/provider-smoke.ts`
- Modify: `tests/unit/provider-smoke.test.ts`
- Modify: `tests/unit/provider-registry.test.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`
- Modify: `tests/integration/cli-smoke.test.ts`
- Modify: `docs/architecture/provider-runtime-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Add RED tests for MiMo real adapter**

Add tests that expect:

- `createMimoRealNoteProvider` POSTs to `https://token-plan-cn.xiaomimimo.com/v1/chat/completions` with `Authorization: Bearer <key>`;
- response maps to `provider: "mimo-api"`, model, finish reason, usage, and content;
- raw envelope hook redacts Authorization and does not leak the test key;
- `mimo-real-smoke` skips without env and passes with injected fake fetch when `executeReal=true`;
- raw envelope artifacts and `llm.provider.raw_ref` are written when store is supplied.

- [ ] **Step 2: Add RED tests for runtime selection and CLI guard**

Add tests that expect:

- `selectNoteProvider({ provider: "mimo-real", apiKeyEnvName: "TEST_MIMO_API_KEY" }, dependencies)` uses injected env/fetch;
- missing env throws `ProviderRuntimeError`;
- `runOrganizeWorkflow` can run `mimo-real` with injected env/fetch;
- CLI blocks `--provider mimo-real` unless `--allow-real-provider` is present.

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
```

Expected: FAIL because MiMo real adapter/runtime is not implemented.

- [ ] **Step 3: Implement MiMo real adapter**

Create `src/domain/llm-provider/mimo-real-provider.ts` as fetch-based OpenAI-compatible adapter. It must:

- accept `apiKey`, `baseUrl`, `model`, `maxTokens`, `temperature`, injected `fetch`, and optional `onRawEnvelope`;
- build chat completions request;
- map response to `LlmNoteProviderResult`;
- emit `providerCallId: "<workItemId>:mimo-real"`;
- emit `provider: "mimo-api"`;
- redact raw envelope before calling hook;
- throw `MimoRealProviderError` with `httpStatus` on non-2xx.

- [ ] **Step 4: Wire registry, CLI, and smoke**

Add `mimo-real` to runtime config and provider registry. Defaults:

- base URL: `https://token-plan-cn.xiaomimimo.com/v1`;
- API key env: `MIMO_API_KEY`;
- model from config/env; tests use explicit `mimo-test-model`.

Add CLI guard:

- `--provider mimo-real` requires `--allow-real-provider`;
- then requires `MIMO_API_KEY`;
- `MIMO_BASE_URL` optional, defaulting to the endpoint above;
- `MIMO_MODEL` required for CLI real provider.

Add `mimo-real-smoke` to `provider-smoke` CLI and `real-smoke.ts`.

- [ ] **Step 5: Run focused verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
```

Expected: PASS without real MiMo env and without network.

- [ ] **Step 6: Run full verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS.

## Task 16: Secret-Safe Real Smoke CLI Input

**Files:**
- Modify: `src/cli/provider-smoke.ts`
- Modify: `tests/unit/provider-smoke.test.ts`
- Modify: `docs/architecture/mimo-real-adapter-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Add RED test for stdin API key**

Spawn:

```bash
node --import tsx src/cli/provider-smoke.ts \
  --provider mimo-real-smoke \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-test-model
```

Write `test-api-key` to stdin. Assert output has:

- `status: "SKIPPED"`;
- `realExternalCall: false`;
- `reason: "EXECUTE_REAL_NOT_SET"`.

Expected current failure: CLI ignores stdin and reports `MISSING_ENV`.

- [ ] **Step 2: Implement stdin/env override**

`provider-smoke.ts` should support:

- `--api-key-stdin`: read API key from stdin and inject only into the in-memory env object.
- `--base-url <url>`: override provider base URL in memory.
- `--model <model>`: override model in memory.

Map provider env names:

- `mimo-real-smoke`: `MIMO_API_KEY`, `MIMO_BASE_URL`, `MIMO_MODEL`;
- `deepseek-real-smoke`: `DEEPSEEK_API_KEY`, `DEEPSEEK_BASE_URL`, `DEEPSEEK_MODEL`.

Do not print the key.

- [ ] **Step 3: Run focused verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts
```

Expected: PASS without real env and without network.

- [ ] **Step 4: Run full verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS.

## Task 17: Agent Execution Quality

**Files:**
- Create: `src/agents/note-quality-loop.ts`
- Create: `tests/unit/note-quality-loop.test.ts`
- Create: `docs/architecture/agent-execution-quality-spec.md`
- Modify: `src/agents/mock-note-agent.ts`
- Modify: `src/domain/llm-provider/harness-note-provider.ts`
- Modify: `src/runtime/provider/provider-runtime-config.ts`
- Modify: `src/runtime/provider/provider-registry.ts`
- Modify: `tests/unit/llm-provider.test.ts`
- Modify: `tests/integration/langgraph-workflow.test.ts`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Add RED tests for note quality loop**

Add unit tests for:

- missing `## 相关链接` is repaired;
- structurally invalid draft is not falsely repaired.

Add agent test for:

- `runMockNoteAgent` writes `agent-loop/<workItemId>.json`;
- repaired content reaches the `PatchBundle`.

Add workflow test for:

- `weak-relations-fixture` publishes repaired note;
- validation quality issues no longer include `TOPIC_NOTE_WEAK_RELATIONS`.

- [ ] **Step 2: Implement bounded note quality loop**

Add `runNoteQualityLoop` with:

- `GENERATE_NOTE`;
- `SELF_CHECK`;
- `REPAIR_NOTE`;
- `SELF_CHECK_AFTER_REPAIR`;
- `repairedIssues`;
- `remainingIssues`.

Only repair `TOPIC_NOTE_WEAK_RELATIONS` when title, summary, and source tracking already exist.

- [ ] **Step 3: Wire note agent and harness provider**

`runMockNoteAgent` must:

- call provider;
- run quality loop;
- write `agent-loop/<workItemId>.json`;
- finalize `contentSha` after repair.

Add `weak-relations-fixture` to provider runtime for integration verification.

- [ ] **Step 4: Run focused and full verification**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/note-quality-loop.test.ts tests/unit/llm-provider.test.ts tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

Expected: PASS.

## Self Review

- Spec coverage: covers multi work item execution, Phase C global review, per-item artifacts, artifact-backed eval, same-run resume, needs-replan protection, retryable failed work item audit, DeepSeek real adapter smoke, redacted raw capture, raw envelope artifacts, opt-in DeepSeek runtime provider registry, CLI guard, workflow injection, MiMo vLLM fixture runtime, MiMo real opt-in adapter, secret-safe real smoke stdin input, and keeps real external calls opt-in.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: uses existing `WorkItem`, `PatchBundle`, validation, and provider runtime types.
