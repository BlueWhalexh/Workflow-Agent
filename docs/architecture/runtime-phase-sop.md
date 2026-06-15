# Runtime Phase Delivery SOP

> 状态：当前执行约定。后续 runtime phase 必须按本文循环推进，除非遇到 Stop Conditions。

## Goal

每个 phase 都必须先收敛目标、边界和验收，再实现。不能只靠对话记忆推进，也不能让用户逐步催促下一步。

## Required Loop

每个 phase 按固定顺序执行：

1. 读本 SOP、最新 architecture spec、implementation plan、change note 和 delivery report。
2. 使用 `adaptive-dev-workflow` 路由，明确 Tiny / Small / Medium / Large。
3. 写或更新 architecture spec。
4. 如果架构方向、产品边界、workflow 类型、public contract 或安全边界发生变化，新增或更新 `docs/changes/*.md`。
5. 写或更新 implementation plan。
6. 如果没有触发 Stop Conditions，计划写完后直接执行，不停在“等待用户继续”。
7. 写 RED 测试，确认当前实现失败且失败原因正确。
8. 最小实现，让 focused test 变 GREEN。
9. 跑相关 focused tests。
10. 跑 full verification。
11. 更新交付说明或 phase doc。
12. 归档本 phase 的 spec / plan / change note / report 状态。
13. 进入下一 phase，除非触发 Stop Conditions。

## Plan Execution Rule

Spec 和 implementation plan 是执行入口，不是默认暂停点。写完计划后：

- 没有 Stop Condition 时，立即进入 RED 测试和实现。
- 如果涉及 public contract、workspace contract、状态机外部语义、安全边界、真实凭证、生产依赖或架构取舍，先暂停说明。
- 如果用户已经明确要求“写完计划直接执行”，不要再把计划当作确认门。
- 如果实际方案偏离计划，暂停并更新 plan / spec 后再继续。

## Phase Archive Rules

每个 phase 完成后必须归档，不允许只留下散落的 active spec / plan：

- architecture spec：将状态更新为 `implemented` / `superseded` / `blocked`，并指向 delivery report。
- implementation plan：补 `Execution Status` 或 `Archive` 小节，记录完成状态、关键验证命令、未完成项和 delivery report 链接。
- change note：如果 decision 已实施，补充 implementation phase / evidence；如果被替代，标为 superseded 并指向替代文档。
- delivery report：追加 phase 小节，区分 fake / fixture / injected / real external call evidence，不把 mock 描述成真实链路。
- next phase：只有在没有 Stop Condition 时才创建或更新下一 phase 的 spec / plan / change note；未执行的 handoff/prompt 要标明 pending，不得伪装成已完成。
- 归档优先采用原文件内状态更新和互链；只有文档被新 canonical spec 替代且不再作为 current truth 时，才移动到未来的 `docs/archive/` 结构。

## Default Next-turn Protocol

当用户说“继续”“下一阶段”“往下走”“别让我重复 SOP”或等价表达时，默认执行：

1. 读 `AGENTS.md` 和本 SOP。
2. 读最新 phase spec、plan、change note、delivery report。
3. 用 `adaptive-dev-workflow` 路由。
4. 如果是行为改动，遵守 TDD：先红测、确认失败原因、再实现、focused tests 绿后继续。
5. 如果涉及 provider，先跑 fake / fixture / injected-fetch tests，再跑 real smoke。
6. 跑 full verification。
7. 更新 delivery report。
8. 如果没有 Stop Condition，主动写下一 phase 的 spec / plan / change note。

## Evidence Rules

- 行为变化必须有自动化测试。
- provider 相关验收必须区分 fake、fixture、harness、real external call。
- full verification 至少包含：
  - `npm test`
  - `npm run typecheck`
  - `git diff --check`
- 如果只改文档，可以用 `git diff --check` 作为 focused validator，但不能把它描述成 runtime 验证。

## Real Provider Smoke Protocol

真实 provider smoke 只在 phase plan 要求或用户明确要求时执行。默认使用临时 fixture workspace，不碰用户真实知识库。

必须记录以下证据：

