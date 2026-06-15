# Open Agent StateGraph Runner And Phase SOP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `OpenAgentGraph` from a hand-written sequential runner to LangGraph `StateGraph` while preserving Phase 33 provider-backed synthesis behavior and codifying the future phase execution SOP.

**Architecture:** Keep current node functions as the domain behavior boundary and wrap them in a LangGraph `StateGraph` orchestration layer. Provider selection and raw envelope writing stay in `runOpenAgentGraph()` closures, not in graph state. SOP docs become the reusable handoff for future phases, including real-smoke and token-handling evidence.

**Tech Stack:** TypeScript, Vitest, `@langchain/langgraph`, existing OpenAgentGraph nodes, existing MiMo/OpenAI-compatible provider adapter, existing `.agent-runs/open-agent` artifacts.

---

## File Structure

- Modify `src/runtime/open-agent/open-agent-state.ts`
  - Add optional runner metadata type.
  - Keep state serializable and secret-free.
- Modify `src/runtime/open-agent/open-agent-graph.ts`
  - Move direct sequential orchestration into a LangGraph `StateGraph`.
  - Keep public request/result interface stable.
  - Keep provider object in closures.
- Modify `src/runtime/open-agent/open-agent-artifacts.ts`
  - Include runner metadata in graph report.
- Modify `tests/unit/open-agent-graph-nodes.test.ts`
  - Add StateGraph runner behavior tests and preserve existing node semantics.
- Modify `tests/integration/open-agent-graph.test.ts`
  - Assert report runner metadata and unchanged no-write behavior.
- Modify `tests/unit/open-agent-real-smoke.test.ts`
  - Keep injected three-call `llm-graph` smoke green after migration.
- Modify `docs/architecture/runtime-phase-sop.md`
  - Update current phase loop, real-provider SOP, token/env rules, and stop conditions.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Record Phase 34 verification and real smoke evidence after implementation.

## Task 1: Runner Metadata Red Test

**Files:**
- Modify: `tests/integration/open-agent-graph.test.ts`
- Modify: `src/runtime/open-agent/open-agent-artifacts.ts`
- Modify: `src/runtime/open-agent/open-agent-state.ts`

- [ ] **Step 1: Add failing report metadata test**

Add to `tests/integration/open-agent-graph.test.ts`:

```ts
it("records LangGraph runner metadata in the open agent graph report", async () => {
  const result = await runOpenAgentGraph({
    workspaceRoot: tempRoot,
    taskId: "graph-runner-metadata",
    message: "根据知识库总结 AI agent 架构",
    outputPolicy: "ANSWER_ONLY"
  });

  expect(result.status).toBe("SUCCEEDED");
  const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as {
    runner?: { kind?: string; version?: number };
  };
  expect(report.runner).toEqual({
    kind: "LANGGRAPH_STATEGRAPH",
    version: 1
  });
});
```

- [ ] **Step 2: Run red test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/integration/open-agent-graph.test.ts
```

Expected: FAIL because `report.runner` is undefined.

- [ ] **Step 3: Add metadata type and report field**

In `src/runtime/open-agent/open-agent-state.ts`, add:

```ts
export interface OpenAgentRunnerMetadata {
  kind: "LANGGRAPH_STATEGRAPH";
  version: 1;
}
```

Add to `OpenAgentGraphState`:

```ts
runner: OpenAgentRunnerMetadata;
```

In `toOpenAgentGraphReport`, include:

```ts
runner: state.runner,
```

- [ ] **Step 4: Initialize metadata in current runner**

In `runOpenAgentGraph`, initialize:

```ts
runner: {
  kind: "LANGGRAPH_STATEGRAPH",
  version: 1
},
```

This intentionally makes the test green before actual StateGraph migration. The next tasks will prove the runner is actually StateGraph-backed.

- [ ] **Step 5: Run test**

Run the same command. Expected: PASS.

## Task 2: StateGraph Invocation Red Test

**Files:**
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`
- Modify: `src/runtime/open-agent/open-agent-graph.ts`

- [ ] **Step 1: Add a runner trace assertion test**

Add a test that proves the graph goes through a StateGraph wrapper by checking a runner step emitted by the wrapper, not by an existing node:

