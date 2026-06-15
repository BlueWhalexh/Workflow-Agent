# Agent SDK MVP Phase 1 Change Note

Date: 2026-06-13

## Context

Phase 34 已完成 OpenAgentGraph StateGraph runner 迁移和真实 MiMo smoke。下一步用户明确希望一次性把 SDK 一期成果做出来，而不是继续优先推进 checkpoint/resume。

当前 SDK 已经暴露多个低层入口，但后端调用方仍需要理解 fixed workflow、deterministic open-agent、StateGraph open-agent、confirmation 的不同 result shape。这会让后端接入过早绑定 runtime internal 细节。

## Decision

将下一阶段切到 Agent SDK MVP Phase 1：

- 新增统一 public SDK 入口 `runAgent(request)` / `agent.runAgent(request)`。
- 新增统一返回 schema `agent-sdk-run.v1`。
- 后端一期推荐只消费 `route`、`status`、`outputKind`、`output`、`artifacts`、`diagnostics`。
- `.agent-runs` 继续作为一期 artifact-backed persistence；暂不引入 DB/backend server。
- OpenAgent candidate patch 继续只写 artifact，不直接写目标 workspace 文件。

## Consequences

- Backend adapter 可以先基于 SDK result 做 mock integration，不需要导入 `src/runtime/*`。
- SDK 需要承担 status/output/artifact normalization，避免后端误判 answer、draft、candidate patch、confirmation、workflow report。
- 真实 provider 仍只作为受控 smoke，不作为普通单测依赖。
- checkpoint/resume 仍然重要，但不作为本次优先 phase。

## Affected Docs

- `docs/architecture/agent-sdk-mvp-phase1-spec.md`
- `docs/superpowers/plans/2026-06-13-agent-sdk-mvp-phase1.md`
- `docs/handoff/2026-06-13-agent-sdk-mvp-phase1-prompt.txt`
- `docs/architecture/runtime-phase-sop.md`

## Follow-up Phase

SDK MVP Phase 1 完成后，再决定是接 backend adapter，还是回到 OpenAgent checkpoint/resume boundary。
