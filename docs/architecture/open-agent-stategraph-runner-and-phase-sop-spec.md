# Open Agent StateGraph Runner And Phase SOP Spec

> 状态：Phase 34 candidate spec。目标是在 Phase 33 provider-backed synthesis 已跑通真实 MiMo `ANSWER_ONLY` smoke 后，把 `OpenAgentGraph` 从顺序 runner 升级为 LangGraph `StateGraph` runner，同时把后续 phase 的 spec/plan/TDD/real-smoke/report 循环固化成统一 SOP，减少人工重复提示。

## 1. Current Truth

Phase 33 已完成：

- `OpenAgentGraph` 支持 provider-backed `plan()`、`nextAction()`、`synthesize()`；
- real MiMo `llm-graph ANSWER_ONLY` smoke 已通过：
  - `realExternalCall: true`；
  - graph status `SUCCEEDED`；
  - providerCalls `3`；
  - raw refs: `open-agent-plan-1`、`open-agent-next-action-2`、`open-agent-synthesize-3`；
  - raw request `Authorization` 为 `[REDACTED]`；
  - token search under `.agent-runs/open-agent` no match；
  - no workspace target write。

当前缺口：

- `src/runtime/open-agent/open-agent-graph.ts` 仍是手写顺序 runner；
- open-agent graph 的 node 边界已经存在，但还没有使用 LangGraph `StateGraph`；
- fixed workflow 已有 `src/runtime/langgraph/graph.ts` 作为 StateGraph precedent；
- `docs/architecture/runtime-phase-sop.md` 仍停留在 Phase 8-10 backlog，缺少 Phase 31-34 的真实 provider SOP、token handling 和自动推进规则；
- 每次真实 smoke 的执行细节仍需要用户重复口述。

## 2. Goal

Phase 34 交付两个结果：

1. `OpenAgentGraph` 内部改为 LangGraph `StateGraph` runner。
2. Runtime phase SOP 升级为后续 phase 的默认执行协议。

目标状态：

```text
runOpenAgentGraph(request)
  -> create initial OpenAgentGraphState
  -> select provider
  -> compileOpenAgentStateGraph(provider)
  -> invoke StateGraph
  -> write report/trace/raw refs
  -> return RunOpenAgentGraphResult
```

Graph node 顺序保持 Phase 33 语义：

```text
START
  -> policyGate
  -> plan
  -> contextGather
  -> toolLoop
  -> synthesize
  -> selfCheck
  -> artifact
  -> END
```

Conditional edges:

- after `policyGate`: stop if status is not `RUNNING`；
- after `plan`: stop if status is not `RUNNING`；
- after `toolLoop`: stop on `FAILED_BUDGET` / `FAILED_VALIDATION` / `FAILED_PROVIDER`；
- after `toolLoop`: route `NEEDS_CONFIRMATION` to `synthesize` for handoff generation；
- after `synthesize`: skip self-check only for `NEEDS_CONFIRMATION`；
- after `artifact`: END。

## 3. Non-goals

- 不改变 public `runOpenAgentGraph` / `RunOpenAgentGraphResult` contract。
- 不改变 provider prompts、parser 或 output schema，除非测试暴露 StateGraph 迁移必须修正的 bug。
- 不改变 publish/resume truth source。
- 不让 provider 直接写 `PatchBundle`、调用 publisher 或写 workspace。
- 不引入 vector database、streaming、front-end UI 或 durable external checkpointer。
- 不把真实 API key 写入 repo、docs、fixtures、artifacts、stdout/stderr 或 shell history。

## 4. Architecture

### 4.1 StateGraph Wrapper

新增 internal graph builder：

```ts
export function compileOpenAgentStateGraph(input: {
  provider: OpenAgentProvider;
}): CompiledStateGraphLike
```

The graph builder owns orchestration only. It must not create domain facts. Existing node functions remain the behavior owners:

- `runPolicyGateNode`
- `runPlanNode`
- `runContextGatherNode`
- `runToolLoopNode`
- `runSynthesizeNode`
- `runSelfCheckNode`
- `runArtifactNode`

### 4.2 State Shape

LangGraph annotation should mirror `OpenAgentGraphState` fields that are currently mutated by nodes. The state must remain artifact-friendly:

- keep paths, refs, summaries, counters, and result fields；
- do not store provider dependency objects；
- do not store raw provider payload；
- do not store API key or token；
- keep provider object outside graph state, captured by node closures。

### 4.3 Provider Dependency Boundary

Provider selection remains in `runOpenAgentGraph()`:

```text
request.providerRuntime + providerRuntimeDependencies
  -> selectOpenAgentProvider(...)
  -> provider closure
  -> StateGraph nodes
```

`providerRuntimeDependencies` must not enter LangGraph state or report.

### 4.4 Artifact Contract

Report and trace paths stay unchanged:

```text
.agent-runs/open-agent/<taskId>.json
.agent-runs/open-agent/traces/<taskId>.jsonl
.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/request.json
.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/response.json
```

The report may add runner metadata:

```json
{
  "runner": {
    "kind": "LANGGRAPH_STATEGRAPH",
    "version": 1
  }
}
```

This metadata is audit evidence only. It is not a resume truth source.

## 5. Phase SOP

The updated SOP must make the next phase executable from docs alone:

1. Read project `AGENTS.md` and nearest scoped instructions。
2. Route with `adaptive-dev-workflow`。
3. Read current phase spec, plan, change note, and runtime report。
4. If behavior changes are planned, use TDD:
   - write failing test；
   - verify red failure reason；
   - implement minimal code；
   - run focused tests；
   - continue。
5. For provider work, run fake/injected tests before real smoke。
6. For real MiMo smoke:
   - use temp fixture workspace under `/private/tmp`；
   - allow API key only through transient process env or hidden stdin；
   - never commit or document real key values；
   - run `--execute-real` only when explicitly requested or phase plan requires it；
   - if sandbox/network fails, classify failure before retrying with escalation；
   - inspect redacted raw artifacts；
   - run token search under `.agent-runs/open-agent`；
   - verify no workspace target write。
7. Run full verification:
   - focused tests；
   - full vitest；
   - typecheck；
   - `git diff --check`。
8. Update delivery report with exact evidence and remaining boundaries。
9. If no Stop Condition is triggered, create the next phase spec/plan/change note before stopping。

## 6. Token And Env Rules

Allowed during local development:

- transient shell env in the current process；
- hidden stdin piped to CLI；
- injected fake env in tests using values like `test-api-key`。

Forbidden:

- committing real token to repo files；
- writing real token to `.env` inside the repo；
- putting real token in command arguments；
- printing real token to stdout/stderr；
- recording real token in docs, reports, raw artifacts, fixtures, snapshots, or trace。

If a token is accidentally pasted into chat, the runner may use it only when the user explicitly authorizes that immediate test, but the report must still exclude the token value and should recommend rotation.

## 7. Acceptance

- Unit tests prove `runOpenAgentGraph` invokes a LangGraph StateGraph runner, not the old direct sequence.
- Unit tests prove conditional edges preserve Phase 33 status semantics:
  - invalid plan stops before context；
  - budget failure stops before synthesis；
  - confirmation still produces fixed workflow handoff；
  - successful path reaches artifact node。
- Existing provider-backed answer/draft/candidate tests continue to pass.
- Integration tests prove raw provider refs, synthesis metadata, context digest, and no-write behavior remain unchanged.
- `runOpenAgentRealSmoke({ mode: "llm-graph" })` still passes injected-fetch three-call smoke.
- One real MiMo `ANSWER_ONLY` llm-graph smoke passes after StateGraph migration:
  - `providerCalls >= 3`；
  - `synthesis.providerBacked === true`；
  - raw synthesize request has `Authorization: "[REDACTED]"`；
  - token search under `.agent-runs/open-agent` has no match；
  - no `knowledge-base/drafts/mimo-open-agent-smoke.md` write。
- `docs/architecture/runtime-phase-sop.md` contains a current reusable SOP for future phase execution.
- Full tests/typecheck/diff pass.

## 8. Review Focus

- StateGraph must preserve existing behavior, not become a new domain truth source.
- Provider object and provider dependencies must stay outside graph state/checkpoint/report.
- Conditional edges must not accidentally skip artifact writing for terminal failures that should be reported.
- No-write and no-publish boundaries must remain deterministic.
- Real-smoke evidence must distinguish injected fake, fixture, and real external call.
- Token handling must be practical for local development while preventing repo/artifact leakage.