```ts
it("runs open agent orchestration through a StateGraph wrapper", async () => {
  const result = await runOpenAgentGraph({
    workspaceRoot: tempRoot,
    taskId: "graph-stategraph-wrapper",
    message: "根据知识库总结 AI agent 架构",
    outputPolicy: "ANSWER_ONLY"
  });

  expect(result.status).toBe("SUCCEEDED");
  expect(result.steps[0]).toMatchObject({
    name: "RUNNER",
    status: "SUCCEEDED",
    summary: "Open agent graph invoked through LangGraph StateGraph."
  });
});
```

Also extend `OpenAgentGraphStep["name"]` in the test expectation only through runtime type changes in the implementation step.

- [ ] **Step 2: Run red test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts
```

Expected: FAIL because there is no `RUNNER` step and the direct sequence starts with `POLICY_GATE`.

- [ ] **Step 3: Extend step type**

In `src/runtime/open-agent/open-agent-state.ts`, add `"RUNNER"` to `OpenAgentGraphStep["name"]`.

- [ ] **Step 4: Add StateGraph annotation and builder**

In `src/runtime/open-agent/open-agent-graph.ts`, import:

```ts
import { Annotation, END, START, StateGraph } from "@langchain/langgraph";
```

Add a local annotation root that includes all fields from `OpenAgentGraphState` used by nodes. Use reducers only where LangGraph requires them; otherwise keep last-value semantics:

```ts
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
  providerRuntime: Annotation<OpenAgentGraphState["providerRuntime"]>,
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
```

If the local LangGraph typings require reducer definitions for object/array state, adapt this annotation following `src/runtime/langgraph/graph.ts` and keep the state fields unchanged.

- [ ] **Step 5: Add wrapper node**

Add:

```ts
function runnerNode(state: OpenAgentGraphState): OpenAgentGraphState {
  state.steps.push({
    name: "RUNNER",
    status: "SUCCEEDED",
    summary: "Open agent graph invoked through LangGraph StateGraph."
  });
  return state;
}
```

- [ ] **Step 6: Add graph builder**

Add:

```ts
function compileOpenAgentStateGraph(provider: OpenAgentProvider) {
  return new StateGraph(OpenAgentGraphAnnotation)
    .addNode("runner", runnerNode)
    .addNode("policyGate", runPolicyGateNode)
    .addNode("plan", (state) => runPlanNode(state, provider))
    .addNode("contextGather", runContextGatherNode)
    .addNode("toolLoop", (state) => runToolLoopNode(state, provider))
    .addNode("synthesize", (state) => runSynthesizeNode(state, provider))
    .addNode("selfCheck", runSelfCheckNode)
    .addNode("artifact", runArtifactNode)
    .addEdge(START, "runner")
    .addEdge("runner", "policyGate")
    .addConditionalEdges("policyGate", (state) => (state.status === "RUNNING" ? "plan" : "artifact"))
    .addConditionalEdges("plan", (state) => (state.status === "RUNNING" ? "contextGather" : "artifact"))
    .addEdge("contextGather", "toolLoop")
    .addConditionalEdges("toolLoop", (state) => {
      if (state.status === "FAILED_BUDGET" || state.status === "FAILED_VALIDATION" || state.status === "FAILED_PROVIDER") {
        return "artifact";
      }
      return "synthesize";
    })
    .addConditionalEdges("synthesize", (state) => (state.status === "NEEDS_CONFIRMATION" ? "artifact" : "selfCheck"))
    .addEdge("selfCheck", "artifact")
    .addEdge("artifact", END)
    .compile();
}
```

- [ ] **Step 7: Invoke compiled graph**

Replace the direct node sequence in `runOpenAgentGraph` with:

```ts
const compiled = compileOpenAgentStateGraph(provider);
const finalState = (await compiled.invoke(state)) as OpenAgentGraphState;
if (finalState.status === "RUNNING") {
  finalState.status = "SUCCEEDED";
}
return toResult(finalState);
```

If artifact node needs status normalized before writing report, add a small `finalizeStatusNode` before artifact:

```ts
function finalizeStatusNode(state: OpenAgentGraphState): OpenAgentGraphState {
  if (state.status === "RUNNING") {
    state.status = "SUCCEEDED";
  }
  return state;
}
```

Then route `selfCheck -> finalize -> artifact`, and terminal failure paths directly to `artifact`.

- [ ] **Step 8: Run focused test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts
```

