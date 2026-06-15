# Open Agent Checkpoint Resume Spec

> 状态：Phase 35 candidate spec。Phase 34 已把 `OpenAgentGraph` 迁到 LangGraph `StateGraph`，但真实 MiMo `ANSWER_ONLY` smoke 在当前环境缺少 transient env/token，仍是 preflight gate。本阶段目标是在不改变 publish/resume truth source 的前提下，为 open-agent graph 增加轻量 checkpoint/resume boundary。

## 1. Current Truth

已完成：

- `OpenAgentGraph` 内部 runner 已是 LangGraph `StateGraph`；
- `compileOpenAgentStateGraph({ provider })` 通过 provider closure 编排节点；
- report 包含 runner metadata：
  - `kind: "LANGGRAPH_STATEGRAPH"`；
  - `version: 1`；
- terminal failure path 会写 `.agent-runs/open-agent/<taskId>.json` 和 trace；
- fixed workflow 已有 `RuntimeCheckpointStore` / `createMemoryCheckpointStore()` precedent；
- fixed workflow graph compile 已支持 `checkpointer` 和 `thread_id`。

当前缺口：

- `runOpenAgentGraph()` 不能接收 checkpoint store；
- open-agent graph invoke 没有 stable `thread_id`；
- report 没有 checkpoint metadata，无法审计某次 run 是否 checkpoint-enabled；
- confirmation / failure / success 的 checkpoint 边界没有测试；
- 仍未引入 durable external checkpointer，本阶段不解决跨进程持久化。

## 2. Goal

Phase 35 交付：

1. `runOpenAgentGraph()` 支持可选 `checkpointStore`。
2. `compileOpenAgentStateGraph()` 支持可选 checkpointer compile。
3. graph invoke 使用 `taskId` 作为 LangGraph `thread_id`。
4. report 写入轻量 checkpoint metadata。
5. tests 证明 checkpoint boundary 不改变 open-agent 业务语义、provider dependency boundary、redaction 和 no-write 行为。

目标调用形态：

```text
runOpenAgentGraph({
  workspaceRoot,
  taskId,
  message,
  checkpointStore
})
  -> select provider in closure
  -> compileOpenAgentStateGraph({ provider, checkpointStore })
  -> invoke(state, { configurable: { thread_id: taskId } })
  -> report checkpoint metadata
```

## 3. Non-goals

- 不引入 SQLite / file-based durable saver。
- 不实现 cross-process resume。
- 不引入 LangGraph native `interrupt()` / `Command`。
- 不改变 `RunOpenAgentGraphResult.status`、output payload、candidate patch contract。
- 不改变 publish/resume truth source。
- 不把 provider object、fetch dependency、env、API key、raw provider payload 或 workspace file content 写入 state/checkpoint/report。
- 不让 open-agent 直接 publish workspace。

## 4. Public Contract

新增 optional request field：

```ts
checkpointStore?: RuntimeCheckpointStore;
```

这是 public SDK surface 的可选扩展。实现前必须确认，因为它会影响 exported `RunOpenAgentGraphRequest` type。

Result contract 不新增必需字段。Report 可新增审计字段：

```json
{
  "checkpoint": {
    "enabled": true,
    "threadId": "<taskId>",
    "kind": "LANGGRAPH_MEMORY"
  }
}
```

`checkpoint.kind` 只表达当前 runtime adapter。它不是 resume truth source。

## 5. Architecture

### 5.1 Checkpointer Boundary

复用 fixed workflow 的 boundary：

```ts
import type { RuntimeCheckpointStore } from "../langgraph/checkpoint-store.js";
```

`RuntimeCheckpointStore` 只进入 graph compile options，不进入 `OpenAgentGraphState`。

### 5.2 State Shape

State 只保存 serializable audit metadata：

- `checkpoint.enabled`；
- `checkpoint.threadId`；
- `checkpoint.kind`。

State 不保存：

- `checkpointStore`；
- `MemorySaver`；
- provider；
- `providerRuntimeDependencies`；
- token / API key；
- raw provider request/response body。

### 5.3 Thread ID

`taskId` 是 open-agent graph 的 stable thread id：

- 用户传入 `taskId` 时使用用户值；
- 未传入时使用 generated `open-agent-graph-<timestamp>`；
- report 中 `checkpoint.threadId` 必须等于 result `taskId`。

### 5.4 Resume Semantics

本阶段只建立 checkpoint boundary，不承诺 cross-process durable resume。

Allowed claim：

```text
MemorySaver checkpoint integration works inside one process for a stable taskId.
```

Forbidden claim：

```text
OpenAgentGraph supports durable resume after process restart.
```

## 6. Acceptance

- Unit/integration tests prove `runOpenAgentGraph({ checkpointStore })` compiles with checkpointer and invokes with `thread_id = taskId`。
- Report records checkpoint metadata when `checkpointStore` is provided。
- Report records `checkpoint.enabled: false` when no checkpoint store is provided。
- Confirmation path still returns `NEEDS_CONFIRMATION` and writes handoff artifact。
- Policy failure path still writes artifact and does not call provider。
- Provider-backed llm-graph injected smoke still passes with three calls and raw refs。
- No report/checkpoint metadata includes API key, provider dependency, fetch dependency, raw provider body, or workspace file content。
- Full vitest / typecheck / diff check pass。
- Phase 34 real MiMo `ANSWER_ONLY` smoke is either completed before implementation report, or explicitly remains a blocking external-env gap。

## 7. Review Focus

- Optional public request field is acceptable and documented。
- Checkpointer stays a runtime adapter, not a domain truth source。
- `thread_id` is stable and does not collide across distinct `taskId`s。
- Checkpoint metadata cannot leak provider dependencies or token values。
- MemorySaver evidence is not overstated as durable cross-process resume。
- Terminal artifact writing from Phase 34 remains intact。