- temp fixture workspace under `/private/tmp`；
- command 使用 `--execute-real`；
- API key 来自 hidden stdin 或当前进程临时 env；
- raw request artifact 中 `Authorization` 为 `[REDACTED]`；
- token search under `.agent-runs/open-agent` 返回 no match；
- 目标 workspace 文件未被写入；
- result/report 记录 providerCalls、raw provider refs、realExternalCall。

如果真实调用失败：

1. 先分类：sandbox/network、HTTP/auth、schema drift、budget、self-check。
2. 如果是 sandbox/network 且命令对任务必要，按审批规则用同样命令形态重跑。
3. 如果 raw response 已存在，先读 redacted artifact，再加回归测试，再修 parser/prompt/self-check。
4. 不把 mock/fake 测试描述成真实 provider 链路。

## Token Handling

允许：

- macOS Keychain entry configured by `src/cli/open-agent-smoke.ts --configure-mimo-keychain --api-key-stdin`；
- hidden stdin；
- 当前命令或当前 shell session 的临时 env；
- 单测中的 fake value，例如 `test-api-key`。

禁止：

- 把真实 token 写入 repo `.env`、代码、文档、fixture、snapshot 或 report；
- 把真实 token 放进命令参数；
- 把真实 token 打印到 stdout/stderr；
- 把真实 token 写入 raw provider artifact、trace、report 或 `.agent-runs`。

Keychain 配置入口：

```bash
read -rs MIMO_API_KEY
printf '%s\n' "$MIMO_API_KEY" | \
  node --import tsx src/cli/open-agent-smoke.ts \
  --provider mimo-open-agent-smoke \
  --configure-mimo-keychain \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5
unset MIMO_API_KEY
```

配置后，`open-agent-smoke.ts` 会在当前 env 缺少 `MIMO_API_KEY` / `MIMO_BASE_URL` / `MIMO_MODEL` 时尝试从 Keychain 补齐。测试可设置 `MY_WORKFLOW_AGENT_DISABLE_KEYCHAIN=1` 禁用该行为。

如果用户把 token 明文发到聊天里，必须提示轮换风险。只有在用户明确要求继续测试时，才可把该 token 通过 stdin/env 用于本次本地真实 smoke；报告仍不能记录 token 值。

## Change Documentation Rules

当发生以下变化时，必须写 `docs/changes/<date>-<topic>.md`：

- 从固定 workflow 扩展到 hybrid agent capability。
- public SDK / backend command contract 改变。
- workspace 三层结构或落库规则抽象改变。
- provider real smoke / workflow smoke 验收策略改变。
- 安全边界、approval、publisher、validator、resume 语义改变。

change 文档必须包含：

- Context；
- Decision；
- Consequences；
- Affected docs；
- Follow-up phase。

## Stop Conditions

以下情况必须暂停说明，不自动推进：

- 需要新的真实 API key、token、cookie 或账号态，且用户尚未授权使用。
- 需要联网调用真实 provider，但当前 phase plan 没要求且用户未明确要求。
- 需要安装依赖或修改生产依赖。
- 需要修改 public contract、workspace contract、状态机外部语义或安全边界。
- 连续两轮 focused/full verification 失败仍无法定位根因。
- 发现当前 spec 与代码事实冲突。

## Current Phase Status

已完成或进行中：

- Phase 31: OpenAgentGraph sequential runner baseline。
- Phase 32: provider-backed plan/action。
- Phase 33: provider-backed synthesis；real MiMo `ANSWER_ONLY` llm-graph smoke passed。
- Phase 34: OpenAgentGraph StateGraph runner migration implemented；real MiMo `ANSWER_ONLY` llm-graph smoke passed。
- Phase 35: OpenAgentGraph checkpoint/resume boundary planned。

当前推荐下一阶段：

- Agent SDK MVP Phase 1: 按 `docs/architecture/agent-sdk-mvp-phase1-spec.md`、`docs/changes/2026-06-13-agent-sdk-mvp-phase1.md` 和 `docs/superpowers/plans/2026-06-13-agent-sdk-mvp-phase1.md` 执行；checkpoint/resume 暂后置。