Expected: PASS.

## Task 3: Conditional Edge Semantics

**Files:**
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`
- Modify: `src/runtime/open-agent/open-agent-graph.ts`

- [ ] **Step 1: Add invalid plan stop test**

Extend the existing invalid-plan test:

```ts
expect(result.status).toBe("FAILED_VALIDATION");
expect(result.steps.map((step) => step.name)).toEqual(["RUNNER", "POLICY_GATE", "PLAN", "ARTIFACT"]);
expect(result.toolCalls).toEqual([]);
expect(result.loopIterations).toBe(0);
```

- [ ] **Step 2: Run red test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts
```

Expected: FAIL if StateGraph routes invalid plan incorrectly or if artifact is not written for failure.

- [ ] **Step 3: Fix plan conditional edge**

Ensure `plan` routes to `artifact` on `FAILED_VALIDATION` / `FAILED_PROVIDER`.

- [ ] **Step 4: Add budget stop assertion**

Extend the budget-loop test:

```ts
expect(result.steps.map((step) => step.name)).toEqual([
  "RUNNER",
  "POLICY_GATE",
  "PLAN",
  "CONTEXT_GATHER",
  "TOOL_LOOP",
  "ARTIFACT"
]);
expect(result.answer).toBeUndefined();
expect(result.synthesis).toBeUndefined();
```

- [ ] **Step 5: Run red/green**

Run the same focused command. If red, adjust the `toolLoop` conditional edge so failure routes directly to `artifact`.

- [ ] **Step 6: Add confirmation assertion**

Extend the confirmation test:

```ts
expect(result.steps.map((step) => step.name)).toContain("HANDOFF");
expect(result.steps.map((step) => step.name)).toContain("ARTIFACT");
expect(result.synthesis).toBeUndefined();
```

Expected: confirmation path still invokes `runSynthesizeNode` only for handoff generation and does not create provider-backed synthesis metadata.

- [ ] **Step 7: Run focused tests**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts
```

Expected: PASS.

## Task 4: Provider-backed Synthesis Regression

**Files:**
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`
- Modify: `tests/integration/open-agent-graph.test.ts`

- [ ] **Step 1: Strengthen provider answer test**

In the provider-backed answer test, assert:

```ts
expect(result.steps.map((step) => step.name)).toEqual([
  "RUNNER",
  "POLICY_GATE",
  "PLAN",
  "CONTEXT_GATHER",
  "TOOL_LOOP",
  "SYNTHESIZE",
  "SELF_CHECK",
  "ARTIFACT"
]);
expect(result.synthesis).toMatchObject({
  providerBacked: true,
  outputKind: "ANSWER"
});
```

- [ ] **Step 2: Run focused test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts
```

Expected: PASS after StateGraph migration preserves ordering.

- [ ] **Step 3: Strengthen integration report test**

In `tests/integration/open-agent-graph.test.ts`, after reading the report for provider candidate, assert:

```ts
expect(report.runner).toEqual({ kind: "LANGGRAPH_STATEGRAPH", version: 1 });
expect(report.synthesis).toMatchObject({
  providerBacked: true,
  outputKind: "CANDIDATE_PATCH"
});
expect(report.candidatePatch?.publishable).toBe(false);
```

- [ ] **Step 4: Run integration focused test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/integration/open-agent-graph.test.ts
```

Expected: PASS.

## Task 5: Raw Provider Artifact Regression

**Files:**
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`
- Modify: `tests/unit/open-agent-real-smoke.test.ts`

- [ ] **Step 1: Strengthen graph providerRuntime test**

In the MiMo-compatible providerRuntime graph test, assert all raw refs:

```ts
expect(result.rawProviderRefs.map((ref) => ref.providerCallId)).toEqual([
  "open-agent-plan-1",
  "open-agent-next-action-2",
  "open-agent-synthesize-3"
]);
```

Read the synthesize request artifact:

```ts
const synthesizeRequest = await readFile(path.join(tempRoot, result.rawProviderRefs[2].requestPath), "utf8");
expect(synthesizeRequest).toContain("[REDACTED]");
expect(synthesizeRequest).not.toContain("test-api-key");
```

- [ ] **Step 2: Run focused test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts
```

