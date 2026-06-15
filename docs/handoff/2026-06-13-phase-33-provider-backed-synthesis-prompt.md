# Phase 33 Handoff Prompt: Provider-backed Synthesis

Copy this prompt into the next Codex session.

```text
继续在 /Users/didi/Documents/personal/My-Workflow-Agent 工作。默认中文协作。

请严格按当前项目 SOP 执行：

1. 先使用 adaptive-dev-workflow 路由。
2. 这是 Large 级别实现任务，必须遵守 TDD：
   - 先写失败测试；
   - 确认红测失败原因正确；
   - 再写实现；
   - focused tests 绿后继续。
3. 使用当前 Phase 33 spec 和 plan：
   - docs/architecture/open-agent-provider-backed-synthesis-spec.md
   - docs/superpowers/plans/2026-06-13-open-agent-provider-backed-synthesis.md
   - docs/changes/2026-06-13-open-agent-provider-backed-synthesis.md
4. 不要回滚已有未提交改动。当前仓库有大量 Phase 25-32 的 tracked/untracked 文件，这是预期状态。
5. 不要把 token、cookie、API key 写入代码、文档、artifact、stdout/stderr 或测试 fixture。
6. 真实 MiMo 调用只能通过 hidden stdin/env：
   - baseUrl: https://token-plan-cn.xiaomimimo.com/v1
   - model: mimo-v2.5
   - 命令中不要出现 token 明文。

当前已完成到 Phase 32：

- OpenAgentGraph 已存在：
  - src/runtime/open-agent/open-agent-graph.ts
  - src/runtime/open-agent/open-agent-provider.ts
  - src/runtime/open-agent/open-agent-state.ts
  - src/runtime/open-agent/open-agent-artifacts.ts
  - src/runtime/open-agent/nodes/*
- provider-backed plan/action 已接入：
  - createOpenAiCompatibleOpenAgentProvider
  - selectOpenAgentProvider
  - parseOpenAgentNextAction
- MiMo provider-backed llm-graph smoke 已通过：
  - status PASSED
  - graph status SUCCEEDED
  - realExternalCall true
  - providerCalls 2
  - raw refs open-agent-plan-1 和 open-agent-next-action-2
  - request artifacts Authorization 为 [REDACTED]
  - token search under .agent-runs/open-agent 无命中
  - no workspace write
- 当前明确边界：
  - provider 已驱动 plan/action；
  - final answer/draft/candidate content 仍由 deterministic synthesize-node 生成；
  - graph runner 仍是顺序 runner，不是 LangGraph StateGraph；
  - provider 不能 publish workspace。

本阶段目标：

实现 provider-backed synthesis：

- 扩展 OpenAgentProvider.synthesize()；
- 新增 parseOpenAgentSynthesisOutput；
- provider-backed synthesis 支持 ANSWER_ONLY、DRAFT_ARTIFACT、CANDIDATE_PATCH；
- SynthesizeNode 在 provider 支持 synthesize 时调用 provider；
- fake/fixture provider 保持 deterministic fallback；
- candidate patch 的 target path/content sha/publishable false/handoff 仍由 deterministic runtime 控制；
- draft 必须包含 Draft only marker；
- answer/draft/candidate 必须包含 grounding refs；
- raw synthesize request/response 写入 .agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/request.json 和 response.json；
- graph result/report 记录 synthesis metadata；
- no workspace write。

执行顺序：

1. 读 docs/architecture/open-agent-provider-backed-synthesis-spec.md。
2. 读 docs/superpowers/plans/2026-06-13-open-agent-provider-backed-synthesis.md。
3. 按 plan Task 1-8 执行。
4. 每个行为改动先写红测。
5. focused tests 至少跑：
   /Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts tests/unit/open-agent-real-smoke.test.ts
6. 完成后跑：
   /Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
   /Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
   git diff --check
7. 跑真实 MiMo smoke：
   - 使用 temp fixture workspace under /private/tmp
   - cp tests/fixtures/workspaces/basic-raw-mirror/. 到 temp workspace
   - hidden stdin 输入 token
   - 执行：
     /Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --import tsx src/cli/open-agent-smoke.ts --provider mimo-open-agent-smoke --mode llm-graph --workspace-root <temp> --base-url https://token-plan-cn.xiaomimimo.com/v1 --model mimo-v2.5 --output-policy ANSWER_ONLY --execute-real
8. 真实 smoke 如果失败：
   - 不要猜；
   - 读 redacted raw response artifact；
   - 分类失败原因；
   - 先加回归测试；
   - 再修 parser/prompt/self-check；
   - focused tests 绿后重跑真实 smoke。
9. 更新 docs/reports/runtime-work-item-execution-resume-delivery.md，记录：
   - focused/full/typecheck/diff；
   - real MiMo smoke；
   - providerCalls；
   - redaction evidence；
   - no workspace write evidence；
   - remaining boundary。

验收标准：

- provider-backed synthesis 至少让 ANSWER_ONLY 的真实 MiMo llm-graph smoke 通过；
- tests cover draft/candidate safety；
- providerCalls >= 3；
- raw synthesize request redacted；
- token search under .agent-runs/open-agent no match；
- no knowledge-base target file written；
- full tests/typecheck/diff pass。

不要做：

- 不要接前端。
- 不要改 publish/resume truth source。
- 不要让 provider 直接写 PatchBundle 或调用 publisher。
- 不要把 graph runner 替换为 LangGraph StateGraph，除非 Phase 33 全部完成后还有余力并另写 spec。
- 不要把 mock/fake 测试描述成真实 provider 链路。
```
