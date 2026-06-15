# Agent Execution Quality Spec

> 状态：Phase 20 spec。目标是把 note agent 从“只包装 provider 输出”升级为“bounded draft -> self-check -> deterministic repair -> patch”的可审计 agent node。本文定义质量 loop 的边界，不把 prompt 当作质量兜底。

## Goal

每个 note work item 在生成 `PatchBundle` 前，必须产出 agent-readable loop artifact：

```text
.agent-runs/<runId>/agent-loop/<workItemId>.json
```

当前 loop：

```text
GENERATE_NOTE
  -> SELF_CHECK
  -> REPAIR_NOTE when deterministic and low-risk
  -> SELF_CHECK_AFTER_REPAIR
  -> PatchBundle
  -> MergeGuard
  -> Validator
```

## Rules

- Provider 只产出 draft，不直接决定 publish。
- Agent node 可以执行 bounded local loop，但最终输出仍只能是 `PatchBundle`。
- Deterministic repair 只能修复低风险、可证明不引入业务事实的质量问题。
- Structural hard blockers 不能由 agent loop 伪装修复，必须继续交给 `Validator` 阻断。
- Loop artifact 是审计和 eval 输入，不参与 resume/publish 判定。

## Current Repair Set

| Issue | Detection | Repair |
| --- | --- | --- |
| `TOPIC_NOTE_WEAK_RELATIONS` | topic note draft missing `## 相关链接` | append `## 相关链接` with `暂无相关链接。` |

Repair guard：

- draft 必须有 title。
- draft 必须有 `## 摘要`。
- draft 必须有 `## 来源追踪`。

如果 guard 不满足，loop 只记录 remaining issue，不修复。

## Artifact Contract

```ts
interface NoteQualityLoopReport {
  schemaVersion: "note-quality-loop.v1";
  workItemId: string;
  steps: Array<{
    name: "GENERATE_NOTE" | "SELF_CHECK" | "REPAIR_NOTE" | "SELF_CHECK_AFTER_REPAIR";
    status: "SUCCEEDED" | "SKIPPED";
    issues: string[];
    repairedIssues: string[];
  }>;
  repairedIssues: string[];
  remainingIssues: string[];
}
```

## Acceptance

- unit: missing `## 相关链接` is repaired and loop steps are recorded.
- unit: structurally invalid draft is not falsely repaired.
- unit: note agent writes `agent-loop/<workItemId>.json`.
- integration: workflow with weak-relations fixture publishes repaired note and validation has no `TOPIC_NOTE_WEAK_RELATIONS`.
- full: `npm test`、`npm run typecheck`、`git diff --check` pass.

## Upgrade Conditions

后续只有满足以下条件，才扩大 repair set：

- repair 不需要引入新的外部事实。
- repair 可以用确定性规则证明。
- repair 后仍经过 `MergeGuard` 和 `Validator`。
- 新增 issue 必须进入 unit + workflow integration 测试。
