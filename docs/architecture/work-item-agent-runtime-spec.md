# Work Item Agent Runtime Spec

> 状态：下一大阶段候选 spec。目标是把当前 note-only quality loop 升级为统一的 work item agent runtime，让不同 work item 都能表达 bounded multi-step loop、预算、质量门禁、失败语义和 agent-readable 输出。本文是规划文档，不表示已实现。

## 1. 背景

当前系统已经具备：

- LangGraph workflow：`inventory -> plan -> approval -> execute -> report`。
- 多 work item 执行：note、topic index、MOC、quality review。
- Provider runtime：fake、fixture、DeepSeek real、MiMo real，真实调用 opt-in。
- Note agent quality loop：`GENERATE_NOTE -> SELF_CHECK -> REPAIR_NOTE -> SELF_CHECK_AFTER_REPAIR`。
- `.agent-runs` artifacts：plan、patches、validation、quality、traces、raw-provider、agent-loop。

当前缺口：

- 只有 note agent 有显式 loop artifact。
- Topic index、MOC、quality review 仍偏模板/单步，没有统一 loop contract。
- Work item 的 loop budget、retry、quality gate、failure reason 没有统一表达。
- `agent-loop/<workItemId>.json` 还不能作为跨 agent 的统一审计协议。
- report 还没有按 agent loop 维度汇总质量、修复、失败和 replan 证据。

## 2. 大阶段目标

建设 **Work Item Agent Runtime**：

```text
WorkItem
  -> AgentRuntime.select(workItem.type)
  -> scoped context builder
  -> bounded loop runner
  -> agent-readable loop artifact
  -> PatchBundle / QualityFindings
  -> MergeGuard / Validator
  -> Publisher / Report
```

目标不是引入复杂 agent 框架，而是把“一个 work item 内部可以多步推理/修复/评估”标准化。

## 3. 非目标

本阶段不做：

- 不接前端。
- 不引入数据库或队列。
- 不实现多 workspace / 多用户并发。
- 不做 provider fallback / rate limit runtime。
- 不让 agent runtime 直接写 workspace。
- 不让 loop artifact 参与 resume/publish 判定。
- 不把 prompt 作为质量兜底；关键规则仍在确定性代码中表达。
- 不执行默认真实外部 provider 调用。

## 4. 核心设计

### 4.0 Runtime Boundary Contract

所有 work item 必须通过统一边界执行：

```text
buildContext
  -> runtime schema validate context
  -> runLoop
  -> runtime schema validate loop result
  -> validate outputRef target
  -> write agent-loop artifact
  -> write PatchBundle / QualityFindings artifact
  -> MergeGuard / Validator
  -> Publisher
  -> update WorkItem artifact
```

硬规则：

- `runLoop` 不能直接写 workspace。
- `runLoop` 不能直接写 publishable patch artifact；只能返回 `PatchBundle | QualityFindings` 给 runtime。
- `agent-loop` artifact 必须先于 patch/quality artifact 写入。
- `outputRef.path` 必须指向同一 run 内真实存在的 artifact。
- loop report 和 output 必须通过 runtime schema parse；schema invalid 时 work item 标记为 `FAILED_EXECUTOR`。
- schema invalid、budget exceeded、context invalid 时不得写 publishable patch。
- `MergeGuard` 和 `Validator` 仍然是 publish 前的唯一安全门。
- loop artifact 是审计/eval 输入，不参与 skip/replan 判定。

### 4.1 Agent Runtime Interface

新增统一接口：

```ts
interface WorkItemAgentRuntime<I = unknown, O = PatchBundle | QualityFindings> {
  type: WorkItem["type"];
  buildContext(input: WorkItemRuntimeInput): Promise<I>;
  runLoop(input: WorkItemLoopInput<I>): Promise<WorkItemLoopResult<O>>;
}
```

运行时输入：

```ts
interface WorkItemRuntimeInput {
  runId: string;
  workspaceRoot: string;
  store: AgentRunsStore;
  workItem: WorkItem;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}
```

Loop 输出：

```ts
interface WorkItemLoopResult<O> {
  output: O;
  report: WorkItemAgentLoopReport;
}
```