Expected: PASS.

- [ ] **Step 3: Strengthen smoke unit test**

In `tests/unit/open-agent-real-smoke.test.ts`, assert:

```ts
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
```

- [ ] **Step 4: Run smoke unit test**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-real-smoke.test.ts
```

Expected: PASS.

## Task 6: SOP Document Update

**Files:**
- Modify: `docs/architecture/runtime-phase-sop.md`

- [ ] **Step 1: Replace stale backlog**

Replace `Current Phase Backlog` with current status:

```md
## Current Phase Status

- Phase 31: OpenAgentGraph sequential runner baseline implemented.
- Phase 32: Provider-backed plan/action implemented.
- Phase 33: Provider-backed synthesis implemented and real MiMo ANSWER_ONLY smoke passed.
- Phase 34: StateGraph runner migration and reusable phase SOP.
```

- [ ] **Step 2: Add default next-turn protocol**

Add:

```md
## Default Next-turn Protocol

When the user says "continue", "next phase", "keep going", or equivalent:

1. Read this SOP.
2. Read latest phase spec, plan, change note, and delivery report.
3. Route with `adaptive-dev-workflow`.
4. Classify phase risk.
5. If behavior changes are planned, follow TDD.
6. Run focused tests before real provider smoke.
7. Run full verification before reporting completion.
8. Update delivery report.
9. Draft the next phase spec/plan/change note if no Stop Condition is triggered.
```

- [ ] **Step 3: Add real provider smoke protocol**

Add:

```md
## Real Provider Smoke Protocol

Use real provider smoke only when the phase plan requires it or the user explicitly asks.

Required evidence:

- temp fixture workspace under `/private/tmp`;
- command uses `--execute-real`;
- API key comes from hidden stdin or transient process env;
- raw request artifacts show `Authorization: "[REDACTED]"`;
- token search under `.agent-runs/open-agent` returns no match;
- expected workspace target does not exist;
- report records providerCalls and raw provider refs.
```

- [ ] **Step 4: Add token handling rules**

Add:

```md
## Token Handling

Allowed:

- hidden stdin;
- transient shell env for the current command/session;
- injected fake env values such as `test-api-key` in unit tests.

Forbidden:

- repo `.env` with real values;
- command arguments containing real token values;
- real token in docs, fixtures, snapshots, raw artifacts, stdout/stderr, or reports.
```

- [ ] **Step 5: Review SOP**

Run:

```text
rg -n "replan failure report|retryable failed work item|optional DeepSeek real adapter|T[O]DO|T[B]D" docs/architecture/runtime-phase-sop.md
```

Expected: no stale backlog phrases and no placeholder markers.

## Task 7: Focused Verification

**Files:**
- No code edits unless tests expose a regression.

- [ ] **Step 1: Run focused tests**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts tests/unit/open-agent-real-smoke.test.ts
```

Expected: all tests pass.

- [ ] **Step 2: Run provider parser smoke tests**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts
```

Expected: pass. This proves StateGraph migration did not require changing provider parse contracts.

## Task 8: Full Verification

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Run full tests**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
```

Expected: all tests pass.

- [ ] **Step 2: Run typecheck**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
```

Expected: exit code 0.

- [ ] **Step 3: Run diff check**

Run:

```text
git diff --check
```

Expected: exit code 0.

- [ ] **Step 4: Run injected-fetch smoke**

Run:

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-real-smoke.test.ts
```

Expected: pass with injected fake fetch. Do not describe this as a real provider call.

## Task 9: Real MiMo Smoke

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Prepare temp workspace**

Run:

```text
tmp=$(mktemp -d /private/tmp/open-agent-mimo-smoke.XXXXXX)
cp -R tests/fixtures/workspaces/basic-raw-mirror/. "$tmp"
printf '%s\n' "$tmp"
```

Expected: prints temp workspace path.

- [ ] **Step 2: Run real smoke with hidden stdin or transient env**

Preferred hidden stdin:

