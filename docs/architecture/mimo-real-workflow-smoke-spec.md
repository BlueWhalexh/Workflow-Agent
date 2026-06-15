# MiMo Real Workflow Smoke Spec

> 状态：Phase 22 spec。目标是在一次性 fixture workspace 上执行真实 MiMo provider 的完整 organize workflow smoke，验证真实 provider 输出能穿过 agent loop、PatchBundle、MergeGuard、Validator、Publisher、eval/report。本文不记录任何 API key/token 值。

## Scenario

真实 provider smoke 已证明：

- `https://token-plan-cn.xiaomimimo.com/v1/models` 可访问。
- `mimo-v2.5` 可用于 `/chat/completions`。
- 旧 fixture/open-source 模型名 `XiaomiMiMo/MiMo-7B-RL-0530` 不被真实 API 支持。

但 provider smoke 只调用 `generateNote`，不证明真实模型输出能通过完整 workflow。

本阶段验证：

```text
temp fixture workspace
  -> runOrganizeWorkflow(provider: mimo-real, model: mimo-v2.5)
  -> real provider call
  -> note quality loop
  -> work-item-agent-loop.v1
  -> PatchBundle
  -> MergeGuard / Validator
  -> Publisher
  -> eval.json / report.md
```

## Safety Rules

- 只在 `mkdtemp` 创建的临时 workspace 上运行。
- API key 只通过 stdin 进入当前进程内存，不写入 env 文件、artifact、trace、docs 或 command args。
- 不在用户真实 workspace 上发布。
- 不把 raw provider response 写入 artifact，除非显式 opt-in raw capture。
- 最终报告只记录 provider、model、status、artifact shape 和验证结果，不记录 token。

## Acceptance

- workflow result 为 `SUCCEEDED_WITH_WARNINGS` 或明确失败原因。
- 如果失败，必须记录失败发生在 provider、loop、validator、merge、publish 还是 report。
- 如果通过：
  - `.agent-runs/<runId>/eval.json` 存在；
  - `eval.agentLoop.providerCalls > 0`；
  - `eval.agentLoop.missingArtifacts == []`；
  - `eval.agentLoop.corruptArtifacts == []`；
  - 至少一个 note target 不再是 raw mirror；
  - report 写出 `Agent loop artifacts` 覆盖摘要。
- 默认 automated test suite 仍不执行真实外部调用。

## Result

最终真实 workflow smoke 通过：

- provider: `mimo-real`
- model: `mimo-v2.5`
- status: `SUCCEEDED_WITH_WARNINGS`
- provider calls: `3`
- agent loop reports: `8/8`
- missing/corrupt/budgetExceeded: `[]`
- all note/index/MOC work items published
- quality review succeeded

真实调用暴露并已修复的 deterministic quality gap：

- common Chinese heading variants: `总结`、`源追踪`、`源信息`、`源文件追踪`、`关键内容`
- common English heading variants: `Summary`、`Source Tracking`、`Key Content`、`Related Links`
- placeholder related-link lines containing `待补充`

## Boundary

- 这不是生产 workspace 迁移。
- 这不验证 DeepSeek / Claude Code。
- 这不验证高并发、rate limit、fallback 或长任务恢复。
- 这不把真实 MiMo 设为默认 provider。