### 4.1.1 Context Contract

每类 work item 必须声明自己的 scoped context：

```ts
interface WorkItemContextContract {
  workItemType: WorkItem["type"];
  allowedWorkspaceReads: string[];
  allowedArtifactReads: string[];
  requiredShas: Record<string, string>;
  forbiddenReads: string[];
}
```

规则：

- Note agent 只能读取 `workItem.sourcePaths` 和 `workItem.targetPaths` 当前 sha。
- Topic index agent 只能读取同 topic 下已发布 note target，来源优先为本 run 已发布 work-item artifacts，其次是 workspace current state。
- MOC agent 只能读取已发布 topic index paths。
- Quality review agent 只能读取已发布 note target 和 validation artifacts。
- Agent runtime 除 `agent-loop` artifact 外，不能直接写 domain artifacts；patch/quality artifact 由 runtime boundary 写入。
- context missing 且 plan/context 不再成立时映射为 `NEEDS_REPLAN`。
- context missing 但只是 agent 输入不完整时映射为 `FAILED_EXECUTOR`。

第一版显式不实现 `MERGE_USER_EDITED_NOTE` agent runtime；如果 planner 产生该类型，runtime 必须返回 `NEEDS_REPLAN` 或等待人工批准，不得自动 merge。

### 4.2 Unified Loop Artifact

统一写入：

```text
.agent-runs/<runId>/agent-loop/<workItemId>.json
```

Contract：

```ts
interface WorkItemAgentLoopReport {
  schemaVersion: "work-item-agent-loop.v1";
  runId: string;
  workItemId: string;
  workItemType: WorkItem["type"];
  agentNode: "note" | "topic-index" | "moc" | "quality-review";
  status: "SUCCEEDED" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  budget: {
    maxIterations: number;
    maxProviderCalls: number;
    timeoutMs: number;
  };
  usage: {
    iterations: number;
    providerCalls: number;
  };
  steps: Array<{
    name: string;
    kind: "draft" | "self_check" | "repair" | "validate" | "summarize";
    status: "SUCCEEDED" | "SKIPPED" | "FAILED";
    issues: string[];
    repairedIssues: string[];
    message?: string;
  }>;
  repairedIssues: string[];
  remainingIssues: string[];
  outputRef?: {
    kind: "patch" | "quality";
    path: string;
  };
}
```

### 4.3 Budget Rules

默认预算：

| Work item type | maxIterations | maxProviderCalls | timeoutMs |
| --- | ---: | ---: | ---: |
| `CREATE_TOPIC_NOTE` | 2 | 1 | provider timeout |
| `REWRITE_TOPIC_NOTE` | 2 | 1 | provider timeout |
| `MERGE_USER_EDITED_NOTE` | 0 | 0 | 0 |
| `MAINTAIN_TOPIC_INDEX` | 1 | 0 | 5000 |
| `MAINTAIN_MOC` | 1 | 0 | 5000 |
| `QUALITY_REVIEW` | 1 | 0 | 5000 |

规则：

- 本阶段不允许 agent loop 无限循环。
- Provider call 默认最多 1 次；修复必须优先是确定性修复。
- 超预算写 `agent-loop` artifact，work item 标记 `FAILED_EXECUTOR`。
- timeout/retry 仍沿用现有 work item attempts 语义。
- `MERGE_USER_EDITED_NOTE` 第一版不自动执行，预算为 0，必须转人工或 replan。

### 4.4 Agent Nodes

#### Note Agent

现有 note quality loop 升级到 unified artifact：

```text
provider draft
  -> self-check
  -> deterministic repair
  -> self-check
  -> PatchBundle
```

保留现有 repair set：

- `TOPIC_NOTE_WEAK_RELATIONS`

新增可选 issue，不在第一批修复：

- `TOPIC_NOTE_SUMMARY_TOO_THIN`
- `TOPIC_NOTE_SOURCE_TRACKING_WEAK`
- `TOPIC_NOTE_KEY_CONTENT_TOO_THIN`

这些先进入 report，不自动修复。

#### Topic Index Agent