```text
read -rs MIMO_API_KEY
printf '%s\n' "$MIMO_API_KEY" | /Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --import tsx src/cli/open-agent-smoke.ts --provider mimo-open-agent-smoke --mode llm-graph --workspace-root "$tmp" --base-url https://token-plan-cn.xiaomimimo.com/v1 --model mimo-v2.5 --output-policy ANSWER_ONLY --execute-real --api-key-stdin
```

Allowed transient process env when the user explicitly approves local dev convenience:

```text
read -rs MIMO_API_KEY
export MIMO_API_KEY
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --import tsx src/cli/open-agent-smoke.ts --provider mimo-open-agent-smoke --mode llm-graph --workspace-root "$tmp" --base-url https://token-plan-cn.xiaomimimo.com/v1 --model mimo-v2.5 --output-policy ANSWER_ONLY --execute-real
unset MIMO_API_KEY
```

Expected:

- `status: "PASSED"`；
- `openAgentGraph.status: "SUCCEEDED"`；
- `openAgentGraph.providerCalls >= 3`；
- `openAgentGraph.synthesis.providerBacked === true`；
- `openAgentGraph.rawProviderRefs` includes `open-agent-synthesize-3`。

- [ ] **Step 3: If sandbox/network fails**

If the result is `fetch failed` before raw provider refs exist, classify it as network/environment failure. Retry with sandbox escalation only for the same command shape, still passing the token through hidden stdin or transient env.

- [ ] **Step 4: Check redaction**

Run:

```text
rg "Authorization|REDACTED|Bearer" "$tmp/.agent-runs/open-agent/raw-provider/mimo-open-agent-smoke/open-agent-synthesize-3/request.json"
```

Expected: shows `Authorization": "[REDACTED]"` and no bearer token.

- [ ] **Step 5: Check token search**

Run a hidden-token search that prints only match/no-match:

```text
read -rs SEARCH_TOKEN
if rg -l -F -- "$SEARCH_TOKEN" "$tmp/.agent-runs/open-agent" >/tmp/open-agent-token-search-files.txt; then
  printf 'TOKEN_MATCH\n'
  cat /tmp/open-agent-token-search-files.txt
else
  printf 'TOKEN_NO_MATCH\n'
fi
```

Expected: `TOKEN_NO_MATCH`.

- [ ] **Step 6: Check no workspace write**

Run:

```text
test ! -e "$tmp/knowledge-base/drafts/mimo-open-agent-smoke.md"
```

Expected: exit code 0.

## Task 10: Delivery Report

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Add Phase 34 report section**

Record:

- StateGraph runner migration；
- focused tests and counts；
- full tests and counts；
- typecheck result；
- diff check result；
- injected-fetch smoke result；
- real MiMo smoke result；
- providerCalls；
- raw provider refs；
- redaction evidence；
- token search evidence；
- no workspace write evidence；
- remaining boundaries。

- [ ] **Step 2: Run report consistency scan**

Run:

```text
rg -n "Phase 34|StateGraph|TOKEN_NO_MATCH|open-agent-synthesize-3|T[O]DO|T[B]D" docs/reports/runtime-work-item-execution-resume-delivery.md docs/architecture/runtime-phase-sop.md
```

Expected:

- Phase 34 report exists；
- SOP mentions StateGraph and token search；
- no placeholder markers。

- [ ] **Step 3: Final verification**

Run:

```text
git diff --check
```

Expected: exit code 0.

## Stop Conditions

Pause and report before proceeding if any of these occur:

- StateGraph migration requires changing public `RunOpenAgentGraphRequest` or `RunOpenAgentGraphResult`。
- LangGraph state/checkpoint would need to store provider object, fetch dependency, token, raw provider payload, or workspace content。
- Existing Phase 33 fake/injected tests fail for reasons unrelated to orchestration order。
- Real MiMo smoke fails after network succeeds and raw response exists; then classify failure, add regression test, and fix parser/prompt/self-check before retry。
- Full tests or typecheck fail twice without a localized root cause。

## Completion Criteria

Phase 34 is complete when:

- open-agent orchestration is backed by LangGraph `StateGraph`；
- Phase 33 provider-backed synthesis behavior is unchanged；
- reusable runtime phase SOP is current；
- focused/full/typecheck/diff pass；
- real MiMo `ANSWER_ONLY` llm-graph smoke passes；
- redaction/token-search/no-write evidence is recorded。
