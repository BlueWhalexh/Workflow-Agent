# Work Item Runtime Resume Spec

> 状态：Phase 7/8/9 runtime spec。本文定义同一 `runId` 下 work item 级恢复、跳过、重规划阻断和 retry audit 行为。

## Goal

同一 `runId` 再次运行时，runtime 必须基于 `.agent-runs` artifacts 和 workspace 当前 sha 做恢复判断，避免重复 provider 调用，也避免覆盖用户手动改动。

## Runtime Decisions

每个写入型 work item 在执行前必须读取：

- `work-items/<workItemId>.json`
- `patches/<workItemId>.patch.json`
- 当前 workspace target content sha

然后转成三态：

| Decision | Condition | Runtime behavior |
| --- | --- | --- |
| `SKIP` | stored status is `PUBLISHED` and current target sha equals patch content sha | 不调用 provider，不重写 patch，不追加 trace |
| `NEEDS_REPLAN` | stored status is `PUBLISHED` but current target sha differs from patch content sha | 标记 work item `NEEDS_REPLAN`，停止当前 run，不覆盖用户改动 |
| `RUN` | no completed artifact, retryable failure, validator blocked, or non-published status | 按当前 provider/runtime 重新执行 |

## Plan Reuse

同一 `runId` 代表继续旧 run。`planNode` 如果发现已有 `plan.json`，必须复用旧 plan，不得覆盖已有 work-item artifacts。

新 workspace 规划必须使用新的 `runId`。

## Phase 8: Replan Failure Report

当 runtime 因 `NEEDS_REPLAN` 停止时，最终 report 必须可操作：

- 明确 `Status: FAILED`。
- 明确 `Error: WORK_ITEM_NEEDS_REPLAN`。
- 列出至少一个 `NEEDS_REPLAN` work item id。
- 不调用 provider。
- 不覆盖用户改动。

## Phase 9: Retryable Failed Work Item Resume

当 stored work item 是 retryable failure 时，runtime 必须允许同一 `runId` 继续执行该 work item。

Retryable statuses:

- `FAILED_TIMEOUT`
- retryable `FAILED_EXECUTOR`
- `BLOCKED_BY_VALIDATOR`，用于用户或 provider 配置修复后重新生成 patch

Retry 行为：

- 复用旧 `plan.json`。
- 不覆盖旧 failed work-item attempts。
- 使用当前 `providerRuntime` 重新执行 provider call。
- 成功 publish 后，work item status 改为 `PUBLISHED`。
- attempts 必须保留失败记录，并追加成功记录。
- retry 成功后可以继续 Phase B / Phase C。

## Non-Goals

- 不自动生成新的 plan。
- 不自动 merge 用户改动。
- 不把 `NEEDS_REPLAN` 当作成功或 warning。
- 不接真实 provider。

## Acceptance

- 同 `runId` 复跑且 sha 匹配时，provider trace 行数不增加。
- 同 `runId` 复跑且用户修改 target 时，run 返回 `FAILED` / `WORK_ITEM_NEEDS_REPLAN`。
- 被修改的 target 内容保留。
- 对应 work item artifact status 为 `NEEDS_REPLAN`。
- failure report 中列出该 work item id。
- timeout failure 后使用同一 `runId` 和可用 provider 重试，work item 可以变为 `PUBLISHED`。
- retry 成功后 attempts 至少包含失败 attempt 和成功 attempt。