从模板输出升级为 loop artifact：

```text
collect published note targets
  -> draft index
  -> self-check links
  -> PatchBundle
```

确定性检查：

- index 只能链接同 topic 下已发布 note。
- link target 必须存在或在本 run patch 中创建。
- index 不能链接 raw 文件。

#### MOC Agent

从模板输出升级为 loop artifact：

```text
collect topic indexes
  -> draft MOC
  -> self-check topic coverage
  -> PatchBundle
```

确定性检查：

- MOC 只能链接 topic index。
- 每个有 index 的 topic 最多一个 MOC link。
- topic slug 排序稳定。

#### Quality Review Agent

从简单 `issues: string[]` 升级为带 evidence 的 findings：

```ts
interface QualityFinding {
  issue: string;
  severity: "warning";
  workItemId?: string;
  targetPath?: string;
  evidence: string;
}
```

本阶段只支持 warning，不阻断 publish；后续如果要支持 blocker，必须单独改 spec、Validator 和 publish semantics。

## 5. Failure / Replan Semantics

新增 loop-level failure reason：

```ts
type AgentLoopFailureReason =
  | "LOOP_BUDGET_EXCEEDED"
  | "LOOP_OUTPUT_SCHEMA_INVALID"
  | "LOOP_REPAIR_UNSAFE"
  | "LOOP_PROVIDER_FAILED"
  | "LOOP_CONTEXT_MISSING";
```

映射：

| Failure | Work item status | Retryable |
| --- | --- | --- |
| `LOOP_BUDGET_EXCEEDED` | `FAILED_EXECUTOR` | false |
| `LOOP_OUTPUT_SCHEMA_INVALID` | `FAILED_EXECUTOR` | false |
| `LOOP_REPAIR_UNSAFE` | `BLOCKED_BY_VALIDATOR` | false |
| `LOOP_PROVIDER_FAILED` | provider classification result | depends |
| `LOOP_CONTEXT_MISSING` | `NEEDS_REPLAN` or `FAILED_EXECUTOR` | false |

`NEEDS_REPLAN` 仍只用于 plan/context 不再成立，不用于普通质量失败。

### 5.1 Durable Failure Contract

本阶段需要扩展 `WorkItem.attempts[]`，使恢复不依赖 loop artifact 判定 retry 语义：

```ts
interface WorkItemAttempt {
  attempt: number;
  status: WorkItemStatus;
  message: string;
  failureSource?: "provider" | "loop" | "validator" | "merge" | "context";
  failureReason?: ProviderErrorClass | AgentLoopFailureReason | string;
  retryable?: boolean;
}
```

规则：

- provider failure 由 provider classification 决定 retryable。
- loop schema invalid / budget exceeded 默认 non-retryable。
- validator / merge blocked 默认 non-retryable。
- retryable 必须持久化到 `work-items/<workItemId>.json` 的 latest attempt。
- resume 判断 retry 时优先读 latest attempt 的 `retryable`，不能只看 `status`。
- 如果拒绝修改 `WorkItem` public contract，则本阶段必须暂停；不能用 loop artifact 代替 durable retry fact。

### 5.2 Artifact Atomic Write Protocol

`.agent-runs` 作为恢复事实源时，所有 JSON artifacts 必须原子写入：

```text
write <path>.tmp
fsync best-effort when available
rename <path>.tmp -> <path>
```

写入顺序：

```text
agent-loop/<id>.json
  -> patches/<id>.patch.json OR quality/<id>.json
  -> validation/<id>.json
  -> workspace publish
  -> work-items/<id>.json
  -> eval/report
```

恢复/报告对中间态的处理：

| State | Handling |
| --- | --- |
| `PUBLISHED` but missing patch | report corrupt run, do not skip silently |
| patch exists but loop missing | report `missingLoopArtifacts`, do not count loop success |
| loop failed but patch exists | ignore patch for publish, mark work item failed |
| corrupt JSON artifact | report `corruptLoopArtifacts` / `corruptArtifacts`, fail current run before publish |
| workspace published but work item status missing | recompute from patch sha and current workspace sha, report recovery warning |

## 6. Report Upgrade

`eval.json` 增加：

```ts
agentLoop: {
  total: number;
  succeeded: number;
  failed: number;
  missingLoopArtifacts: string[];
  corruptLoopArtifacts: string[];
  repairedIssues: Record<string, number>;
  remainingIssues: Record<string, number>;
  providerCalls: number;
}
```

`agentLoop.total` 必须等于本 run 实际执行的 work item 数，不是等于已存在 loop artifact 数。缺失或损坏必须显式进入 `missingLoopArtifacts` / `corruptLoopArtifacts`。

`report.md` 增加：

```text
- Agent loops: <succeeded>/<total> succeeded
- Repaired issues:
  - TOPIC_NOTE_WEAK_RELATIONS: 3
- Remaining issues:
  - TOPIC_NOTE_SUMMARY_TOO_THIN: 1
```

## 7. Acceptance

本阶段验收必须覆盖：

- unit: unified loop artifact schema builder。
- unit: runtime boundary rejects invalid loop output before writing publishable patch。
- unit: budget exceeded records non-retryable loop failure。
- unit: context contract rejects out-of-scope reads。
- unit: note agent writes `work-item-agent-loop.v1` artifact while preserving existing repair behavior。
- unit: topic index agent writes loop artifact and rejects raw links。
- unit: MOC agent writes loop artifact and preserves stable sorted topic links。
- unit: quality review emits structured findings with evidence。
- integration: one full organize workflow produces loop artifacts for every executed work item。
- integration: `eval.agentLoop.total` equals exact executed work item count。
- integration: missing/corrupt loop artifact is reported explicitly and not counted as success。
- integration: `eval.json.agentLoop` aggregates repaired/remaining issues。
- integration: loop artifact does not affect resume skip/replan decisions。
- integration: non-retryable loop failure is not retried by artifact resume。
- failure harness: invalid loop output marks work item failed without publishing。
- full: `npm test`、`npm run typecheck`、`git diff --check`。

## 8. Implementation Slices

### Slice A: Unified Loop Artifact

- Add `WorkItemAgentLoopReport` type.
- Add writer/helper.
- Migrate note quality loop artifact from `note-quality-loop.v1` to `work-item-agent-loop.v1` or wrap it as compatible output.
- Add runtime schema validation for loop report and output.

### Slice B: Agent Runtime Registry

- Add runtime registry keyed by `WorkItem["type"]`。
- `execute-phase-node` delegates per work item instead of branching by type.
- Preserve existing behavior and artifact paths.
- Add `ContextContract` per work item type.

### Slice C: Topic Index / MOC Loop Artifacts

- Wrap current deterministic agents with loop reports.
- Add self-check evidence for links and topic coverage.

### Slice D: Structured Quality Findings

- Upgrade `QualityFindings` from `string[]` to structured findings.
- Keep report backward-compatible by deriving `qualityIssues`.

### Slice E: Eval / Report Aggregation

- Add `agentLoop` section to `eval.json`.
- Add agent loop summary to `report.md`.
- Exact-match executed work item ids against loop artifact ids.

### Slice F: Durable Failure / Resume Hardening

- Extend `WorkItem.attempts[]` with `failureSource`、`failureReason`、`retryable`。
- Resume retry decisions use latest attempt retryability.
- Add fixtures for non-retryable loop failure and corrupt/missing artifacts.

## 9. Stop Conditions

必须暂停重新对齐：

- 需要修改 `WorkItem` public contract。
- 需要改变 resume/publish 判定事实源。
- 需要让 loop artifact 参与 skip/replan。
- 需要引入真实外部 provider 调用作为默认测试。
- repair 规则需要引入新业务事实或猜测用户意图。
- quality finding 从 warning 升级 blocker。

## 10. Review Focus

- Agent runtime 是否仍然只输出 `PatchBundle` / `QualityFindings`。
- Loop artifact 是否只是审计/eval，不污染 domain truth。
- Budget 是否阻止无限 agent loop。
- Topic index/MOC 是否不会因为 agent loop 放宽路径边界。
- Structured quality findings 是否能帮助下一步 replan，而不是制造噪声。
