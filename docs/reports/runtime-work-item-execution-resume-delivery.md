# Runtime Work Item Execution And Resume Delivery

> 日期：2026-06-12
> 范围：Phase 5-20
> 状态：fake / fixture / harness 验证通过；MiMo opt-in real smoke 已执行；默认 test suite 不执行真实外部 LLM 调用。

> Java backend phase-one tracking：`docs/reports/java-backend-phase-one-completion-audit.md` 是当前后端一期完成度、剩余缺口和后续 implementation gate 的索引。本文后续 Java Phase J1-J25A 条目保留具体交付证据。

## Delivered

### Phase 5: Multi Work Item Execution

- LangGraph execute phase 从单个 `REWRITE_TOPIC_NOTE` 扩展为执行计划内多个 work items。
- Phase A 支持 `CREATE_TOPIC_NOTE` 和 `REWRITE_TOPIC_NOTE`。
- Phase B 支持 `MAINTAIN_TOPIC_INDEX`。
- 每个写入型 work item 都生成 `patches/<workItemId>.patch.json` 和 `validation/<workItemId>.json`。

### Phase 6: Phase C Global Review

- `MAINTAIN_MOC` 生成 `PatchBundle`，通过 `MergeGuard`、`Validator`、`Publisher` 写入 `knowledge-base/moc.md`。
- `QUALITY_REVIEW` 读取已发布 note 内容，写入 `quality/<workItemId>.json`。
- `reportNode` 将 validation issues 和 quality findings 汇总进 `eval.json`。

### Phase 7: Runtime Resume / Skip / Needs-Replan

- 同一 `runId` 会复用已有 `plan.json`，不会覆盖旧 work-item artifacts。
- 已 `PUBLISHED` 且 workspace target sha 匹配 patch content sha 的 work item 会 `SKIP`。
- 已 `PUBLISHED` 但 target sha 不匹配的 work item 会标记 `NEEDS_REPLAN`，并停止当前 run。
- `NEEDS_REPLAN` 不调用 provider，不追加 trace，不覆盖用户改动。

### Phase 8: Replan Failure Report

- `WORK_ITEM_NEEDS_REPLAN` 的失败报告会列出具体 `NEEDS_REPLAN` work item id。
- 用户可以从 `report.md` 直接定位需要重新规划的 item。

### Phase 9: Retryable Failed Work Item Resume

- 同一 `runId` 下，`FAILED_TIMEOUT` 等 retryable work item 可以用新的 `providerRuntime` 重试。
- retry 成功后 work item status 会变为 `PUBLISHED`。
- attempts 会保留之前的失败记录，并追加成功发布记录。
- retry 成功后可以继续 Phase B / Phase C。

### Phase 10: DeepSeek Real Adapter Smoke

- 新增 fetch-based `createDeepSeekRealNoteProvider`，不引入 OpenAI SDK 或新依赖。
- Adapter POST 到 `${baseUrl}/chat/completions`，映射 OpenAI-compatible response 到 `LlmNoteProviderResult`。
- `runRealProviderSmoke` 在 `executeReal=true` 且 env 完整时通过 adapter 执行。
- Smoke CLI 默认仍 skip，不发起真实外部调用。
- 单测通过 injected fake fetch 覆盖 request/response mapping、opt-in passed path、HTTP failed path。

### Phase 11: DeepSeek Redacted Raw Envelope Capture

- DeepSeek real adapter 支持可选 `onRawEnvelope` hook。
- Hook 只接收 redacted request/response envelope。
- `Authorization`、`api_key`、`access_token` 等 secret 字段会被替换成 `[REDACTED]`。
- `prompt_tokens`、`completion_tokens`、`total_tokens` 等 usage 计数字段不会被误脱敏。
- 默认不捕获 raw envelope。

### Phase 12: Raw Envelope Artifacts And Trace Ref

- 新增 `writeRawEnvelopeArtifacts`。
- 当 `runRealProviderSmoke` 提供 `AgentRunsStore` 时，会写入：
  - `raw-provider/<workItemId>/request.json`
  - `raw-provider/<workItemId>/response.json`
  - `traces/<workItemId>.jsonl`
- trace 中追加 `llm.provider.raw_ref`，只保存 raw artifact path，不内联 payload。
- raw artifacts 会二次 redaction，避免 hook 调用方误传未脱敏数据。

### Phase 13: Opt-In DeepSeek Runtime Provider Registry

- `ProviderRuntimeConfig` 支持 `provider: "deepseek-real"`。
- Config 不保存 API key，只保存 `apiKeyEnvName`。
- Provider registry 从 injected env 或 `process.env` 读取 API key。
- 缺少 API key 时抛出 `ProviderRuntimeError("auth", "MISSING_DEEPSEEK_API_KEY", false)`。
- 默认 provider 仍是 `fake`。
- CLI 行为未改变；本 phase 不把真实 provider 暴露为默认路径。

### Phase 14: CLI Opt-In Guard

- `organize` CLI 识别 `--provider deepseek-real`。
- 未提供 `--allow-real-provider` 时会在 workflow 前阻断。
- 即使提供 `--allow-real-provider`，也会预检查 `DEEPSEEK_API_KEY`。
- 默认 provider 仍是 `fake`。

### Phase 15: Workflow Dependency Injection

- `runOrganizeWorkflow` 支持 `providerRuntimeDependencies`。
- `providerRuntimeDependencies` 通过 execute node closure 注入，不进入 `GraphState`。
- 集成测试用 fake env/fetch 跑通 `deepseek-real` workflow，不触发真实网络。

### Phase 16: MiMo vLLM Fixture Runtime

- 新增 `mimo-vllm-fixture` provider runtime。
- 新增 `createMimoVllmFixtureNoteProvider`，把 vLLM-like fixture output 映射为 `LlmNoteProviderResult`。
- Provider 输出 `provider: "mimo-vllm"`，默认 model 为 `XiaomiMiMo/MiMo-7B-RL-0530`。
- usage 从 `prompt_token_ids.length`、`output_token_ids.length` 计算。
- Provider registry、trace provider mapping、CLI provider allow-list 已接入。
- `organize --provider mimo-vllm-fixture` 不需要 token，不需要 `--allow-real-provider`，不触发真实网络。
- 这不是小米 MiMo 真实 API 调用；它验证的是 MiMo/vLLM 形态 provider contract 和 agent loop 接入边界。

### Phase 17: MiMo Real Adapter And Opt-In Runtime

- 新增 `createMimoRealNoteProvider`，按 OpenAI-compatible `/chat/completions` 适配 MiMo endpoint。
- `mimo-real` runtime provider 已接入 Provider registry。
- `mimo-real` 输出 `provider: "mimo-api"`，不绕过 `PatchBundle`、`MergeGuard`、`Validator`、`Publisher`。
- API key 只从 `MIMO_API_KEY` 或配置的 `apiKeyEnvName` 读取，不进入 `ProviderRuntimeConfig`、`GraphState`、trace 或文档。
- 默认 MiMo base URL 为 `https://token-plan-cn.xiaomimimo.com/v1`。
- `organize --provider mimo-real` 必须显式提供 `--allow-real-provider`，并要求 `MIMO_API_KEY` 和 `MIMO_MODEL`。
- `mimo-real-smoke` 已接入 provider smoke CLI；缺 env 时 skip，不发起真实调用。
- 单测用 injected fake fetch 覆盖 request mapping、response mapping、redacted raw envelope、raw artifact、raw_ref trace。
- 集成测试用 injected fake env/fetch 跑通 workflow，不触发真实网络。

### Phase 18: Secret-Safe Real Smoke CLI Input

- `provider-smoke` CLI 支持 `--api-key-stdin`，从 stdin 读取 API key，只注入当前进程内存 env。
- `provider-smoke` CLI 支持 `--base-url <url>` 和 `--model <model>`，用于补齐 real smoke 所需 env。
- `mimo-real-smoke` 和 `deepseek-real-smoke` 使用各自 env name 映射，不打印 API key。
- 新增测试证明 stdin API key + baseUrl/model 后，MiMo smoke 不再因为缺 env 返回 `MISSING_ENV`。
- 测试不加 `--execute-real`，因此仍不触发真实外部调用。

本地真实 MiMo smoke 的推荐执行方式：

```bash
printf '%s' "$MIMO_API_KEY" | \
node --import tsx src/cli/provider-smoke.ts \
  --provider mimo-real-smoke \
  --execute-real \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5
```

真实 MiMo smoke 补充验收：

- `/v1/models` 真实调用返回可用模型列表，包含 `mimo-v2.5`。
- 使用旧 fixture 模型名 `XiaomiMiMo/MiMo-7B-RL-0530` 真实调用返回 HTTP 400，错误为模型不支持。
- 使用 `mimo-v2.5` 真实执行 `mimo-real-smoke` 通过：`status=PASSED`，`realExternalCall=true`。
- 本报告不记录 API key/token 值。

### Phase 19: Provider Runtime Readiness

- 新增共享 `createOpenAiCompatibleNoteProvider`。
- DeepSeek real provider 和 MiMo real provider 迁移为薄 wrapper。
- `/chat/completions` URL 拼接、messages 构造、Authorization header、response usage mapping、raw envelope redaction 统一在共享 factory 中实现。
- Provider-specific wrapper 只保留 provider id、providerCall suffix、display name、error class 等差异。
- 新增 `openai-compatible-provider` 单测，覆盖 request/response/redaction 的共享行为。
- 现有 DeepSeek/MiMo smoke、registry、workflow 测试继续覆盖 wrapper 行为，确保迁移不改变外部语义。

### Phase 20: Agent Execution Quality

- 新增 `runNoteQualityLoop`，把 note agent 明确为 bounded draft -> self-check -> deterministic repair -> patch。
- `runMockNoteAgent` 在 provider draft 后执行质量 loop，并写入：
  - `agent-loop/<workItemId>.json`
- 当前 deterministic repair set 覆盖 `TOPIC_NOTE_WEAK_RELATIONS`：
  - draft 缺 `## 相关链接`；
  - 且 draft 已有 title、`## 摘要`、`## 来源追踪`；
  - 则追加 `## 相关链接` / `暂无相关链接。`
- 结构性缺失不会被伪装修复，仍由 `Validator` 阻断。
- 新增 `weak-relations-fixture` runtime provider，用于 workflow 级质量 loop 验收。
- 新增 `agent-execution-quality-spec.md` 记录 loop artifact contract 和升级条件。

### Phase 21: Work Item Agent Runtime

- 新增 `WorkItemAgentLoopReport`，统一 `agent-loop/<workItemId>.json` 为 `work-item-agent-loop.v1`。
- 新增 `budgetForWorkItemType`：
  - note work item：`maxIterations=2`，`maxProviderCalls=1`；
  - topic index / MOC / quality review：`maxIterations=1`，`maxProviderCalls=0`；
  - `MERGE_USER_EDITED_NOTE`：`maxIterations=0`，`maxProviderCalls=0`，本阶段不自动执行。
- note agent 从 `note-quality-loop.v1` 迁移为统一 loop artifact，保留 deterministic repair 结果。
- topic index、MOC、quality review 都会写统一 loop artifact；这些节点当前是 deterministic single-step agent node，不调用 provider。
- execute phase 在写 `patches/*.patch.json` 或 `quality/*.json` 前校验 loop artifact：
  - schema invalid；
  - runId / workItemId / workItemType mismatch；
  - budget exceeded；
  - outputRef 不匹配。
- loop gate 失败会写 `FAILED_EXECUTOR`，attempt 带：
  - `failureSource: "loop"`；
  - `failureReason`；
  - `retryable: false`。
- provider、validator、merge guard 失败 attempts 已补 durable metadata。
- resume 决策读取最新 attempt 的 `retryable`，不再把运行时失败统一视为可重试。
- `QualityFindings` 增加 structured warning findings：`issue`、`severity`、`targetPath?`、`evidence`。
- `eval.json` 增加 `agentLoop`：
  - `total` / `reports`；
  - `byNode`；
  - `providerCalls`；
  - `repairedIssues` / `remainingIssues`；
  - `budgetExceeded`；
  - `missingArtifacts` / `corruptArtifacts`。
- `report.md` 增加 agent loop artifact 覆盖摘要。
- `AgentRunsStore` 写 JSON/text artifacts 改为 temp file + rename 原子写协议。
- 新增声明式 context contract：
  - note 只允许读取 source paths 和目标 sha；
  - topic index 禁止 raw reads；
  - MOC 只读取 topic index paths；
  - quality review 读取 published notes 和 validation artifacts；
  - `MERGE_USER_EDITED_NOTE` 第一版不自动读取/写入。

### Phase 22: MiMo Real Workflow Smoke

- 在临时 fixture workspace 上执行真实 MiMo `organize workflow`。
- API key 只通过 stdin 进入进程内存，不写入 artifact、文档或命令参数。
- 模型使用真实 `/v1/models` 验证过的 `mimo-v2.5`。
- 第一次完整 workflow smoke 暴露真实模型输出结构问题：
  - `## 总结`、`## 源追踪`、`## 源信息`、`## 源文件追踪`；
  - `## Summary`、`## Source Tracking`、`## Key Content`、`## Related Links`；
  - related links 中出现 `待补充` 占位。
- 已将这些低风险结构问题放入 deterministic `runNoteQualityLoop` 修复，不依赖 prompt 兜底。
- 最终真实 workflow smoke 通过：
  - result status: `SUCCEEDED_WITH_WARNINGS`；
  - raw coverage: `3/3`；
  - pages rewritten: `3`；
  - raw mirror converted: `1`；
  - agent loop reports: `8/8`；
  - provider calls: `3`；
  - missing/corrupt/budgetExceeded: `[]`；
  - all note/index/MOC work items published；
  - quality review succeeded。

### Phase 25: Knowledge Methodology Registry

- 新增 `KnowledgeMethodology` / `KnowledgeMethodologySummary` domain contract。
- 新增默认 `lmwiki-v1` methodology profile，显式表达 LMWiki 风格三层结构：
  - raw layer: `raw/`；
  - rules layer: `schema/`；
  - knowledge layer: `knowledge-base/`、`knowledge-base/topics/`、`knowledge-base/moc.md`。
- 将 note heading alias 和 placeholder blocker 从 `note-quality-loop` 硬编码迁移到 `lmwiki-v1` profile：
  - `总结` / `Summary` / `Overview` -> `摘要`；
  - `源追踪` / `源信息` / `源文件` / `源文件追踪` / `Source Tracking` / `Source` / `Sources` -> `来源追踪`；
  - `关键内容` / `Key Content` / `Key Points` / `Key Concepts` -> `关键概念`；
  - `Related Links` / `References` -> `相关链接`。
- `runNoteQualityLoop` 保持 public function signature 不变，但内部从默认 methodology 读取 alias/blocker 规则。
- 新增 registry 单测，覆盖默认 profile、profile list、unknown id fail-fast。
- 新增 note quality 单测，证明 `Overview` / `Source` / `Key Points` 由默认 methodology 驱动归一化。
- 本阶段没有执行真实 provider 调用；原因是变更只影响确定性规则抽象，真实 provider 行为已在 Phase 22 暴露并转化为 fixture/unit 回归。

### Phase 26: Methodology-aware Workflow Contract

- `GraphState` 新增 `methodologyId`，默认 `lmwiki-v1`。
- `OrganizePlan` 新增：
  - `methodologyId`；
  - `methodologyVersion`。
- 每个 `WorkItem` artifact 新增 `methodologyId`，使单个 work item 也能自描述规则来源。
- `createOrganizePlan` 通过 registry 解析 methodology：
  - unknown id fail-fast；
  - note target path、topic index path、MOC path、schema sha、publish policy 从 `lmwiki-v1` profile 读取。
- `validateBundle` 增加 `methodologyId`：
  - raw/rules write blocker 从 profile layout 读取；
  - topic note path 从 profile topic dir 读取；
  - required sections 从 `noteSchema.requiredSections` 读取；
  - placeholder blocker 从 `noteSchema.placeholderBlockers` 读取。
- `eval.json` 新增：
  - `methodology.id`；
  - `methodology.version`。
- `report.md` 新增 `Methodology: lmwiki-v1@1`。
- CLI 新增 `--methodology lmwiki-v1`，并在 workflow 执行前阻断 unknown methodology。
- 本阶段没有执行真实 external provider call；原因是改动集中在 runtime contract 和 deterministic validation，真实 provider 行为由既有 injected-fake integration 和 Phase 22 real smoke 覆盖。

### Phase 27: SDK And Tool Surface

- 新增 public SDK facade：
  - `createKnowledgeWorkflowAgent`；
  - `runOrganize`；
  - `inspectRun`。
- `src/index.ts` 导出 SDK API 和稳定 request/result types，后端不需要 import `runtime/langgraph/*`。
- `RunOrganizeRequest` 支持 `methodologyId`，默认 `lmwiki-v1`。
- `RunOrganizeResult` 输出：
  - `runId`；
  - `status`；
  - `methodologyId`；
  - `artifactRoot`；
  - `planPath`；
  - `reportPath`；
  - `lastError`。
- `inspectRun` 只读 `.agent-runs` artifacts，返回 plan/eval/report/work item statuses/resume decisions，不重新执行 workflow。
- `organize` CLI 改为 SDK wrapper，输出 SDK result JSON。
- `resume` CLI 改为调用 SDK `inspectRun`，保留 `workspaceRoot/runId/decisions` 输出。
- 新增 internal tool metadata registry：
  - read/artifact/validation tools 可作为 `SDK_ONLY` metadata；
  - `patch.publish` 标记为 `WORKSPACE_WRITE` + `INTERNAL_ONLY`；
  - 不暴露 provider raw envelope writer。
- 本阶段没有执行真实 external provider call；原因是 public SDK facade 和 artifact inspector 不改变 provider adapter 行为。

### Phase 28: Hybrid Agent Command Router

- 新增 SDK command router：
  - `handleCommand`；
  - `classifyCommand`。
- `createKnowledgeWorkflowAgent()` 返回对象新增 `handleCommand`。
- Router 只做 execution lane selection，不枚举所有未来业务场景：
  - `FIXED_WORKFLOW`：高置信固定 workflow，例如整理整个知识库；
  - `OPEN_AGENT_TASK`：开放 agent 请求，第一版只返回 task envelope；
  - `CONFIRMATION_REQUIRED`：疑似 workspace write 但缺少确认。
- 新增 command risk：
  - `READ_ONLY`；
  - `DRAFT_ONLY`；
  - `WORKSPACE_WRITE`。
- `OPEN_AGENT_TASK` 输出 `OpenAgentTaskEnvelope`：
  - `objective`；
  - `risk`；
  - `outputPolicy`；
  - `allowedToolNames`；
  - `blockedToolNames`。
- `patch.publish` 永远不会进入 open agent `allowedToolNames`。
- 固定 workflow 默认不执行；只有 `execute=true` 时才调用 `runOrganize`。
- 第一版不执行真实 LLM classifier，不写 workspace；目的是先稳定 SDK command contract 和风险边界。
- 本阶段没有执行真实 external provider call；原因是开放 agent task 只返回 envelope，固定 workflow execution 覆盖使用 fake provider。

### Phase 29: Knowledge-scoped Open Agent Runtime

- 新增 `runOpenAgentTask` public SDK API。
- `createKnowledgeWorkflowAgent()` 返回对象新增 `runOpenAgentTask`。
- `handleCommand` 对 `OPEN_AGENT_TASK` 不再只返回 envelope，而是执行 open runtime 并返回 `openAgent` result。
- `OpenAgentRuntime` 第一版为 deterministic runtime，不调用真实 LLM、不新增依赖。
- `READ_ONLY` / `ANSWER_ONLY` 请求会：
  - scan workspace；
  - gather raw files / knowledge pages / methodology context；
  - produce answer；
  - write `.agent-runs/open-agent/<taskId>.json`。
- `DRAFT_ONLY` / `DRAFT_ARTIFACT` 请求会：
  - produce draft artifact；
  - write only under `.agent-runs/open-agent/`；
  - not write `knowledge-base/` files。
- Open agent artifact schema:
  - `schemaVersion: "open-agent-runtime.v1"`；
  - steps: `PLAN` / `GATHER_CONTEXT` / `PRODUCE_OUTPUT` / `SELF_CHECK`；
  - context: raw files、knowledge pages、methodology；
  - toolPolicy: allowed / blocked tools；
  - outputRef: answer / draft / policy-failure。
- Policy guard:
  - `patch.publish` in `allowedToolNames` returns `FAILED_POLICY`；
  - ambiguous workspace write commands still return `CONFIRMATION_REQUIRED` and do not create open-agent artifact。
- 本阶段没有执行真实 external provider call；原因是目标是锁定 open runtime contract 和安全边界，LLM runtime 是后续替换点。

## Verification

已执行：

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/provider-failure.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/artifact-fallback-resume.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/resume-inspector.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/fixture-providers.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/llm-trace-writer.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-registry.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/cli-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/fixture-providers.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/provider-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/openai-compatible-provider.test.ts tests/unit/provider-smoke.test.ts tests/unit/provider-registry.test.ts tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/note-quality-loop.test.ts tests/unit/llm-provider.test.ts tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/work-item-agent-runtime.test.ts tests/unit/work-item-runtime-boundary.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/llm-provider.test.ts tests/unit/note-quality-loop.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/quality-review-agent.test.ts tests/unit/mock-agents.test.ts tests/integration/langgraph-workflow.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/resume-inspector.test.ts tests/integration/provider-failure.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/agent-runs-store.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/note-quality-loop.test.ts tests/unit/llm-provider.test.ts tests/integration/provider-failure.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/methodology.test.ts tests/unit/note-quality-loop.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/methodology.test.ts tests/unit/note-quality-loop.test.ts tests/unit/llm-provider.test.ts tests/integration/provider-failure.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/planner.test.ts tests/unit/validation.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/sdk.test.ts tests/unit/internal-tool-registry.test.ts tests/integration/cli-smoke.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/command-router.test.ts tests/unit/sdk.test.ts tests/unit/internal-tool-registry.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/open-agent-runtime.test.ts tests/unit/command-router.test.ts tests/unit/sdk.test.ts tests/unit/internal-tool-registry.test.ts
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

结果：

- `npm test`: 34 test files / 121 tests passed。
- `npm run typecheck`: passed。
- `git diff --check`: passed。
- `mimo-real-smoke --execute-real --model mimo-v2.5`: passed，real external call = true。
- `mimo-real organize workflow smoke --model mimo-v2.5`: passed，real external call = true，providerCalls = 3。

## Phase 30: Open Agent Candidate Patch And Real Smoke

本阶段补齐 open agent 的“可提议写入但不直接写入”能力，并新增 MiMo open-agent real smoke/eval 入口。

交付：

- 新增 `CANDIDATE_PATCH` open agent output policy。
- `runOpenAgentTask` 现在可以返回 `CandidatePatchProposal`：
  - `publishable: false`；
  - target path 限定在 `knowledge-base/drafts/<taskId>.md`；
  - artifact 只写 `.agent-runs/open-agent/<taskId>.json`；
  - 不写 `knowledge-base/` 目标文件。
- open agent report 新增：
  - `groundingRefs`；
  - `toolCalls`；
  - `candidatePatch`。
- `handleCommand` 对模糊写入请求继续返回 `CONFIRMATION_REQUIRED`，并新增 fixed workflow handoff：
  - `capabilityId: "workflow.organizeWorkspace"`；
  - `confirmationRequired: true`；
  - `executeRequired: true`。
- 新增 `runOpenAgentRealSmoke` / `inspectOpenAgentRealSmoke` public SDK exports。
- 新增 `src/cli/open-agent-smoke.ts`：
  - 支持 `--api-key-stdin`；
  - 默认无 `--execute-real` 时跳过真实外部调用；
  - 不把 API key 输出到 stdout/stderr。

已验证：

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-runtime.test.ts tests/unit/command-router.test.ts tests/unit/sdk.test.ts tests/unit/open-agent-real-smoke.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
git diff --check
```

结果：

- Focused: 4 test files / 19 tests passed。
- Full: 35 test files / 125 tests passed。
- Typecheck: passed。
- Diff check: passed。

真实调用状态：

- MiMo open-agent real smoke 已执行：
  - provider: `mimo-open-agent-smoke`；
  - model: `mimo-v2.5`；
  - status: `PASSED`；
  - realExternalCall: `true`；
  - artifact: `.agent-runs/open-agent/mimo-open-agent-smoke.json` in temp workspace；
  - candidate target `knowledge-base/drafts/mimo-open-agent-smoke.md` 未被写入。
- 真实调用 token 通过 stdin/env 输入；未写入代码、文档、artifact 或 stdout/stderr。

## Phase 31: LLM-backed Open Agent Graph

本阶段把 open agent 从 deterministic runtime 推进到可执行的 graph runner 骨架：policy gate、plan、context gather、bounded tool loop、synthesis、self-check、artifact/trace writer、SDK/router opt-in、MiMo graph smoke。

交付：

- 新增 `runOpenAgentGraph` public SDK API。
- `createKnowledgeWorkflowAgent()` 返回对象新增 `runOpenAgentGraph`。
- `handleCommand` 新增显式 opt-in：
  - 默认仍执行 deterministic `runOpenAgentTask`；
  - `openAgentMode: "llm-graph"` 才执行 graph。
- 新增 open agent graph runtime 文件：
  - `src/runtime/open-agent/open-agent-state.ts`
  - `src/runtime/open-agent/open-agent-graph.ts`
  - `src/runtime/open-agent/open-agent-provider.ts`
  - `src/runtime/open-agent/open-agent-artifacts.ts`
  - `src/runtime/open-agent/nodes/*`
- Graph status 覆盖：
  - `SUCCEEDED`
  - `NEEDS_CONFIRMATION`
  - `FAILED_POLICY`
  - `FAILED_PROVIDER`
  - `FAILED_VALIDATION`
  - `FAILED_BUDGET`
- Graph 节点覆盖：
  - policy gate blocks unknown methodology / `patch.publish` / invalid budget；
  - plan parses provider JSON/object output；
  - context gather scans workspace and selects grounding refs；
  - bounded tool loop records safe observation refs, not private chain-of-thought；
  - synthesis outputs answer/draft/candidate patch；
  - self-check validates grounding and candidate patch target；
  - artifact writer writes report and trace under `.agent-runs/open-agent/`。
- MiMo open-agent smoke 新增 `mode: "llm-graph"` 和 `--mode llm-graph` CLI。
- Graph-mode MiMo smoke 写 redacted raw provider artifacts：
  - `.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/request.json`
  - `.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/response.json`

已验证：

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-policy.test.ts tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts tests/unit/sdk.test.ts tests/unit/open-agent-real-smoke.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
git diff --check
```

结果：

- Focused: 5 test files / 20 tests passed。
- Full: 38 test files / 137 tests passed。
- Typecheck: passed。
- Diff check: passed。

真实调用状态：

- MiMo llm-graph smoke 已执行：
  - provider: `mimo-open-agent-smoke`；
  - mode: `llm-graph`；
  - model: `mimo-v2.5`；
  - outputPolicy: `ANSWER_ONLY`；
  - status: `PASSED`；
  - realExternalCall: `true`；
  - graph status: `SUCCEEDED`；
  - artifact: `.agent-runs/open-agent/mimo-open-agent-smoke.json` in temp workspace；
  - trace: `.agent-runs/open-agent/traces/mimo-open-agent-smoke.jsonl`；
  - raw provider request contains `Authorization: "[REDACTED]"`；
  - `knowledge-base/drafts/mimo-open-agent-smoke.md` 未被写入。

边界：

- 当前 `OpenAgentGraph` 是顺序 graph runner 骨架；public contract、node boundary、artifact、tool loop 已固定，后续可把内部 runner 替换为 LangGraph `StateGraph`。
- 当前 graph-mode MiMo smoke 的真实外部调用发生在 smoke harness 的 MiMo adapter；graph 本体使用 deterministic open-agent provider 完成 plan/tool-loop。下一阶段应把 MiMo/OpenAI-compatible provider 接入 `open-agent-provider.ts` 的 plan/action/synthesis。
- Open graph 仍不能 publish workspace；candidate patch 仍 `publishable: false`。

## Phase 32: Provider-backed Open Agent Graph

本阶段把真实 provider 从 smoke 前置调用推进到 `OpenAgentGraph` 本体：MiMo/DeepSeek/OpenAI-compatible provider 可以直接驱动 graph plan 和 nextAction。

交付：

- 新增 `createOpenAiCompatibleOpenAgentProvider`。
- 新增 `selectOpenAgentProvider`：
  - `mimo-real` 使用 `MIMO_API_KEY` / `MIMO_BASE_URL` / `MIMO_MODEL`；
  - `deepseek-real` 使用 `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL`；
  - fake/fixture runtime 仍走 deterministic provider。
- 新增 provider output parsing：
  - strict JSON；
  - fenced JSON；
  - `contextHints` string -> single-item array normalization；
  - invalid JSON -> `FAILED_VALIDATION`。
- `runOpenAgentGraph` 现在在没有显式注入 `openAgentProvider` 时，会按 `providerRuntime` 选择 provider。
- Graph-owned raw provider refs：
  - `rawProviderRefs` 进入 graph result/report；
  - request/response 写入 `.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/`；
  - `Authorization` redacted。
- `runOpenAgentRealSmoke({ mode: "llm-graph" })` 现在不再做 smoke harness 前置 note-provider 调用；真实 MiMo 调用发生在 graph provider 的 `plan()` 和 `nextAction()`。

已验证：

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
git diff --check
```

结果：

- Focused: 3 test files / 14 tests passed。
- Full: 39 test files / 142 tests passed。
- Typecheck: passed。
- Diff check: passed。

真实调用状态：

- 第一次 provider-backed MiMo graph smoke 失败在 plan schema drift：
  - provider returned `contextHints` as string；
  - runtime returned `FAILED_VALIDATION`；
  - raw request was redacted。
- 第二次失败在 action loop：
  - provider repeatedly returned `READ_CONTEXT`；
  - runtime returned `FAILED_BUDGET`；
  - this exposed prompt did not clearly state runtime already owns file reads。
- 修正后 MiMo provider-backed graph smoke 已执行通过：
  - provider: `mimo-open-agent-smoke`；
  - mode: `llm-graph`；
  - model: `mimo-v2.5`；
  - outputPolicy: `ANSWER_ONLY`；
  - status: `PASSED`；
  - graph status: `SUCCEEDED`；
  - realExternalCall: `true`；
  - providerCalls: `2` (`plan` + `nextAction`)；
  - raw provider refs: `open-agent-plan-1`, `open-agent-next-action-2`；
  - raw request artifacts contain `Authorization: "[REDACTED]"`；
  - token search under `.agent-runs/open-agent` had no match；
  - `knowledge-base/drafts/mimo-open-agent-smoke.md` 未被写入。

边界：

- Provider-backed graph 现在覆盖 plan/action，不覆盖最终 answer synthesis；当前 answer synthesis 仍由 deterministic graph node 基于 grounding refs 生成。
- Graph runner 仍是顺序 runner，尚未替换为 LangGraph `StateGraph`。
- Provider 不能 publish workspace；candidate patch 仍为 non-publishable proposal。

## Phase 33: Provider-backed Open Agent Synthesis

本阶段把 `OpenAgentGraph` 的最终 synthesis 从 deterministic-only 推进到 provider-backed synthesis。Provider 可以生成 answer / draft / candidate content，但 runtime 仍负责 schema parse、grounding self-check、candidate target/content sha/publishable/handoff 和 no-write 边界。

交付：

- `OpenAgentProvider` 新增 optional `synthesize()`。
- 新增 `parseOpenAgentSynthesisOutput`：
  - 支持 object；
  - 支持 fenced JSON；
  - invalid JSON / invalid shape -> `OpenAgentProviderValidationError`。
- OpenAI-compatible open-agent provider 新增 synthesize adapter：
  - call id 使用 `open-agent-synthesize-<n>`；
  - raw request/response 继续写入 `.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/`；
  - raw envelope 继续 redaction。
- context gather 新增 `contextDigest`：
  - 从已选 grounding refs 读取短摘要；
  - 每条 excerpt 最多 800 字符；
  - graph result/report 暴露 digest。
- `SynthesizeNode` 在 provider 支持 `synthesize()` 时走 provider-backed path：
  - `ANSWER_ONLY` 使用 provider answer；
  - `DRAFT_ARTIFACT` 使用 provider title/content；
  - `CANDIDATE_PATCH` 使用 provider content，但 target path、content sha、`publishable: false`、handoff 由 runtime 生成。
- deterministic provider/fake provider 无 `synthesize()` 时继续走 deterministic fallback。
- self-check 新增：
  - draft 必须包含 `Draft only` marker；
  - provider synthesis grounding refs 必须来自 gathered refs；
  - provider synthesis content 必须包含其 grounding refs；
  - candidate target 仍必须在 `knowledge-base/` 下。
- `runOpenAgentRealSmoke({ mode: "llm-graph" })` 的 pass 条件收紧为：
  - graph status `SUCCEEDED`；
  - `providerCalls >= 3`；
  - synthesis metadata `providerBacked: true`。

已验证：

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts tests/unit/open-agent-real-smoke.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
git diff --check
```

结果：

- Focused: 4 test files / 25 tests passed。
- Full: 39 test files / 151 tests passed。
- Typecheck: passed。
- Diff check: passed。

fake / injected-fetch 证据：

- Provider unit 覆盖 strict/fenced synthesis parser、synthesize adapter、`open-agent-synthesize-1` call id、raw envelope redaction。
- Graph node unit 覆盖 provider-backed answer synthesis：
  - providerCalls: `3`；
  - raw provider refs: `plan` / `next-action` / `synthesize`。
- Integration graph 覆盖：
  - provider draft content 包含 `Draft only`，且不写 `knowledge-base/drafts/provider-draft.md`；
  - provider candidate content 进入 candidate patch；
  - candidate target path runtime-controlled 为 `knowledge-base/drafts/<taskId>.md`；
  - candidate `contentSha` 为 deterministic sha256；
  - candidate `publishable: false`；
  - unsafe provider target `raw/unsafe.md` -> `FAILED_POLICY`；
  - provider grounding ref outside gathered context -> `FAILED_VALIDATION`。
- Open-agent real smoke unit 使用 injected fetch 覆盖 llm-graph 三次 provider 调用：
  - plan；
  - nextAction；
  - synthesize；
  - result JSON 不包含测试 API key。

真实 MiMo smoke 状态：

- 已执行通过：
  - provider: `mimo-open-agent-smoke`；
  - mode: `llm-graph`；
  - model: `mimo-v2.5`；
  - outputPolicy: `ANSWER_ONLY`；
  - status: `PASSED`；
  - graph status: `SUCCEEDED`；
  - realExternalCall: `true`；
  - providerCalls: `3` (`plan` + `nextAction` + `synthesize`)；
  - synthesis providerBacked: `true`；
  - synthesis providerCallId: `open-agent-synthesize-3`；
  - raw provider refs: `open-agent-plan-1`, `open-agent-next-action-2`, `open-agent-synthesize-3`；
  - temp fixture workspace: `/private/tmp/open-agent-mimo-smoke.tvk0Z3`。
- Redaction evidence:
  - plan request artifact contains `Authorization: "[REDACTED]"`；
  - synthesize request artifact contains `Authorization: "[REDACTED]"`；
  - token search under `.agent-runs/open-agent` returned `TOKEN_NO_MATCH`。
- No workspace write evidence:
  - `knowledge-base/drafts/mimo-open-agent-smoke.md` does not exist in the temp fixture workspace。

边界：

- Provider-backed synthesis 已覆盖本地 fake/injected-fetch plan/action/synthesis 链路和真实 MiMo `ANSWER_ONLY` llm-graph smoke。
- Provider 仍不能 publish workspace。
- Candidate patch 仍是 non-publishable proposal，固定 workflow handoff 仍由 runtime 生成。
- Graph runner 仍是顺序 runner，未替换为 LangGraph `StateGraph`。
- publish/resume truth source 未改变。

## Phase 34: Open Agent StateGraph Runner And Phase SOP

本阶段把 `OpenAgentGraph` 的内部 orchestration 从手写顺序 runner 迁移为 LangGraph `StateGraph`，并把后续 phase 的执行 SOP 固化为默认协议。

交付：

- 新增 `compileOpenAgentStateGraph({ provider })`，由 LangGraph `StateGraph` 编排 open-agent nodes。
- 保留现有 node 作为行为边界：
  - `runPolicyGateNode`；
  - `runPlanNode`；
  - `runContextGatherNode`；
  - `runToolLoopNode`；
  - `runSynthesizeNode`；
  - `runSelfCheckNode`；
  - `runArtifactNode`。
- Provider selection 仍在 `runOpenAgentGraph()` 中完成，provider object 和 `providerRuntimeDependencies` 不进入 graph state/report。
- Graph state 新增 runner metadata：
  - `kind: "LANGGRAPH_STATEGRAPH"`；
  - `version: 1`。
- Report 新增 `runner` metadata，作为审计证据；不作为 resume truth source。
- Terminal failures 现在通过 StateGraph conditional edge 路由到 `artifact`，因此 policy/plan/tool-loop/synthesis/self-check 失败都会写 open-agent report/trace。
- Conditional edge 语义：
  - `policyGate` / `plan` 非 `RUNNING` -> `artifact`；
  - `toolLoop` 的 `FAILED_BUDGET` / `FAILED_VALIDATION` / `FAILED_PROVIDER` -> `artifact`；
  - `NEEDS_CONFIRMATION` -> `synthesize` 生成 fixed workflow handoff -> `artifact`；
  - `synthesize` 失败 -> `artifact`；
  - success path -> `selfCheck` -> `finalizeStatus` -> `artifact`。
- `docs/architecture/runtime-phase-sop.md` 已包含后续 phase 默认协议、real provider smoke、token/env handling、full verification 和 Stop Conditions。

TDD / red evidence：

- 新增 runner metadata report test，红测失败为 `report.runner` undefined。
- 新增 StateGraph wrapper step test，红测失败为首个 step 仍是 `POLICY_GATE`。
- 强化 invalid plan / budget failure / provider-backed answer step ordering，红测暴露旧 runner 不写 terminal artifact。
- Full suite 首轮失败在 `open-agent-graph-policy.test.ts` 的旧断言：测试仍假设 failure path 最后一步是 `POLICY_GATE`。根因是 Phase 34 改为 terminal failure 仍写 artifact；已改为断言 `POLICY_GATE` failure step 和最终 `ARTIFACT` step。

已验证：

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts tests/unit/open-agent-real-smoke.test.ts tests/unit/open-agent-provider.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-graph-policy.test.ts
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
git diff --check
```

结果：

- Initial red focused: expected failures for missing `runner`, missing `RUNNER` step, and old non-artifact terminal paths。
- Focused StateGraph: 2 test files / 16 tests passed。
- Focused provider regression: 4 test files / 27 tests passed。
- Policy focused after assertion migration: 1 test file / 3 tests passed。
- Full: 39 test files / 153 tests passed。
- Typecheck: passed。
- Diff check: passed。

Subagent review follow-up：

- Reviewer found provider selection happened before StateGraph invoke. Missing real-provider env could throw `ProviderRuntimeError` without writing `.agent-runs/open-agent/<taskId>.json`。已修复为 `FAILED_PROVIDER` result/report，providerCalls 保持 `0`，并新增 missing MiMo env regression test。
- Reviewer found `loopBudget.maxToolCalls` was validated only for `<= 0` but not enforced. 已修复为 tool-loop READ_CONTEXT budget gate：达到 `maxToolCalls` 后下一轮直接 `FAILED_BUDGET`，不进入 synthesis，并新增 regression test。
- Reviewer flagged `routeAfterToolLoop()` only handled selected failure statuses. 已泛化为 `state.status !== "RUNNING" && state.status !== "NEEDS_CONFIRMATION"` -> `artifact`。
- Reviewer flagged `providerRuntime` was present in LangGraph state annotation and could enter future checkpoints. 已从 `OpenAgentGraphState` 和 StateGraph annotation 移除；provider runtime config remains local to provider selection。
- Review follow-up focused verification:
  - `tests/unit/open-agent-graph-nodes.test.ts`: 10 tests passed；
  - `tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-graph-policy.test.ts tests/integration/open-agent-graph.test.ts tests/unit/open-agent-real-smoke.test.ts tests/unit/open-agent-provider.test.ts`: 5 test files / 32 tests passed。

MiMo local credential entry follow-up：

- 新增 `src/runtime/provider/mimo-keychain.ts`：
  - Keychain service: `my-workflow-agent.mimo`；
  - accounts: `MIMO_API_KEY` / `MIMO_BASE_URL` / `MIMO_MODEL`；
  - `open-agent-smoke.ts` 在 env 缺失时自动从 Keychain 补齐；
  - tests 可用 `MY_WORKFLOW_AGENT_DISABLE_KEYCHAIN=1` 禁用自动读取，避免本机 Keychain 影响 deterministic tests。
- 新增 `open-agent-smoke.ts --configure-mimo-keychain --api-key-stdin --base-url ... --model ...` 一次性配置入口。
- 配置命令输出只包含 service/accounts，不输出 token 值。
- Focused verification:
  - `tests/unit/mimo-keychain.test.ts tests/unit/open-agent-real-smoke.test.ts`: 2 test files / 7 tests passed。

fake / injected-fetch 证据：

- Provider parser tests passed；StateGraph migration did not require parser/schema changes。
- Graph node tests prove step order:
  - success: `RUNNER` -> `POLICY_GATE` -> `PLAN` -> `CONTEXT_GATHER` -> `TOOL_LOOP` -> `SYNTHESIZE` -> `SELF_CHECK` -> `ARTIFACT`；
  - invalid plan: `RUNNER` -> `POLICY_GATE` -> `PLAN` -> `ARTIFACT`；
  - budget failure: `RUNNER` -> `POLICY_GATE` -> `PLAN` -> `CONTEXT_GATHER` -> `TOOL_LOOP` -> `ARTIFACT`。
- Provider-backed answer still records:
  - `providerCalls: 3`；
  - `synthesis.providerBacked: true`；
  - `outputKind: "ANSWER"`。
- MiMo-compatible injected fetch test still records raw refs:
  - `open-agent-plan-1`；
  - `open-agent-next-action-2`；
  - `open-agent-synthesize-3`。
- Injected raw request artifacts contain `[REDACTED]` and do not contain `test-api-key`。
- `runOpenAgentRealSmoke({ mode: "llm-graph" })` injected-fetch smoke passed with three provider calls and `open-agent-synthesize-3` synthesis metadata。

真实 MiMo smoke 状态：

- 已执行通过：
  - provider: `mimo-open-agent-smoke`；
  - mode: `llm-graph`；
  - model: `mimo-v2.5`；
  - outputPolicy: `ANSWER_ONLY`；
  - status: `PASSED`；
  - graph status: `SUCCEEDED`；
  - realExternalCall: `true`；
  - providerCalls: `3`；
  - synthesis providerBacked: `true`；
  - synthesis providerCallId: `open-agent-synthesize-3`；
  - raw provider refs: `open-agent-plan-1`, `open-agent-next-action-2`, `open-agent-synthesize-3`；
  - temp fixture workspace: `/private/tmp/open-agent-mimo-smoke.IVGtmf`。
- 第一次 sandbox run 失败在 network/environment：
  - graph status: `FAILED_VALIDATION`；
  - providerCalls: `0`；
  - rawProviderRefs: `[]`；
  - plan summary: `fetch failed`。
- 使用同一命令形态通过 sandbox escalation 重跑后通过。
- Redaction evidence:
  - synthesize request artifact contains `Authorization: "[REDACTED]"`；
  - token search under `.agent-runs/open-agent` returned `TOKEN_NO_MATCH`。
- No workspace write evidence:
  - `knowledge-base/drafts/mimo-open-agent-smoke.md` does not exist in the temp fixture workspace。
- 本报告不记录任何 API key/token 值。

边界：

- Public `runOpenAgentGraph` / `RunOpenAgentGraphResult` contract 未改变。
- Provider prompt、parser、output schema 未改变。
- Provider dependencies 未进入 graph state/report。
- Open-agent 仍不能 publish workspace；candidate patch 仍是 non-publishable proposal。
- Phase 35 spec/plan/change note 已创建；后续可继续 checkpoint/resume boundary。

## Phase 35 - Agent SDK MVP Phase 1

交付内容：

- Public SDK entry:
  - `runAgent(request)`；
  - `createKnowledgeWorkflowAgent().runAgent(request)`。
- Public result schema:
  - `schemaVersion: "agent-sdk-run.v1"`；
  - normalized `route` / `status` / `capabilityId` / `outputKind` / `output` / `artifacts` / `diagnostics`。
- Changed files:
  - `src/sdk/knowledge-workflow-agent.ts`；
  - `src/index.ts`；
  - `tests/unit/agent-sdk-run.test.ts`；
  - `tests/integration/agent-sdk-backend-sim.test.ts`；
  - `docs/reports/runtime-work-item-execution-resume-delivery.md`。

RED evidence：

- `npm test -- tests/unit/agent-sdk-run.test.ts`
  - 初始失败符合预期；
  - 失败原因：`runAgent` 未从 public SDK 导出，后续调用报 `(0 , runAgent) is not a function`；
  - 这证明测试先捕获了 Phase 35 缺失的 public SDK entry。

Focused verification：

- `npm test -- tests/unit/agent-sdk-run.test.ts`
  - 1 test file / 11 tests passed；
  - 覆盖 public export/types、unsafe runId rejection、deterministic answer/draft、llm graph candidate patch、injected provider raw refs/redaction、confirmation、fixed workflow preview/execution、fixed workflow resume skip no-write、fixed-workflow route failure。
- `npm test -- tests/integration/agent-sdk-backend-sim.test.ts`
  - 1 test file / 1 test passed；
  - 模拟后端只调用 public SDK：用户请求 -> SDK -> runtime -> `.agent-runs` artifact -> response envelope；
  - 覆盖 answer、draft、candidate patch、confirmation、fixed workflow route preview、fixed workflow execution。

Full verification：

- `npm test`
  - 42 test files / 170 tests passed。
- `npm run typecheck`
  - passed。
- `git diff --check`
  - passed；no whitespace errors。
- `rg -n "t[p]-[a-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" src tests docs`
  - no matches；command exited with no output and status 1。

mock / fake / injected / real evidence：

- Mock/deterministic evidence:
  - `deterministic-open-agent` answer returns `outputKind: "answer"` and `.agent-runs/open-agent/<runId>.json`；
  - deterministic draft returns `outputKind: "draft"` and does not write workspace。
- Fake provider evidence:
  - `llm-open-agent` with `providerRuntime: { provider: "fake" }` returns candidate patch envelope；
  - `mode: "llm-open-agent"` bypasses `handleCommand()` confirmation interception for messages containing `落库` and forces `OpenAgentGraph`。
- Injected provider evidence:
  - MiMo-compatible injected fetch returns provider-backed graph answer；
  - SDK diagnostics records `providerBacked: true` and `providerRuntime: "mimo-real"`；
  - `artifacts.rawProviderRefs` contains only artifact refs for redacted request/response files, not raw payloads or tokens。
- Real external call evidence:
  - not run in this phase；
  - reason: user did not explicitly request a new real MiMo smoke and Phase 35 acceptance is satisfied by mock/fake/injected provider verification。

No-write evidence：

- Unit candidate test asserts:
  - `artifacts.wroteWorkspace === false`；
  - `output.candidatePatch.publishable === false`；
  - `artifacts.targetWorkspacePaths === ["knowledge-base/drafts/sdk-candidate.md"]`；
  - target file under the temp fixture workspace does not exist。
- Backend simulation asserts `knowledge-base/drafts/sim-candidate.md` does not exist after graph candidate run。
- Fixed workflow execution uses before/after `knowledge-base` snapshots for `artifacts.wroteWorkspace`：
  - first publish reports `true`；
  - same-run resume/skip reports `false`。
- Repo-level check:
  - `test ! -e knowledge-base/drafts/sdk-candidate.md` passed。

Redaction evidence：

- Injected provider SDK test reads all raw provider request artifacts referenced by `artifacts.rawProviderRefs`；
- each request artifact contains `[REDACTED]`；
- no request artifact contains `test-api-key`；
- token pattern scan over `src tests docs` found no real token pattern。

Reviewer follow-up：

- Reviewer P0 on unsafe `runId/taskId` path interpolation fixed at SDK boundary：
  - `runId` must be a safe artifact slug；
  - `/`、`..`、token-like `tp-...` patterns are rejected before artifact creation。
- Reviewer fixed-workflow backend simulation gap fixed by adding route preview and execution to `tests/integration/agent-sdk-backend-sim.test.ts`。
- Reviewer `wroteWorkspace` false-positive risk reduced by comparing `knowledge-base` snapshots before/after fixed workflow execution instead of relying on status alone。
- Reviewer public provider type concern reduced for the new Phase 35 entry:
  - `RunAgentRequest` now uses SDK-owned `AgentSdkProviderRuntimeConfig` / `AgentSdkProviderRuntimeDependencies`；
  - backend-facing Phase 35 request types no longer name runtime provider modules directly。

Known limitations / Review risks：

- `RunOrganizeResult` still does not expose explicit published file list; Phase 35 uses content snapshot evidence for SDK `wroteWorkspace` until runtime report grows a first-class published-path summary。
- `artifacts.rawProviderRefs` normalizes runtime `{ providerCallId, requestPath, responsePath }` objects into string artifact refs to match `agent-sdk-run.v1` contract.
- Existing advanced SDK exports still include low-level `runOpenAgentGraph` / `RunOpenAgentGraphResult` types. Phase 35 backend simulation and new `runAgent` tests use only public SDK entry points, but a later API-boundary cleanup should separate advanced runtime-facing APIs from backend-facing package declarations。
- Graph failed terminal statuses other than `FAILED_PROVIDER` currently normalize to SDK `FAILED`, matching the Phase 35 spec table; future provider/policy reporting may need a more granular contract revision.

## Phase 37 - Agent SDK Backend Adapter Smoke

交付内容：

- 新增极薄 backend adapter：
  - `runBackendAgent(request)`；
  - `toBackendAgentResponse(result)`；
  - response schema: `agent-backend-response.v1`。
- Adapter 只消费 `agent-sdk-run.v1`：
  - 内部调用 `createKnowledgeWorkflowAgent().runAgent()`；
  - 不 import `src/runtime/*`；
  - 不解析 `openAgent`、`openAgentGraph`、`workflow` 等旧 runtime-specific result fields。
- Backend response fields：
  - `displayText`；
  - `requiresConfirmation`；
  - `requiresApproval`；
  - `artifactRefs`；
  - `wroteWorkspace`；
  - `targetWorkspacePaths`；
  - `source` 保留完整 `AgentSdkRunResult` envelope。
- Changed files:
  - `src/sdk/backend-adapter.ts`；
  - `src/index.ts`；
  - `tests/integration/agent-sdk-backend-adapter.test.ts`；
  - `docs/reports/runtime-work-item-execution-resume-delivery.md`。

RED evidence：

- `npm test -- tests/integration/agent-sdk-backend-adapter.test.ts`
  - 初始失败符合预期；
  - 失败原因：
    - `(0 , runBackendAgent) is not a function`；
    - `src/sdk/backend-adapter.ts` 不存在；
  - 这证明测试先捕获了 Phase 37 缺失的 backend adapter API，而不是 fixture 或 runtime 错误。

Focused verification：

- `npm test -- tests/integration/agent-sdk-backend-adapter.test.ts`
  - 1 test file / 2 tests passed；
  - 覆盖 answer、provider-backed answer、candidate patch、confirmation、fixed workflow preview、fixed workflow execution；
  - 覆盖 adapter source boundary：不包含 `../runtime/`、`openAgentGraph`、`openAgent`、`workflow.`。
- `npm test -- tests/unit/agent-sdk-run.test.ts tests/integration/agent-sdk-backend-sim.test.ts tests/integration/agent-sdk-backend-adapter.test.ts`
  - 3 test files / 16 tests passed；
  - 证明 Phase 37 adapter 未破坏 Phase 35 `runAgent()` contract 和既有 backend simulation。

Full verification：

- `npm test`
  - 43 test files / 174 tests passed。
- `npm run typecheck`
  - passed。
- `git diff --check`
  - passed；no whitespace errors。
- `rg -n "t[p]-[A-Za-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" src tests docs`
  - no matches；command exited with status 1 because `rg` found no real-token-like pattern。
- Broad fake-value scan note:
  - `rg ... "test-api-key" ...` intentionally matches existing tests/docs that use fake values；
  - adapter artifact redaction is covered by the integration test scanning the temp `.agent-runs` tree and asserting no `test-api-key` appears in generated artifacts。

Adapter mapping evidence：

- `answer`:
  - `displayText` 来自 `source.output.answer`；
  - `requiresConfirmation=false`；
  - `requiresApproval=false`；
  - `wroteWorkspace=false`。
- provider-backed `answer` with injected fetch:
  - `displayText` 来自 provider-backed SDK answer；
  - `artifactRefs` 包含 redacted raw provider request/response refs；
  - this is injected-provider evidence, not a real external provider call。
- `candidate-patch`:
  - `displayText` 使用 candidate patch rationale；
  - `requiresApproval=true`；
  - `requiresConfirmation=false`；
  - `targetWorkspacePaths` only means proposed target paths, not published files。
- `confirmation`:
  - `displayText` joins confirmation questions；
  - `requiresConfirmation=true`；
  - `requiresApproval=false`。
- fixed workflow `route-preview`:
  - `requiresApproval=true`；
  - `wroteWorkspace=false`；
  - `displayText=null`。
- fixed workflow execution:
  - `outputKind="workflow-report"`；
  - `requiresApproval=false`；
  - `wroteWorkspace=true` from SDK envelope；
  - `displayText=null`。

No-write evidence：

- Candidate patch adapter test asserts:
  - `wroteWorkspace === false`；
  - `targetWorkspacePaths === ["knowledge-base/drafts/adapter-candidate.md"]`；
  - `knowledge-base/drafts/adapter-candidate.md` does not exist after the run。
- Fixed workflow preview adapter test asserts `wroteWorkspace === false`。
- Fixed workflow execution adapter test asserts `wroteWorkspace === true` only for the executed fixed workflow path。

Redaction evidence：

- Adapter injected-provider test scans the temp workspace `.agent-runs` tree:
  - artifacts contain `[REDACTED]`；
  - artifacts do not contain `test-api-key`。
- Adapter exposes raw provider artifacts only as `artifactRefs`; it does not inline raw provider payloads into backend response fields.

Real external call evidence：

- Initial opt-in real MiMo smoke after Phase 37:
  - provider: `mimo-open-agent-smoke`；
  - mode: `llm-graph`；
  - model: `mimo-v2.5`；
  - outputPolicy: `ANSWER_ONLY`；
  - temp fixture workspace: `/private/tmp/open-agent-mimo-smoke.tat4y1`；
  - realExternalCall: `true`；
  - providerCalls: `3`；
  - raw provider refs: `open-agent-plan-1`, `open-agent-next-action-2`, `open-agent-synthesize-3`；
  - result: `FAILED` because graph status was `FAILED_VALIDATION`；
  - failing step: `SELF_CHECK`；
  - failure reason: `Provider synthesis content must include its grounding refs.`。
- Root cause:
  - provider returned valid structured `groundingRefs`；
  - answer content used numbered citations such as `[1]` instead of embedding exact path strings；
  - `SELF_CHECK` required exact path strings in synthesized content, while the provider prompt only said to cite provided refs。
- Follow-up fix:
  - provider-backed synthesis now deterministically materializes missing exact source refs into answer/draft/candidate content before `SELF_CHECK`；
  - strict `SELF_CHECK` remains unchanged and still requires exact refs in the final artifact content。
- Follow-up RED/GREEN evidence:
  - RED: `npm test -- tests/unit/open-agent-graph-nodes.test.ts` failed with `expected 'FAILED_VALIDATION' to be 'SUCCEEDED'` for a provider answer that used `[1]` citations and valid `groundingRefs`；
  - GREEN: `npm test -- tests/unit/open-agent-graph-nodes.test.ts` passed with 11 tests。
- Follow-up focused verification:
  - `npm test -- tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts tests/integration/agent-sdk-backend-adapter.test.ts`
  - 3 test files / 17 tests passed。
- Follow-up real MiMo smoke after fix:
  - temp fixture workspace: `/private/tmp/open-agent-mimo-smoke.3L55d0`；
  - status: `PASSED`；
  - graph status: `SUCCEEDED`；
  - realExternalCall: `true`；
  - providerCalls: `3`；
  - `SELF_CHECK`: `SUCCEEDED`；
  - raw provider refs: `open-agent-plan-1`, `open-agent-next-action-2`, `open-agent-synthesize-3`。
- Follow-up redaction / no-write evidence:
  - token pattern scan under `/private/tmp/open-agent-mimo-smoke.3L55d0/.agent-runs/open-agent` returned no matches；
  - raw request artifact contains `Authorization: "[REDACTED]"`；
  - `knowledge-base/drafts/mimo-open-agent-smoke.md` does not exist in the temp fixture workspace。

Known limitations / Review risks：

- `source` intentionally carries the full `AgentSdkRunResult`; backend callers should treat it as the audit envelope and prefer top-level backend fields for UI/approval decisions。
- `artifactRefs` intentionally excludes `artifactRoot`; it only includes concrete SDK artifact refs: `artifactPath`、`reportPath`、`tracePath`、`rawProviderRefs`。
- This phase does not add HTTP server、DB schema、auth、approval UI or real backend controller。

## Phase 38 - Open Agent Source Materialization Hardening

交付内容：

- Provider-backed synthesis source materialization 从 answer 扩展到 draft 和 candidate patch。
- Final answer 继续使用 `Sources:`。
- Final draft / candidate Markdown content 使用 `## Sources`。
- Strict `SELF_CHECK` 保持不变：final content 必须包含 exact grounding ref path。
- Candidate patch 仍由 runtime deterministic 包装：
  - `publishable: false`；
  - deterministic target path under `knowledge-base/drafts/<taskId>.md`；
  - deterministic content sha；
  - no workspace write。

Changed files：

- `docs/architecture/open-agent-provider-backed-synthesis-spec.md`；
- `docs/superpowers/plans/2026-06-14-open-agent-source-materialization.md`；
- `src/runtime/open-agent/nodes/synthesize-node.ts`；
- `tests/unit/open-agent-graph-nodes.test.ts`；
- `docs/reports/runtime-work-item-execution-resume-delivery.md`。

RED evidence：

- `npm test -- tests/unit/open-agent-graph-nodes.test.ts`
  - failed as expected for draft/candidate numbered citation cases；
  - failure reason:
    - expected provider draft content to contain `## Sources`, but final content contained plain `Sources:`；
    - expected provider candidate content to contain `## Sources`, but final content contained plain `Sources:`。

Focused verification：

- `npm test -- tests/unit/open-agent-graph-nodes.test.ts`
  - 1 test file / 13 tests passed。
- `npm test -- tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts tests/integration/agent-sdk-backend-adapter.test.ts`
  - 3 test files / 19 tests passed。

Full verification：

- `npm test`
  - 43 test files / 177 tests passed。
- `npm run typecheck`
  - passed。
- `git diff --check`
  - passed；no whitespace errors。
- `rg -n "t[p]-[A-Za-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" src tests docs`
  - no matches；command exited with status 1 because `rg` found no real-token-like pattern。

Evidence boundaries：

- Draft/candidate source materialization evidence is fake/injected provider unit evidence, not a new real external provider call。
- No new real MiMo smoke was required for this phase because the changed behavior affects Markdown formatting for draft/candidate provider outputs; the Phase 37 follow-up real `ANSWER_ONLY` smoke remains the current real external call evidence。
- This phase does not change provider schema, public SDK contract, backend adapter contract, or workspace write rules。

## Phase J1 - Java Platform Skeleton

Status: implemented.

Scope:

- Added `backend/` Maven Spring Boot skeleton.
- Added common API envelope with schema version `java-backend-api.v1`.
- Added `/health` and `/ready` endpoints.
- Added stable error envelope coverage for missing route, unsupported method, and unexpected exception.
- Added springdoc OpenAPI metadata and `/v3/api-docs` contract smoke for `application/json` envelope response schema.
- Disabled actuator web endpoint exposure for Phase J1 so public ops endpoints use the project API envelope.
- Added `.gitignore` coverage for `backend/target/`.
- Did not add DB schema, auth, workspace model, agent worker bridge, provider integration, remote runner, or frontend.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - Initial failure matched the expected missing backend project: `POM file backend/pom.xml ... does not exist`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApiEnvelopeTest test`
  - Initial failure matched missing `ApiEnvelope` / `ApiError` symbols.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpsControllerTest test`
  - Initial failure matched missing endpoints: `/health` and `/ready` returned 404.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest test`
  - Initial failure matched missing fixed OpenAPI metadata: expected title `My Workflow Agent Backend API`, actual `OpenAPI definition`.
- Reviewer follow-up: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest,ApiErrorEnvelopeTest test`
  - Failed as expected before the fix:
    - missing route and unsupported method returned `INTERNAL_ERROR` / 500 instead of 404/405 envelopes;
    - unexpected exception returned `retryable=true`;
    - OpenAPI exposed the response schema under `*/*` rather than stable `application/json`.
- Reviewer follow-up: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpsControllerTest test`
  - Failed as expected before actuator exposure was disabled: `/actuator/health` returned raw actuator JSON with status 200.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -DskipTests package`
  - PASS.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApiEnvelopeTest test`
  - 1 test class / 2 tests passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpsControllerTest test`
  - 1 test class / 3 tests passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest test`
  - 1 test class / 1 test passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest,ApiErrorEnvelopeTest test`
  - 2 test classes / 4 tests passed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 4 test classes / 9 tests passed.
- `npm test`
  - 43 test files / 177 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed for tracked diffs; no whitespace errors.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-platform-skeleton.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches; covers untracked Phase J1 files that `git diff --check` does not inspect before staging.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests`
  - no matches; command exited with status 1 because `rg` found no long token-like pattern.

Environment facts:

- System `mvn` was not on PATH.
- IntelliJ bundled Maven was used: `/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`.
- Local Java was Amazon Corretto 18.0.2.
- Phase J1 compiles with Java 17 bytecode baseline because the local JDK is 18; the long-term platform spec still targets Java 21 unless team infrastructure requires Java 17.
- Spring Boot `3.5.15` was selected after checking Spring Initializr metadata; no production dependency was added outside the new Java backend project.

Evidence boundaries:

- Java tests are local unit/integration tests using JUnit 5, AssertJ, MockMvc, and springdoc.
- No real provider call was executed for Phase J1.
- No TypeScript worker bridge was executed for Phase J1.
- The Java backend skeleton does not import or parse TypeScript runtime result shapes.
- Reviewer subagent reported no Critical issues. Important findings were fixed in this phase: OpenAPI envelope schema coverage, framework-level error envelope coverage, and untracked-file whitespace verification. The actuator exposure concern was also closed by disabling actuator web endpoints in Phase J1.

## Phase J2A - Java Workspace And Identity Baseline

Status: implemented for J2A. Full J2 remains incomplete until the identity/workspace schema is executed against real MySQL/Testcontainers and a DB-backed repository is added.

Scope:

- Added dev principal provider and `GET /v1/me`.
- Added `POST /v1/workspaces`, `GET /v1/workspaces`, and `GET /v1/workspaces/{workspaceId}`.
- Added service-owned workspace storage refs under configured data root.
- Added workspace permission guard and path traversal/absolute path rejection.
- Added stable error envelope codes: `WORKSPACE_NOT_FOUND`, `WORKSPACE_FORBIDDEN`, `WORKSPACE_PATH_INVALID`, and `INVALID_REQUEST`.
- Added MySQL-oriented Flyway migration artifact `V2__identity_workspace_baseline.sql` for users, teams, memberships, workspaces, and workspace members.
- Rejected unknown JSON fields so public API does not silently accept client-supplied `workspaceRoot`.
- Did not add Spring Security/OIDC/RBAC, JDBC/Flyway runtime wiring, agent run/job, TS worker bridge, artifact registry, approval API, or real provider calls.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest test`
  - Initial failure matched missing `/v1/me`: expected 200, got 404.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceControllerTest test`
  - Initial failure matched missing workspace API: expected 200 for `POST /v1/workspaces`, got 404.
  - Missing workspace initially returned generic `NOT_FOUND`; after workspace API implementation it returns `WORKSPACE_NOT_FOUND`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceSchemaArtifactTest test`
  - Initial failure matched missing migration file: `NoSuchFileException`.
- Additional API boundary RED:
  - `WorkspaceControllerTest.workspaceRegistrationRejectsClientSuppliedWorkspaceRoot` initially failed because unknown `workspaceRoot` was ignored and returned 200.
- Internal path isolation RED:
  - `WorkspaceServiceTest.rejectsRepositoryStorageRefsOutsideDataRoot` initially failed because a malicious repository `serverStorageRef` could resolve outside `dataRoot`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest,WorkspaceControllerTest,WorkspaceServiceTest,WorkspaceSchemaArtifactTest test`
  - 4 test classes / 9 tests passed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 8 test classes / 18 tests passed.
- `npm test`
  - 43 test files / 177 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed for tracked diffs.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-identity-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches; covers untracked Phase J2A files that `git diff --check` does not inspect before staging.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests`
  - no matches; command exited with status 1 because `rg` found no long token-like pattern.

Evidence boundaries:

- Initial `WorkspaceServiceTest` cases are post-implementation coverage for service guard behavior because the initial workspace controller implementation introduced `WorkspaceService` and path resolution together. The later malicious `serverStorageRef` case is strict RED evidence for internal storage-ref boundary hardening.
- `WorkspaceSchemaArtifactTest` verifies the schema artifact content only. It is not real MySQL migration execution evidence.
- Docker daemon was unavailable: `docker ps` could not connect to `/Users/didi/.docker/run/docker.sock`.
- The runtime workspace repository is still an in-memory J2A implementation behind `WorkspaceRepository`; full J2 must add DB-backed repository and MySQL/Testcontainers verification.

## Phase J2B - Java Workspace JDBC Baseline

Status: implemented. Phase J2 is now closed for the planned workspace/identity baseline; J3+ remains unimplemented.

Scope:

- Added Spring JDBC, Flyway, Flyway MySQL, MySQL Connector/J, and Testcontainers dependencies to the Java backend.
- Added `jdbc` profile wiring for `DataSource`, `JdbcTemplate`, and explicit Flyway migration execution.
- Kept `InMemoryWorkspaceRepository` as the default non-`jdbc` local implementation.
- Added `JdbcWorkspaceRepository` behind the `jdbc` profile.
- Added shared `WorkspaceRepositoryContract` coverage for in-memory and JDBC repositories.
- Added Testcontainers MySQL repository verification that applies `V2__identity_workspace_baseline.sql`.
- Added JDBC-profile MockMvc smoke proving workspace registration/listing works through MySQL.
- Preserved public API boundaries:
  - API still uses `workspaceId`, not public absolute paths.
  - response still does not expose `workspaceRoot` or internal `serverStorageRef`.
  - client-supplied `workspaceRoot` remains rejected.
  - candidate patch / agent runtime behavior was not changed.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest test`
  - Initial failure matched missing JDBC/Flyway/Testcontainers classes and missing `JdbcWorkspaceRepository`.
- After adding dependencies, the same command failed only on the missing `JdbcWorkspaceRepository` symbol, confirming the test fixture and dependency setup had moved to the intended RED point.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - 2 test classes / 2 tests passed.
  - Testcontainers started MySQL `8.4.0`; Flyway applied `V2__identity_workspace_baseline.sql`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceJdbcControllerTest test`
  - 1 test class / 1 test passed.
  - JDBC profile HTTP smoke persisted/listed workspace data through MySQL and rejected `workspaceRoot`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest,WorkspaceJdbcControllerTest test`
  - 2 test classes / 2 tests passed with real Testcontainers MySQL containers.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 21 Java tests passed.
- `npm test`
  - 43 test files / 177 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches; command exited with status 1 because `rg` found no pattern.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches; command exited with status 1 because `rg` found no long token-like pattern.

Environment facts:

- Docker Desktop was started locally before J2B DB verification.
- `docker ps` succeeded before Testcontainers tests.
- Testcontainers connected to Docker Desktop and started `testcontainers/ryuk:0.12.0` plus MySQL `8.4.0`.
- Flyway emitted a compatibility warning because MySQL `8.4` is newer than the latest MySQL version explicitly tested by this Flyway release (`8.1`). Migration validation and application still succeeded.

Evidence boundaries:

- MySQL/Testcontainers evidence is a real local container integration test, not a mock repository test.
- No real LLM/provider call was executed for Phase J2B.
- No TypeScript worker bridge was executed for Phase J2B.
- Java still does not import or parse `src/runtime/*` result shapes.
- Full J2 covers the current dev-principal identity/workspace baseline only; Spring Security/OIDC/RBAC remains Phase J7.

## Phase J3A - Java Async Agent Run And Local TS Worker Bridge

Status: implemented for J3A. The async run baseline is in place; cancel/retry policy deepening, approval decision APIs, artifact registry, and remote runner remain future phases.

Scope:

- Added `V3__agent_run_job_baseline.sql` for:
  - `agent_runs`;
  - `agent_jobs`;
  - `run_attempts`.
- Added Java run/job state model:
  - `AgentRunStatus`;
  - `AgentJobStatus`;
  - `AgentRunRecord`;
  - `AgentJobRecord`;
  - `RunAttemptRecord`.
- Added `AgentRunRepository` with:
  - default in-memory implementation for local non-DB profile;
  - `jdbc` profile implementation backed by MySQL/Flyway.
- Added `AgentWorker` port plus `LocalTsAgentWorker` process adapter.
- Added `src/cli/backend-agent-worker.ts`:
  - reads a backend request from stdin;
  - calls `runBackendAgent()`;
  - writes `agent-backend-response.v1` JSON to stdout.
- Added async Java API:
  - `POST /v1/workspaces/{workspaceId}/agent-runs`;
  - `GET /v1/agent-runs/{runId}`.
- Added in-process executor:
  - creates run/job records first;
  - returns queued metadata immediately;
  - updates run/job/attempt after worker completion.
- Preserved workspace boundary:
  - public API accepts `workspaceId`, not `workspaceRoot`;
  - Java resolves server-hosted workspace root internally;
  - existing workspace permission guard is used before run creation and run reads.
- Preserved runtime boundary:
  - Java parses only stable top-level `agent-backend-response.v1` fields;
  - Java response model ignores unknown worker fields, including TS adapter `source`;
  - Java code does not import or parse `src/runtime/*`, OpenAgentGraph, fixed workflow internals, or runtime-private result shape.
- Added local/fake execution evidence:
  - Java API -> local TS worker -> TS backend adapter smoke for deterministic answer;
  - candidate patch response maps to `WAITING_APPROVAL` and `wroteWorkspace=false`.

RED evidence:

- `npm test -- tests/integration/backend-agent-worker-cli.test.ts`
  - Failed first with exit code `1` because `src/cli/backend-agent-worker.ts` did not exist.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunControllerTest,JdbcAgentRunRepositoryTest,LocalTsAgentWorkerTest test`
  - Failed first at Java test compile because `AgentWorker`, request/response records, run/job records, JDBC repository, and local worker adapter did not exist.
- First GREEN attempt found one wiring issue:
  - Spring could not instantiate `LocalTsAgentWorker` because the class had two constructors and no explicit `@Autowired`.
  - Root cause was fixed by annotating the production constructor.

Focused verification:

- `npm test -- tests/integration/backend-agent-worker-cli.test.ts`
  - 1 test passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunControllerTest,AgentRunLocalTsControllerTest,JdbcAgentRunRepositoryTest,LocalTsAgentWorkerTest test`
  - 4 test classes / 6 tests passed.
  - Covers async queued-before-worker-completion, polling completion, candidate patch approval-required mapping, local TS worker adapter, and Java API -> local TS worker smoke.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=LocalTsAgentWorkerTest test`
  - 1 test passed after removing a stale import.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 27 Java tests passed.
  - Testcontainers MySQL applied V2 and V3 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J3A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J3A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J3A archive updates.

Environment facts:

- Docker Desktop was reachable for Testcontainers.
- Testcontainers started MySQL `8.4.0`; Flyway applied V2 and V3.
- Flyway still warns that MySQL `8.4` is newer than the latest explicitly tested MySQL version for this Flyway release (`8.1`).
- Local TS worker tests use the local Node/tsx runtime and fixture workspace.

Evidence boundaries:

- Java API -> local TS worker smoke is a real local process bridge, but it is not a real external provider call.
- J3A provider behavior uses deterministic/fake local paths only; no real MiMo/DeepSeek/Claude call was executed.
- J3A does not persist raw TS adapter `source` payload in Java DB.
- J3A does not implement approval decisions or artifact read APIs; it only records approval-required run state.

## Phase J4A - Java Artifact Registry Baseline

Status: implemented for J4A. Artifact metadata/list/read baseline is in place; object storage, retention, binary streaming, unredacted raw provider access, approval decisions, and publish flow remain future phases.

Scope:

- Added `V4__artifact_registry_baseline.sql` for `artifact_refs`.
- Added artifact domain and repository boundary:
  - `ArtifactRefRecord`;
  - `ArtifactRepository`;
  - `InMemoryArtifactRepository`;
  - `JdbcArtifactRepository`.
- Added artifact registration after worker response validation and before run completion is marked visible.
- Added artifact APIs:
  - `GET /v1/agent-runs/{runId}/artifacts`;
  - `GET /v1/artifacts/{artifactId}`.
- Added path safety boundary:
  - worker artifact refs must be relative;
  - absolute and traversal refs are not registered;
  - read endpoint resolves through `WorkspaceService.resolveContentPath()`.
- Added public response boundary:
  - response returns `artifactId` and relative `artifactRef`;
  - response does not expose server absolute path or internal `serverStorageRef`.
- Added raw provider metadata classification:
  - refs under `/raw-provider/` are `kind=RAW_PROVIDER`;
  - redaction status is `REDACTED`.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ArtifactControllerTest,JdbcArtifactRepositoryTest test`
  - Failed first at Java test compile because `ArtifactRepository`, `JdbcArtifactRepository`, and `ArtifactRefRecord` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ArtifactControllerTest,JdbcArtifactRepositoryTest test`
  - 2 test classes / 2 tests passed.
  - Covers artifact list/read API, no absolute path in public response, raw provider metadata marked redacted, and Testcontainers MySQL V2+V3+V4 migration.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 29 Java tests passed.
  - Testcontainers MySQL applied V2, V3, and V4 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J4A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J4A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J4A archive updates.

Evidence boundaries:

- Artifact API tests use fake local worker output and redacted fixture raw content.
- J4A did not execute a real external provider call.
- J4A does not prove object storage, binary streaming, retention, or unredacted raw payload access.

## Phase J5A - Java Approval Boundary Baseline

Status: implemented for J5A. Approval request and decision metadata are in place; candidate patch publishing, fixed workflow execution after approval, audit log, RBAC, and remote runner remain future phases.

Scope:

- Added `V5__approval_request_baseline.sql` for `approval_requests`.
- Added approval domain and repository boundary:
  - `ApprovalDecision`;
  - `ApprovalStatus`;
  - `ApprovalRequestRecord`;
  - `ApprovalRepository`;
  - `InMemoryApprovalRepository`;
  - `JdbcApprovalRepository`.
- Added approval APIs:
  - `GET /v1/agent-runs/{runId}/approvals`;
  - `POST /v1/agent-runs/{runId}/approvals`.
- Integrated approval request creation into `AgentRunService`:
  - worker response is validated first;
  - artifacts are registered first;
  - if `requiresApproval=true`, a pending approval request is created before the run is marked complete as `WAITING_APPROVAL`.
- Approval request metadata includes:
  - run id;
  - workspace id;
  - requested user id;
  - artifact ref;
  - proposed `targetWorkspacePaths`;
  - status and decision metadata.
- Confirmation responses do not create approval requests.
- Approval decisions update metadata only. They do not publish candidate patches or execute fixed workflow routes.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApprovalControllerTest,JdbcApprovalRepositoryTest test`
  - Failed first at Java test compile because `ApprovalRepository`, `JdbcApprovalRepository`, `ApprovalRequestRecord`, `ApprovalStatus`, and `ApprovalDecision` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApprovalControllerTest,JdbcApprovalRepositoryTest test`
  - 2 test classes / 4 tests passed.
  - Covers candidate patch pending approval creation, rejection no-write behavior, confirmation no approval request, unknown `workspaceRoot` rejection, and Testcontainers MySQL V2+V3+V4+V5 migration.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 33 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J5A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J5A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J5A archive updates.

Evidence boundaries:

- Approval API tests use fake local worker output only.
- J5A did not execute a real external provider call.
- J5A does not prove publish-after-approval or fixed-workflow execution-after-approval.

## Phase J6A - Java Job Reliability Cancel Baseline

Status: implemented for J6A. Cancel-safe terminal guard baseline is in place; retry scheduling, stale lock recovery, progress events, audit, RBAC, process interruption, and remote runner remain future phases.

Scope:

- Added `POST /v1/agent-runs/{runId}/cancel`.
- Added `AgentRunRepository.cancel(runId, now)` for:
  - `InMemoryAgentRunRepository`;
  - `JdbcAgentRunRepository`.
- Cancellation is scoped to queued/running run/job state.
- Cancel persists:
  - `AgentRun.status=CANCELED`;
  - queued/running `AgentJob.status=CANCELED`;
  - open `RunAttempt.status=CANCELED` with `finishedAt`.
- Added terminal guard:
  - `complete()` no-ops if the run is already terminal, including `CANCELED`;
  - `fail()` no-ops if the run is already terminal, including `CANCELED`;
  - `markRunning()` no-ops if the run is already terminal.
- `AgentRunService.cancelRun()` reuses existing `getRun()` workspace guard before canceling.
- `AgentRunService` checks `CANCELED` before registering late worker artifacts or approval requests.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunCancelControllerTest,AgentRunRepositoryReliabilityTest,AgentRunFailureControllerTest test`
  - Failed first at Java test compile because `InMemoryAgentRunRepository.cancel(String, Instant)` did not exist.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunCancelControllerTest,AgentRunRepositoryReliabilityTest,AgentRunFailureControllerTest,JdbcAgentRunRepositoryTest test`
  - Failed at Java test compile because both in-memory and JDBC repository contracts lacked `cancel(String, Instant)`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunCancelControllerTest,AgentRunRepositoryReliabilityTest,AgentRunFailureControllerTest,JdbcAgentRunRepositoryTest test`
  - 5 tests passed.
  - Covers cancel endpoint, late worker success preserving `CANCELED`, worker failure to `FAILED`, in-memory terminal guard, and Testcontainers MySQL JDBC terminal guard.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 37 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6A archive updates.

Evidence boundaries:

- J6A tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J6A did not execute a real external provider call.
- J6A does not prove process interruption, retry scheduling, stale lock recovery, progress events, audit, RBAC, or remote runner behavior.

## Phase J6B - Java Job Retry Baseline

Status: implemented for J6B. Retry policy baseline is in place; delayed scheduling, backoff, stale lock recovery, timeout policy, progress events, audit, RBAC, and remote runner remain future phases.

Scope:

- Added `AgentRunRepository.retry(runId, jobId, errorCode, now)` for:
  - `InMemoryAgentRunRepository`;
  - `JdbcAgentRunRepository`.
- Added `AgentJobRecord.retry(now)` to re-queue a failed local attempt without changing schema.
- Added in-process retry loop in `AgentRunService`:
  - worker exceptions retry immediately up to `AgentJob.maxAttempts`;
  - transient failure can complete as `SUCCEEDED`;
  - exhausted attempts produce final `FAILED` with `errorCode=WORKER_FAILED`.
- Retry persistence:
  - closes current `RunAttempt` as `FAILED`;
  - clears job lock metadata;
  - sets run/job back to `QUEUED` for another local attempt.
- Retry state guard:
  - retry only transitions from `RUNNING` to `QUEUED`;
  - retry does not revive `CANCELED` or other terminal states.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRetryControllerTest,AgentRunFailureControllerTest,AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - Failed first at Java test compile because `retry(String, String, String, Instant)` did not exist on the in-memory/JDBC repository contract.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRetryControllerTest,AgentRunFailureControllerTest,AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - 8 tests passed.
  - Covers transient worker failure retrying to success, exhausted failures producing 3 failed attempts and final `FAILED`, in-memory retry/cancel guard, and Testcontainers MySQL JDBC retry persistence.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 41 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6B archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6B archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6B archive updates.

Evidence boundaries:

- J6B tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J6B did not execute a real external provider call.
- J6B does not prove delayed retry scheduling, backoff, stale lock recovery, timeout policy, progress events, audit, RBAC, or remote runner behavior.

## Phase J6C - Java Stale Lock Recovery Baseline

Status: implemented for J6C. Stale lock recovery repository baseline is in place; scheduler wiring, timed recovery cadence, progress events, audit, RBAC, and remote runner lease/heartbeat remain future phases.

Scope:

- Added `AgentRunRepository.failStaleRunningJobs(staleBefore, errorCode, now)` for:
  - `InMemoryAgentRunRepository`;
  - `JdbcAgentRunRepository`.
- Uses existing `AgentJob.lockedUntil`; no schema migration was added.
- Recovery behavior:
  - only jobs in `RUNNING` with `lockedUntil <= staleBefore` are recovered;
  - matching run is marked `FAILED` with the supplied stale-lock error code;
  - matching job is marked `FAILED` and lock metadata is cleared;
  - latest open attempt is closed as `FAILED` with the same error code.
- Non-stale running jobs are not changed.
- Terminal runs remain guarded by existing state checks and are not revived.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - Failed first at Java test compile because `failStaleRunningJobs(Instant, String, Instant)` did not exist on the in-memory/JDBC repository contract.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - 9 tests passed.
  - Covers in-memory stale recovery, fresh lock no-op, cancel/retry terminal guards, and Testcontainers MySQL stale recovery.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 44 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6C archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6C archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6C archive updates.

Evidence boundaries:

- J6C tests use local repository objects and local Testcontainers MySQL.
- J6C did not execute a real external provider call.
- J6C does not prove scheduler wiring, timed recovery cadence, progress events, audit, RBAC, or remote runner heartbeat/lease behavior.

## Phase J6D - Java Run Event Baseline

Status: implemented for J6D. Durable run event history baseline is in place. Bounded run event SSE is delivered later by J17A.

WebSocket / production multi-node streaming, audit/RBAC event coverage, scheduler-driven recovery events, and remote runner heartbeat/lease events remain future phases.

Scope:

- Added `run_events` schema via Flyway `V6__run_event_baseline.sql`.
- Added run event domain model and repository boundary:
  - `RunEventRecord`;
  - `RunEventRepository`;
  - `InMemoryRunEventRepository`;
  - `JdbcRunEventRepository`.
- Added `RunEventService` and `RunEventController`.
- Added `GET /v1/agent-runs/{runId}/events`.
- `RunEventService` reuses `AgentRunService.getRun(runId)` for the existing run/workspace guard before listing events.
- `AgentRunService` appends backend-owned lifecycle events:
  - `RUN_QUEUED`;
  - `RUNNING`;
  - `RETRY_QUEUED`;
  - `COMPLETED`;
  - `FAILED`;
  - `CANCELED`.
- Public event response exposes stable metadata only and does not include workspace internal paths, raw provider payloads, worker raw output, tokens, or runtime-private source.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest,JdbcRunEventRepositoryTest test`
  - Failed first at Java test compile because `JdbcRunEventRepository` and `RunEventRecord` did not exist.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test`
  - Failed after the first implementation because a run recovered as `FAILED(STALE_LOCK)` could still receive a late worker success and incorrectly append `COMPLETED/SUCCEEDED` to event history.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest,JdbcRunEventRepositoryTest test`
  - 3 tests passed.
  - Covers HTTP event listing for a completed run, response redaction boundaries, ordered lifecycle events, no false `COMPLETED` event after stale recovery wins the terminal state, and Testcontainers MySQL event persistence.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 47 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, V5, and V6 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6D archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6D archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6D archive updates.

Evidence boundaries:

- J6D tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J6D did not execute a real external provider call.
- J6D does not prove SSE streaming, WebSocket / production multi-node streaming, audit/RBAC coverage, scheduler-driven recovery events, or remote runner heartbeat/lease behavior. J17A later adds the bounded SSE baseline only.

## Phase J7A - Java Team RBAC And Audit Baseline

Status: implemented for J7A. Local-dev SecurityContext principal, workspace role guard, and append-only audit metadata baseline are in place; full OIDC/OAuth, public user/team directory APIs, audit listing API, provider secret policy, WebSocket / production multi-node stream authorization, and remote runner authorization remain future phases. Narrow public workspace member management is delivered later by J10A. J17A later adds bounded SSE stream authorization through the existing run/workspace guard.

Scope:

- Added Spring Security dev-header authentication:
  - `X-Dev-User-Id`;
  - `X-Dev-Team-Id`;
  - `X-Dev-Display-Name`.
- Kept configured dev principal fallback for local calls without dev headers.
- Moved current-principal lookup behind `SecurityContext` via `DevPrincipalProvider`.
- Added workspace role lookup/grant support for in-memory and JDBC repositories.
- Enforced workspace role hierarchy in service layer:
  - viewer or higher can read workspace/run/artifact/approval data;
  - editor or owner can start/cancel runs and decide approvals.
- Added `audit_events` schema via Flyway `V7__audit_event_baseline.sql`.
- Added audit domain model and repository boundary:
  - `AuditEventRecord`;
  - `AuditRepository`;
  - `InMemoryAuditRepository`;
  - `JdbcAuditRepository`;
  - `AuditService`.
- Recorded audit metadata for:
  - `WORKSPACE_CREATED`;
  - `AGENT_RUN_REQUESTED`;
  - `AGENT_RUN_CANCELED`;
  - `APPROVAL_DECIDED`;
  - `ARTIFACT_READ`.
- Public tests assert audit metadata, not raw provider payloads or token values.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=SecurityPrincipalControllerTest,WorkspaceRoleAuthorizationTest,JdbcAuditRepositoryTest test`
  - Failed first at Java test compile because `AuditEventRecord`, `AuditRepository`, `JdbcAuditRepository`, and `WorkspaceRepository.grantAccess(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=SecurityPrincipalControllerTest,WorkspaceRoleAuthorizationTest,JdbcAuditRepositoryTest test`
  - 4 tests passed.
  - Covers dev-header principal override, default dev fallback, viewer read/write denial boundaries, editor approval decision, audit actor/team/workspace/run metadata, and Testcontainers MySQL audit persistence.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 51 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, V5, V6, and V7 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J7A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J7A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J7A archive updates.

Evidence boundaries:

- J7A tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J7A did not execute a real external provider call.
- J7A dev-header authentication is local-dev baseline, not production OIDC/OAuth.
- J7A workspace membership is role truth source; J7A itself did not include public member management API. J10A later adds a narrow owner-only grant/list baseline.
- J7A audit events are append-only metadata, but there is no public audit listing API yet.
- J7A does not prove remote runner authorization, runner lease security, WebSocket / production multi-node stream authorization, or provider secret policy. J17A later adds bounded SSE stream authorization through the existing run/workspace guard.

## Phase J8A - Java Remote Runner Contract Spike

Status: implemented for J8A. Configurable worker implementation selection and a remote HTTP worker contract spike are in place; production remote runner platform features remain future work.

Scope:

- Added `AgentWorker.workerKind()`:
  - default `LOCAL_TS_WORKER`;
  - remote HTTP worker returns `REMOTE_RUNNER`.
- Kept `LocalTsAgentWorker` as the default worker via:
  - `my-workflow.backend.agent-worker.kind=local-ts`;
  - `matchIfMissing=true`.
- Added opt-in `RemoteHttpAgentWorker` via:
  - `my-workflow.backend.agent-worker.kind=remote-http`;
  - `my-workflow.backend.agent-worker.remote-http.endpoint-url`;
  - `my-workflow.backend.agent-worker.remote-http.timeout-ms`.
- Remote worker transport:
  - POSTs the existing `AgentWorkerRequest`.
  - Requires `agent-remote-runner-result.v1`.
  - Requires `workerKind=REMOTE_RUNNER`.
  - Requires `signatureKind=unsigned-local-spike`.
  - Returns the nested `agent-backend-response.v1` result to existing run logic.
- `AgentRunService` now records run attempts with the selected worker kind instead of a hardcoded local worker kind.
- Tests prove runtime-private `source` in remote response is ignored by Java mapping.

RED evidence:

- First focused RED run exposed a test fixture error: Java `Map.of(...)` cannot build maps with more than 10 key/value pairs.
- After fixing the fixture, focused RED failed at Java test compile because `RemoteHttpAgentWorker` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test`
  - 3 tests passed.
  - Covers remote request mapping, response mapping, unsupported remote envelope rejection, runtime-private payload ignoring, Spring config selecting `remote-http`, and `RunAttempt.workerKind=REMOTE_RUNNER`.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 54 Java tests passed.
  - Full suite includes existing local TS worker coverage and J8A remote HTTP worker coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J8A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-remote-runner-contract-spike.md docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J8A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J8A archive updates.

Evidence boundaries:

- J8A remote worker tests use local JDK `HttpServer`; this is fake/local HTTP spike evidence, not a real remote runner service.
- `signatureKind=unsigned-local-spike` is not cryptographic signature verification.
- J8A did not execute a real external provider call.
- J8A does not implement runner registration, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, remote runner authorization, or runner-scoped secret access.
- J8A does not change public API, DB schema, or provider secret behavior.

## Phase J9A - Java Provider Secret Policy Baseline

Status: implemented for J9A. Provider runtime references are now resolved by backend policy without accepting raw provider token values through the public run API; credential DB, secret manager, real provider calls, and remote runner secret distribution remain future work.

Scope:

- Added `ProviderRuntimePolicy`.
- Added optional `providerRuntimeRef` to `POST /v1/workspaces/{workspaceId}/agent-runs`.
- Backend resolves safe refs from:
  - `my-workflow.backend.provider-runtime.refs.<ref>.provider`;
  - optional `model`;
  - optional `base-url`;
  - optional `api-key-env-name`;
  - optional `timeout-ms`;
  - optional `max-tokens`;
  - optional `temperature`.
- Built-in `fake` ref remains available.
- Blank ref keeps existing default fake provider behavior for:
  - `llm-open-agent`;
  - executable `fixed-workflow`.
- Worker request receives TS-compatible `providerRuntime` metadata, including `apiKeyEnvName` only.
- Direct token-bearing `providerRuntime` request body remains rejected by strict request parsing.
- Public error response does not echo token-like request values.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - Failed first at Java test compile because `ProviderRuntimePolicy` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 6 tests passed.
  - Covers blank/default fake behavior, configured real provider ref mapping, unknown/invalid ref rejection, safe HTTP propagation to worker, token-value rejection, and no response echo of token-like request data.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 60 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J9A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-secret-policy-baseline.md docs/superpowers/plans/2026-06-14-java-remote-runner-contract-spike.md docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J9A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J9A archive updates.

Evidence boundaries:

- J9A tests use fake/injected worker behavior; no real external provider call was executed.
- J9A does not store, rotate, encrypt, or retrieve secret values.
- J9A does not add credential database tables or public credential management APIs.
- J9A does not distribute secrets to remote runners.
- J9A configured refs are local/backend config metadata, not team-scoped credential records.

## Phase J10A - Java Workspace Member Management Baseline

Status: implemented for J10A. The Java backend now has a narrow public workspace member management baseline: workspace viewers can list members, workspace owners can grant viewer/editor roles, and member grants are recorded as workspace-level audit metadata. Full user/team directory APIs, invitations, full OIDC/OAuth, credential DB/secret manager, WebSocket / production multi-node streaming, and production remote runner platform remain future work. Owner-only audit listing is delivered later by J11A, owner-only non-owner member removal is delivered later by J12A, owner transfer is delivered later by J15A, and bounded run event SSE is delivered later by J17A.

Scope:

- Added `WorkspaceMemberRecord`.
- Added `GET /v1/workspaces/{workspaceId}/members`.
  - Requires `WORKSPACE_VIEWER` or higher.
  - Returns stable API envelope data with `workspaceId`, `userId`, `teamId`, and `role`.
  - Does not expose `workspaceRoot`, `serverStorageRef`, provider payload, or token material.
- Added `PUT /v1/workspaces/{workspaceId}/members/{userId}`.
  - Requires `WORKSPACE_OWNER`.
  - Grants only `WORKSPACE_VIEWER` or `WORKSPACE_EDITOR`.
  - Rejects public `WORKSPACE_OWNER` grants.
  - Rejects `teamId` values that do not match the workspace team.
- Added `WorkspaceRepository.listMembers(...)` and covered it through the shared in-memory/JDBC repository contract.
- Added `AuditRepository.findByWorkspaceId(...)` and JDBC coverage for workspace-level audit events.
- Added `WORKSPACE_MEMBER_GRANTED` audit event for owner member grants.
- Did not add a schema migration; existing `workspace_members` and `audit_events` tables already support this baseline.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest,JdbcWorkspaceRepositoryTest,JdbcAuditRepositoryTest test`
  - Failed first at Java test compile because `AuditRepository.findByWorkspaceId(...)`, `WorkspaceRepository.listMembers(...)`, and `WorkspaceMemberRecord` did not exist.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - Failed after adding the repository team-mismatch contract because neither in-memory nor JDBC `grantAccess(...)` rejected a `teamId` that did not match the workspace team.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest,JdbcWorkspaceRepositoryTest,JdbcAuditRepositoryTest test`
  - 3 tests passed.
  - Covers owner grant/list, viewer read/list, viewer write denial, public owner grant rejection, team mismatch rejection, member response path secrecy, workspace-level audit lookup, and Testcontainers MySQL repository/audit persistence.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - 2 tests passed after adding repository-level team mismatch guards.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 61 Java tests passed.
  - Full suite includes in-memory and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J10A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-member-management-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J10A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-member-management-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J10A archive updates.

Evidence boundaries:

- J10A tests use local MockMvc, in-memory repositories, and Testcontainers MySQL.
- J10A did not execute a real external provider call.
- J10A does not implement full OIDC/OAuth, user/team directory CRUD, invitations, credential DB/secret manager, WebSocket / production multi-node streaming, or production remote runner authorization. J11A later adds owner-only public audit listing. J12A later adds owner-only non-owner member removal. J15A later adds workspace owner transfer. J17A later adds bounded run event SSE.
- J10A public owner grants are intentionally rejected; initial owner membership still comes from workspace creation.

## Phase J11A - Java Audit Listing API Baseline

Status: implemented for J11A. The Java backend now exposes owner-only workspace audit listing through a stable public API. Audit export, signed audit records, retention policy, public audit write API, full OIDC/OAuth, user/team directory APIs, credential DB/secret manager, WebSocket / production multi-node streaming, and production remote runner platform remain future work. Audit pagination/filtering is delivered later by J16A. Bounded run event SSE is delivered later by J17A.

Scope:

- Added `AuditQueryService`.
  - Calls `WorkspaceService.requireWorkspaceRole(workspaceId, WORKSPACE_OWNER)`.
  - Reads from existing `AuditRepository.findByWorkspaceId(...)`.
- Added `AuditController`.
  - `GET /v1/workspaces/{workspaceId}/audit-events`.
  - Returns `java-backend-api.v1` envelope.
  - Response fields are limited to `auditEventId`, `actorUserId`, `teamId`, `workspaceId`, `runId`, `eventType`, `message`, and `createdAt`.
- Public audit listing is owner-only:
  - owner receives audit metadata;
  - viewer receives `WORKSPACE_FORBIDDEN`;
  - missing workspace receives `WORKSPACE_NOT_FOUND`.
- No schema migration was needed; J7A/J10A audit storage already supports workspace-level lookup.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - Failed first with 404 `NOT_FOUND` for `GET /v1/workspaces/{workspaceId}/audit-events`, after workspace creation and member grant fixtures succeeded.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - 1 test passed.
  - Covers owner listing, viewer denial, missing workspace 404, `WORKSPACE_CREATED` and `WORKSPACE_MEMBER_GRANTED` visibility, and response exclusion for `workspaceRoot`, `serverStorageRef`, `Authorization`, `rawProvider`, and `token`.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 62 Java tests passed.
  - Full suite includes existing in-memory and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J11A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-listing-api-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J11A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J11A archive updates.

Evidence boundaries:

- J11A tests use local MockMvc and in-memory repositories.
- J11A did not execute a real external provider call.
- J11A does not implement audit export, retention policy, signed audit records, public audit write API, full OIDC/OAuth, user/team directory CRUD, credential DB/secret manager, WebSocket / production multi-node streaming, or production remote runner authorization. J16A later adds audit pagination/filtering. J17A later adds bounded run event SSE. J18A later adds audit export baseline.

## Phase J12A - Java Workspace Member Removal Baseline

Status: implemented for J12A. The Java backend now exposes owner-only removal for non-owner workspace members. The removed member loses workspace visibility/read access, and the backend records `WORKSPACE_MEMBER_REMOVED` as workspace-level audit metadata. Owner transfer, invitations, public user/team directory CRUD, full OIDC/OAuth, credential DB/secret manager, WebSocket / production multi-node streaming, and production remote runner platform remain future work. Bounded run event SSE is delivered later by J17A.

Scope:

- Added `DELETE /v1/workspaces/{workspaceId}/members/{userId}` to `WorkspaceController`.
  - Requires `WORKSPACE_OWNER`.
  - Returns the existing safe member response shape: `workspaceId`, `userId`, `teamId`, and `role`.
  - Does not expose `workspaceRoot`, `serverStorageRef`, provider payload, or token material.
- Added `WorkspaceService.removeMember(...)`.
  - Rejects viewer/editor callers through the existing workspace role guard.
  - Rejects owner removal through `VALIDATION_ERROR`.
  - Revokes only the selected non-owner workspace member.
  - Records `WORKSPACE_MEMBER_REMOVED` after successful revocation.
- Added `WorkspaceRepository.findMember(...)` and `WorkspaceRepository.revokeAccess(...)`.
  - In-memory and JDBC implementations share the repository contract.
  - JDBC removal stays scoped to the workspace's team boundary before deleting from `workspace_members`.
- No schema migration was needed; existing `workspace_members` and `audit_events` tables already support this baseline.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest test`
  - Failed first with 405 `METHOD_NOT_ALLOWED` for `DELETE /v1/workspaces/{workspaceId}/members/{userId}`, after workspace creation, grant, list, viewer read, viewer write denial, owner grant rejection, and team mismatch fixtures succeeded.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test`
  - Failed first at Java test compile because `WorkspaceRepository.findMember(...)` and `WorkspaceRepository.revokeAccess(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest test`
  - 1 test passed.
  - Covers viewer deletion denial, owner deletion rejection, owner removal of viewer, removed viewer losing workspace read access, safe member response shape, and `WORKSPACE_MEMBER_REMOVED` audit metadata.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - 2 tests passed.
  - Covers in-memory and Testcontainers MySQL member lookup, revoke success, access removal, visible workspace removal, and idempotent false return after the member is already removed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 62 Java tests passed.
  - Full suite includes existing MockMvc, in-memory repository, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J12A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-member-removal-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J12A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J12A archive updates.

Evidence boundaries:

- J12A tests use local MockMvc, in-memory repositories, and Testcontainers MySQL.
- J12A did not execute a real external provider call.
- J12A does not implement invitations, public user/team directory CRUD, full OIDC/OAuth, credential DB/secret manager, WebSocket / production multi-node streaming, audit export/retention/signed records, or production remote runner authorization. J15A later adds workspace owner transfer. J16A later adds audit pagination/filtering. J17A later adds bounded run event SSE. J18A later adds audit export baseline.
- J12A member removal only revokes workspace membership; it does not delete the user or team membership records.

## Phase J13A - Java Current Team Discovery Baseline

Status: implemented for J13A. The Java backend now exposes a narrow current-team discovery API so clients can discover the active principal's team id through the stable Java API. Full user/team directory CRUD, invitations, cross-team discovery, full OIDC/OAuth, credential DB/secret manager, WebSocket / production multi-node streaming, and production remote runner platform remain future work. Backend-known team member listing is delivered later by J14A. Bounded run event SSE is delivered later by J17A.

Scope:

- Added `GET /v1/teams` to `IdentityController`.
  - Returns `java-backend-api.v1` envelope.
  - Returns a single current-principal team record with `teamId`, `name`, and `status`.
  - Uses `PrincipalProvider.currentPrincipal()` as the local/dev source of truth.
  - Does not expose `workspaceRoot`, `serverStorageRef`, provider payload, or token material.
- No schema migration was needed.
- No repository or DB read path was added; this is current-team discovery, not a global directory.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest test`
  - Failed first with 404 `NOT_FOUND` for `GET /v1/teams`, while the existing `/v1/me` fixture loaded correctly.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest test`
  - 2 tests passed.
  - Covers `/v1/me` and `/v1/teams`, including response envelope, current team metadata, and exclusion of workspace internal path and token-like fields.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 63 Java tests passed.
  - Full suite includes existing MockMvc, in-memory repository, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J13A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-current-team-discovery-baseline.md docs/superpowers/plans/2026-06-14-java-workspace-member-removal-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J13A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J13A archive updates.

Evidence boundaries:

- J13A tests use local MockMvc and configured dev principal metadata.
- J13A did not execute a real external provider call.
- J13A does not implement user directory, team CRUD, invitations, cross-team discovery, full OIDC/OAuth, audit export/retention/signed records, credential DB/secret manager, WebSocket / production multi-node streaming, or production remote runner authorization. J14A later adds backend-known team member listing. J15A later adds workspace owner transfer. J16A later adds audit pagination/filtering. J17A later adds bounded run event SSE. J18A later adds audit export baseline.

## Phase J14A - Java Team Member Listing Baseline

Status: implemented for J14A. The Java backend now exposes backend-known team member listing for the current principal's team. Team membership is tracked separately from workspace membership, so removing a user from a workspace does not delete the user's team membership. Full user/team directory CRUD, invitations, cross-team discovery, production OIDC/OAuth team-role sync, credential DB/secret manager, WebSocket / production multi-node streaming, and production remote runner platform remain future work. Owner transfer is delivered later by J15A. Bounded run event SSE is delivered later by J17A.

Scope:

- Added `GET /v1/teams/{teamId}/members` to `IdentityController`.
  - Returns `java-backend-api.v1` envelope.
  - Current principal can only query their own team.
  - Cross-team query returns 403 `TEAM_FORBIDDEN`.
  - Response fields are limited to `teamId`, `userId`, and `role`.
  - Response does not expose `workspaceRoot`, `serverStorageRef`, provider payload, or token material.
- Added identity team membership domain:
  - `TeamMemberRecord`;
  - `TeamRole` with `TEAM_ADMIN` and `TEAM_MEMBER`;
  - `TeamDirectoryService`;
  - `TeamForbiddenException`.
- Added `WorkspaceRepository.listKnownTeamMembers(teamId)`.
  - In-memory repository now stores team memberships independently from workspace memberships.
  - JDBC repository reads existing `team_memberships`.
  - Workspace creation records the owner as `TEAM_ADMIN`.
  - Workspace grant records the target user as `TEAM_MEMBER` without demoting existing admins.
  - Workspace member removal does not delete team membership.
- No schema migration was needed; existing `team_memberships` already supports the baseline.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=TeamMemberControllerTest test`
  - Failed first with 404 `NOT_FOUND` for `GET /v1/teams/{teamId}/members`, after workspace creation and member grant fixtures succeeded.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test`
  - Failed first at Java test compile because `TeamMemberRecord`, `TeamRole`, and `WorkspaceRepository.listKnownTeamMembers(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=TeamMemberControllerTest test`
  - 1 test passed.
  - Covers current-team listing, owner as `TEAM_ADMIN`, granted viewer as `TEAM_MEMBER`, response secrecy for internal path/token-like fields, cross-team `TEAM_FORBIDDEN`, and team membership persistence after workspace member removal.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - 2 tests passed.
  - Covers in-memory and Testcontainers MySQL team membership recording, role mapping, listing, and persistence after workspace access revocation.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 64 Java tests passed.
  - Full suite includes existing MockMvc, in-memory repository, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J14A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-team-member-listing-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J14A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J14A archive updates.

Evidence boundaries:

- J14A tests use local MockMvc, in-memory repositories, and Testcontainers MySQL.
- J14A did not execute a real external provider call.
- J14A does not implement full user/team directory CRUD, user profiles, invitations, cross-team discovery, production OIDC/OAuth team-role sync, audit export/retention/signed records, credential DB/secret manager, WebSocket / production multi-node streaming, or production remote runner authorization. J15A later adds workspace owner transfer. J16A later adds audit pagination/filtering. J17A later adds bounded run event SSE. J18A later adds audit export baseline.

## Phase J15A - Java Workspace Owner Transfer Baseline

Status: implemented for J15A. The Java backend now exposes an owner-only workspace ownership transfer baseline. Ownership transfer is a separate API from member grants: public member grants still reject `WORKSPACE_OWNER`, while `POST /v1/workspaces/{workspaceId}/owner-transfer` promotes an existing workspace member and demotes the previous owner.

Scope:

- Added `POST /v1/workspaces/{workspaceId}/owner-transfer` to `WorkspaceController`.
  - Request body: `newOwnerUserId`.
  - Requires the current principal to be `WORKSPACE_OWNER`.
  - Target must already be a member of the same workspace/team.
  - Self-transfer, missing target, and already-owner target return 400 `VALIDATION_ERROR`.
  - Viewer calls return 403 `WORKSPACE_FORBIDDEN`.
  - Successful response reuses `WorkspaceMemberResponse`: `workspaceId`, `userId`, `teamId`, `role`.
  - Response does not expose `workspaceRoot`, `serverStorageRef`, provider payload, Authorization header, or token material.
- Added `WorkspaceService.transferOwnership(...)`.
  - Promotes target member to `WORKSPACE_OWNER`.
  - Demotes previous owner to `WORKSPACE_EDITOR`.
  - Appends `WORKSPACE_OWNER_TRANSFERRED` audit metadata.
- Added `WorkspaceRepository.transferOwnership(...)`.
  - In-memory repository performs a guarded role swap.
  - JDBC repository performs a single `CASE UPDATE` over the two workspace member rows.
  - Existing team membership records are not changed; workspace ownership does not imply `TEAM_ADMIN`.
- No schema migration was needed; `workspace_members.role` already represents the workspace-level owner role.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceOwnerTransferControllerTest test`
  - Failed first with 404 `NOT_FOUND` for `POST /v1/workspaces/{workspaceId}/owner-transfer`, after workspace creation and member grant fixtures succeeded.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test`
  - Failed first at Java test compile because `WorkspaceRepository.transferOwnership(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceOwnerTransferControllerTest test`
  - 2 tests passed.
  - Covers successful owner transfer, old owner demotion, new owner mutation permission, old owner owner-only denial, response secrecy, viewer denial, self-transfer validation, missing-target validation, and audit metadata.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - 2 tests passed.
  - Covers in-memory and Testcontainers MySQL role swap semantics, old owner continued visibility as editor, new owner promotion, and unchanged visibility for both members.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceOwnerTransferControllerTest,WorkspaceMemberControllerTest,TeamMemberControllerTest,AuditControllerTest,InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - 7 Java tests passed.
  - Covers owner transfer and adjacent workspace member, team member, audit listing, in-memory repository, and Testcontainers MySQL repository boundaries.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 66 Java tests passed.
  - Full suite includes MockMvc, in-memory repository, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-owner-transfer-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.

Evidence boundaries:

- J15A tests use local MockMvc, in-memory repository coverage, and Testcontainers MySQL coverage.
- J15A did not execute a real external provider call.
- J15A does not implement invitations, full user/team directory CRUD, team admin transfer, full OIDC/OAuth team-role sync, audit export/retention/signed records, credential DB/secret manager, WebSocket / production multi-node streaming, or production remote runner authorization. J16A later adds audit pagination/filtering. J17A later adds bounded run event SSE. J18A later adds audit export baseline.

## Phase J16A - Java Audit Pagination Filtering Baseline

Status: implemented for J16A. The Java backend now supports owner-only workspace audit event pagination and filtering while preserving the existing `java-backend-api.v1` envelope and `data` array response shape.

Scope:

- Extended `GET /v1/workspaces/{workspaceId}/audit-events` query params:
  - `limit`: 1..100, default 100;
  - `offset`: default 0, must be non-negative;
  - `eventType`: optional exact audit event type filter;
  - `runId`: optional exact run id filter.
- Added `AuditEventQuery`.
  - Normalizes blank optional filters to `null`.
  - Rejects invalid `limit`/`offset` through existing 400 `VALIDATION_ERROR` handling.
- Extended `AuditQueryService`.
  - Keeps the existing `WORKSPACE_OWNER` guard before returning audit data.
- Extended `AuditRepository`.
  - Existing no-query `findByWorkspaceId(workspaceId)` remains as a default query.
  - New query overload supports filtering and bounded pagination.
- Added in-memory/JDBC repository query implementations.
  - Both sort by `createdAt ASC, auditEventId/id ASC`.
  - Both support combined `workspaceId`, `eventType`, `runId`, `limit`, and `offset`.
- Added shared audit repository contract plus in-memory and Testcontainers MySQL coverage.
- No schema migration was needed; existing `audit_events` columns and indexes support the baseline.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - Failed first because `eventType=ARTIFACT_READ&limit=1` still returned 4 events; query params were ignored by the existing endpoint.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest test`
  - Failed first at Java test compile because `AuditEventQuery` and `AuditRepository.findByWorkspaceId(workspaceId, query)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - 2 tests passed.
  - Covers no-query existing list behavior, owner-only guard, missing workspace handling, `eventType`, `runId`, `limit`, `offset`, invalid limit validation, and response secrecy.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest,JdbcAuditRepositoryTest test`
  - 2 tests passed.
  - Covers in-memory and Testcontainers MySQL query semantics for offset pagination, event type filtering, run id filtering, workspace scoping, stable ordering, and payload secrecy.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest,InMemoryAuditRepositoryContractTest,JdbcAuditRepositoryTest test`
  - 4 Java tests passed.
  - Covers HTTP query behavior plus in-memory and Testcontainers MySQL audit query repository contracts.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 68 Java tests passed.
  - Full suite includes MockMvc, in-memory repository, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-pagination-filtering-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.

Evidence boundaries:

- J16A tests use local MockMvc, in-memory repository coverage, and Testcontainers MySQL coverage.
- J16A did not execute a real external provider call.
- J16A does not implement audit export, retention policy, signed audit records, public audit write API, full OIDC/OAuth, user/team directory CRUD, credential DB/secret manager, WebSocket / production multi-node streaming, or production remote runner authorization. J17A later adds bounded run event SSE. J18A later adds audit export baseline.

## Phase J17A - Java Run Event SSE Streaming Baseline

Status: implemented for J17A. The Java backend now exposes a bounded server-sent events stream for backend-owned durable run lifecycle events. The stream is intentionally thin: it reuses the existing run/workspace authorization guard at request-open time, emits the same public `RunEventResponse` fields already used by the JSON run-event list API, and does not parse worker/runtime-private result shapes.

Scope:

- Added `GET /v1/agent-runs/{runId}/events/stream`.
  - Produces `text/event-stream`.
  - Emits SSE `id` from backend event id.
  - Emits SSE `event` from backend event type.
  - Emits SSE `data` as the public `RunEventResponse`.
  - Deduplicates by backend event id while polling the durable event repository.
  - Completes after a non-queued/non-running lifecycle status is observed.
  - Also closes when the bounded stream window expires; that EOF means the client should reconnect or poll and must not be treated as run terminal evidence.
- Added an authorized stream handle in `RunEventService`.
  - Authorization happens once before the stream starts through the existing `agentRunService.getRun(runId)` path.
  - Background stream emission reads only events for that authorized run id and does not depend on a background-thread `SecurityContext`.
- Added pre-stream error handling for SSE content negotiation.
  - Unauthorized workspace access returns HTTP 403 without starting async streaming.
  - Missing run/workspace returns HTTP 404 without starting async streaming.
  - Existing JSON `GET /v1/agent-runs/{runId}/events` behavior and envelope are unchanged.
- J17A does not introduce a DB migration, new dependency, WebSocket endpoint, broker fanout, multi-node replay cursor, remote runner live event channel, or production heartbeat/backpressure protocol.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test`
  - Failed first with 404 for `GET /v1/agent-runs/{runId}/events/stream`, proving the new SSE endpoint was absent.
- A follow-up negative-path test with `Accept: text/event-stream` exposed a content negotiation bug before final green:
  - workspace-forbidden authorization failed before stream creation;
  - the method-level `produces=text/event-stream` prevented the global JSON exception handler from writing a JSON envelope;
  - MockMvc surfaced the original `WorkspaceForbiddenException` instead of returning 403.
  - The fix maps pre-stream 403/404 statuses inside the SSE controller method and leaves the JSON list endpoint unchanged.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test`
  - 6 Java tests passed.
  - Covers JSON event listing, JSON error envelopes for forbidden/missing run cases, stale recovery no-late-completed event behavior, SSE stream success, SSE event names/run id, absence of internal path/provider/runtime/token-like fields, unauthorized SSE request returning 403 without async streaming, and missing-run SSE request returning 404 without async streaming.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 72 Java tests passed.
  - Full suite includes MockMvc, in-memory repositories, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-remote-runner-signature-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.

Evidence boundaries:

- J17A SSE controller behavior is covered with local MockMvc and a fake/injected worker in the default in-memory profile. The full Java suite also covers adjacent local TS bridge, remote HTTP worker spike, repository, and Testcontainers MySQL boundaries, but it is not a JDBC-profile SSE controller E2E.
- J17A did not execute a real external provider call.
- J17A proves a single-process bounded SSE baseline over durable run events. Stream EOF without a terminal lifecycle event is only bounded-window closure, not run terminal evidence. J17A does not prove WebSocket behavior, production multi-node streaming, broker-backed fanout, replay cursors, remote runner live streaming, production heartbeat/backpressure semantics, or long-lived connection operations under load.

## Phase J18A - Java Audit Export Baseline

Status: implemented for J18A. The Java backend now exposes a narrow owner-only NDJSON export for existing workspace audit metadata. The endpoint is intentionally thin: it reuses the same audit query service and public response fields as the JSON audit list API, and it does not introduce retention, signing, async export jobs, object storage, or raw payload export.

Scope:

- Added `GET /v1/workspaces/{workspaceId}/audit-events/export`.
  - Produces `application/x-ndjson`.
  - Requires the current principal to be `WORKSPACE_OWNER`.
  - Reuses J16A query params: `limit`, `offset`, `eventType`, and `runId`.
  - Serializes one public `AuditEventResponse` per line: `auditEventId`, `actorUserId`, `teamId`, `workspaceId`, `runId`, `eventType`, `message`, and `createdAt`.
  - Adds `Content-Disposition: attachment; filename="<workspaceId>-audit-events.ndjson"`.
- Added export-specific pre-response error mapping in `AuditController`.
  - Invalid query returns 400 `VALIDATION_ERROR`.
  - Viewer access returns 403 `WORKSPACE_FORBIDDEN`.
  - Missing workspace returns 404 `WORKSPACE_NOT_FOUND`.
  - These errors still return the existing `java-backend-api.v1` JSON envelope even when the client requests NDJSON.
- The export response does not contain `workspaceRoot`, `serverStorageRef`, provider payload, Authorization header, raw provider data, token-like fields, or runtime-private source fields.
- No schema migration, production dependency, public audit write API, retention policy, signed audit record, async export job, object storage export, or real provider call was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - Failed first with 404 for `GET /v1/workspaces/{workspaceId}/audit-events/export`, proving the new export endpoint was absent.
- First GREEN attempt exposed an NDJSON content negotiation edge case:
  - viewer request used `Accept: application/x-ndjson`;
  - the service guard raised `WorkspaceForbiddenException` before response serialization;
  - controller-level JSON error mapping was added for pre-export validation/authorization/not-found failures.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - 3 Java tests passed.
  - Covers existing audit list behavior, audit pagination/filtering, owner-only NDJSON export, viewer denial under NDJSON accept headers, invalid query validation, and export response secrecy.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 73 Java tests passed.
  - Full suite includes MockMvc, in-memory repositories, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-export-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.

Evidence boundaries:

- J18A tests use local MockMvc and the default in-memory profile for the export HTTP behavior. The full Java suite also covers adjacent repository and Testcontainers MySQL boundaries, but J18A did not add a separate JDBC-profile export controller E2E.
- J18A did not execute a real external provider call.
- J18A proves a bounded metadata export of existing audit events. It does not prove retention enforcement, tamper-evident signatures, async export job lifecycle, object storage delivery, public audit ingestion, long-running export operations, WebSocket/fanout behavior, or production OIDC/OAuth identity integration.

## Phase J19A - Java Audit Record Digest Baseline

Status: implemented for J19A. The Java backend now includes a stable `recordDigest` in audit list and audit export public responses. This is a public metadata digest baseline for line-level identity and future signed-record work; it is not a persisted signature, hash chain, or retention mechanism.

Scope:

- Extended `AuditEventResponse` with `recordDigest`.
  - Format: `sha256:<64 lowercase hex chars>`.
  - Included by `GET /v1/workspaces/{workspaceId}/audit-events`.
  - Included by `GET /v1/workspaces/{workspaceId}/audit-events/export` NDJSON lines.
- Digest input is limited to existing public audit metadata:
  - `auditEventId`;
  - `actorUserId`;
  - `teamId`;
  - `workspaceId`;
  - `runId`;
  - `eventType`;
  - `message`;
  - `createdAt`.
- Digest implementation uses only JDK `MessageDigest` and `HexFormat`.
- No schema migration, dependency, raw audit payload export, provider payload export, workspace internal path exposure, token exposure, signed audit record, key management, public audit write API, or retention enforcement was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - Failed first because `$.data[0].recordDigest` was absent from JSON audit list responses.
  - Failed first because the NDJSON export line did not contain `"recordDigest":"sha256:`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - 3 Java tests passed.
  - Covers existing audit list behavior, pagination/filtering, owner-only NDJSON export, `recordDigest` presence/format, viewer denial, invalid query validation, and response secrecy.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 73 Java tests passed.
  - Full suite includes MockMvc, in-memory repositories, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-record-digest-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.

Evidence boundaries:

- J19A tests use local MockMvc and the default in-memory profile for HTTP audit list/export behavior. The full Java suite also covers adjacent repository and Testcontainers MySQL boundaries, but J19A did not add a separate JDBC-profile digest controller E2E.
- J19A did not execute a real external provider call.
- J19A proves a stable public metadata digest is emitted by audit list/export. It does not prove persisted signed audit records, tamper-evident hash chains, key rotation, external KMS/secret manager integration, retention policy enforcement, public audit ingestion, WebSocket/fanout behavior, or production OIDC/OAuth identity integration.

## Phase J20A - Java Remote Runner Signature Baseline

Status: implemented for J20A. The Java remote-http worker now supports optional HMAC verification for `agent-remote-runner-result.v1` envelopes. When `my-workflow.backend.agent-worker.remote-http.signature-secret` is configured, Java requires `signatureKind=hmac-sha256` and validates `signature=hmac-sha256:<base64>` over the remote envelope identity plus nested public `agent-backend-response.v1` fields before returning the nested backend response to the run state machine. A review follow-up replaced delimiter-joined canonicalization with fixed field name + UTF-8 length-prefixed canonical payloads.

Scope delivered:

- Added optional `remote-http.signature-secret` injection to `RemoteHttpAgentWorker`.
- Kept the existing no-secret local spike path accepting `signatureKind=unsigned-local-spike`.
- Added signed envelope validation for `signatureKind=hmac-sha256`.
- Signature payload uses fixed field names, UTF-8 byte length prefixes, list lengths, and length-prefixed list items.
- Signature payload is limited to:
  - remote envelope `schemaVersion`;
  - remote envelope `workerKind`;
  - remote envelope `signatureKind`;
  - nested backend response `schemaVersion`;
  - nested backend response `runId`;
  - nested backend response `status`;
  - nested backend response `outputKind`;
  - nested backend response `displayText`;
  - nested backend response approval/confirmation flags;
  - nested backend response `artifactRefs`;
  - nested backend response `wroteWorkspace`;
  - nested backend response `targetWorkspacePaths`.
- Signature comparison uses JDK `MessageDigest.isEqual`.
- No public API response schema, DB schema, production dependency, provider secret store, runner registry, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, runner authorization, or runner-scoped secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test`
  - Failed first at `testCompile`.
  - Expected failure: the new signed-envelope test required a `(ObjectMapper, endpointUrl, timeoutMs, signatureSecret)` construction path and HMAC validation that did not exist yet.
  - Error included `java.lang.String无法转换为java.net.http.HttpClient`, confirming the new test was hitting missing constructor support instead of a fixture parse issue.
- Review follow-up RED:
  - Same command failed with 5 tests / 1 failure.
  - Expected failure: `rejectsDelimiterCollisionTamperingInSignedEnvelope` expected `invalid remote runner envelope signature`, but the delimiter-joined canonicalization accepted a semantically different tampered result and no exception was thrown.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test`
  - 5 Java tests passed.
  - Covers existing unsigned local spike behavior, unsupported remote envelope rejection, signed remote envelope acceptance, direct field tamper rejection, delimiter-collision tamper rejection, configured-secret rejection for unsigned/missing/malformed signatures, and no secret leakage in the worker request body.

Adjacent verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test`
  - 6 Java tests passed.
  - Confirms the added Spring property does not break `remote-http` worker wiring or existing Java API mapping from nested `agent-backend-response.v1`.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 76 Java tests passed.
  - Full suite includes MockMvc, in-memory repositories, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/run/RunEventController.java backend/src/test/java/com/myworkflow/agent/backend/run/RunEventControllerTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-run-event-sse-replay-cursor-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-run-event-sse-replay-cursor-baseline.md`
  - no unchecked plan tasks.

Evidence boundaries:

- J20A tests use local JDK `HttpServer`; this is local HTTP fixture evidence, not a real remote runner service.
- J20A did not execute a real external provider call.
- J20A proves optional result-envelope HMAC validation in the Java remote-http worker. It does not prove runner registration, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, remote runner authorization, runner-scoped secret distribution, key rotation, KMS/secret manager integration, mTLS, WebSocket/fanout behavior, or production remote runner operations.
- The test secret is a local fixture string and is not a real credential.

## Phase J21A - Java Remote Runner Production Secret Guard

Status: implemented for J21A. The Java remote-http worker now rejects production-profile construction when `my-workflow.backend.agent-worker.remote-http.signature-secret` is blank. Production profiles are currently recognized as active Spring profiles named `prod` or `production`. Default/local profiles continue to allow the J20A `unsigned-local-spike` path for local fixture evidence.

Scope delivered:

- Added Spring `Environment` injection to `RemoteHttpAgentWorker` so the worker can inspect active profiles at construction time.
- Added a production-only guard that requires non-blank `remote-http.signature-secret` when active profiles include `prod` or `production`.
- Preserved default/local profile behavior, including no-secret `unsigned-local-spike` local HTTP fixture tests.
- No public API response schema, DB schema, production dependency, runner registry, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, runner authorization, runner-scoped secret distribution, key rotation, KMS/secret manager, or mTLS was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test`
  - Failed first at `testCompile`.
  - Expected failure: the new production-profile guard test required a `(ObjectMapper, endpointUrl, timeoutMs, signatureSecret, activeProfiles)` construction path that did not exist yet.
  - Error included `java.lang.String无法转换为java.net.http.HttpClient`, confirming the new test was hitting missing profile-aware constructor support instead of a fixture parse issue.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test`
  - 6 Java tests passed.
  - Covers default-profile unsigned local spike behavior, unsupported envelope rejection, signed envelope acceptance, tamper rejection, delimiter-collision tamper rejection, configured-secret negative cases, and production-profile blank secret rejection.

Adjacent verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test`
  - 7 Java tests passed.
  - Confirms adding `Environment` injection does not break default-profile Spring `remote-http` worker wiring.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 77 Java tests passed.
  - Full suite includes MockMvc, in-memory repositories, local TS worker bridge, remote HTTP worker spike, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J23A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicy.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicyTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-runtime-raw-secret-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J23A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J23A archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-runtime-raw-secret-guard.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J21A tests use constructor-level profile inputs plus existing local JDK `HttpServer` fixtures; this is local fixture evidence, not a real remote runner service.
- J21A did not execute a real external provider call.
- J21A does not add a separate Spring production-profile context failure test in order to keep this phase within the five-file scope limit. The guard is covered at the constructor boundary and default Spring wiring is covered by the adjacent controller test.
- J21A is a misconfiguration guard only. It does not prove runner identity, registration, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, runner authorization, runner-scoped secret distribution, key rotation, KMS/secret manager integration, mTLS, WebSocket/fanout behavior, or production remote runner operations.

## Phase J22A - Java Run Event SSE Replay Cursor Baseline

Status: implemented for J22A. The Java run event SSE endpoint now honors an optional `Last-Event-ID` reconnect cursor over backend-owned durable run lifecycle events. When the cursor matches an event in the current run event list, the stream skips that event and earlier events; when the cursor is unknown, the stream falls back to full replay rather than dropping events.

Scope delivered:

- Added optional `Last-Event-ID` header support to `GET /v1/agent-runs/{runId}/events/stream`.
- Added durable JDBC append ordering for run events via `run_events.event_sequence`.
- Added `V8__run_event_sequence_baseline.sql` instead of mutating the existing V6 Flyway migration, preserving upgrade compatibility for schemas already at V7.
- Preserved request-open run/workspace authorization before creating the async SSE stream.
- Preserved public SSE payload shape: event id, event type, and `RunEventResponse` data only.
- Preserved terminal-event completion and bounded-window EOF semantics.
- Added reconnect tests proving known cursor skip, unknown cursor full replay, and completed-run middle cursor behavior.
- Added MySQL/Testcontainers regression tests proving same-timestamp events preserve append order rather than random id ordering and that an existing V7 schema upgrades to V8.
- No production dependency, WebSocket endpoint, broker fanout, multi-node stream routing, remote runner live channel, heartbeat/backpressure protocol, audit stream, or runtime-private payload parsing was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test`
  - Failed first with 7 tests / 1 failure.
  - Expected failure: `streamsOnlyEventsAfterLastEventIdWhenClientReconnects` opened the SSE stream with `Last-Event-ID` set to the already consumed `RUN_QUEUED` event id, but the stream still returned `event:RUN_QUEUED`.
- Reviewer follow-up RED:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcRunEventRepositoryTest test`
  - Failed with 2 tests / 1 failure.
  - Expected failure: `preservesAppendOrderWhenEventsShareTimestampAgainstMySql` inserted `evt_z_first` then `evt_a_second` at the same timestamp, but current JDBC ordering returned `evt_a_second` first because `id` was the tie-breaker.

Debugging note:

- The first GREEN attempt still re-emitted the consumed event on the second poll cycle.
- Root cause: skipped cursor-prior events were not recorded in `emittedEventIds`, so later poll cycles treated them as unsent.
- Fix: mark skipped events as processed and only start cursor skipping when the provided cursor is present in the current durable event list.
- Reviewer found a second root cause: JDBC event ordering used `created_at, id`, and `id` is random. Same-timestamp events could be returned in a different order from append order, which could make `Last-Event-ID` skip the wrong range.
- Fix: restore V6, add `V8__run_event_sequence_baseline.sql`, and order JDBC `findByRunId` by the durable append sequence.
- A second reviewer pass caught that mutating the existing V6 migration would break Flyway checksum compatibility for databases already at V6/V7; the final implementation preserves V6 and uses V8 as an additive migration.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcRunEventRepositoryTest,RunEventControllerTest test`
  - 12 Java tests passed.
  - Covers V7-to-V8 migration upgrade path, JDBC durable append ordering for same-timestamp events, JSON event listing, stale-lock event behavior, SSE streaming, SSE known cursor skip, unknown cursor full replay, completed-run middle cursor behavior, stream authorization failure, missing-run failure, and JSON error envelopes for list failures.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 82 Java tests passed.
  - Full suite includes MockMvc, in-memory repositories, local TS worker bridge, remote HTTP worker fixture, Flyway migration to v8, V7-to-V8 upgrade coverage, and Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/run/RunEventController.java backend/src/main/java/com/myworkflow/agent/backend/run/JdbcRunEventRepository.java backend/src/main/resources/db/migration/V6__run_event_baseline.sql backend/src/main/resources/db/migration/V8__run_event_sequence_baseline.sql backend/src/test/java/com/myworkflow/agent/backend/run/RunEventControllerTest.java backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRunEventRepositoryTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-run-event-sse-replay-cursor-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-run-event-sse-replay-cursor-baseline.md`
  - no unchecked plan tasks.

Evidence boundaries:

- J22A tests use MockMvc plus the existing in-memory event repository for SSE HTTP behavior and a JDBC repository-level MySQL/Testcontainers regression for durable append ordering. J22A did not add a separate JDBC-profile SSE controller E2E.
- J22A did not execute a real external provider call.
- J22A proves reconnect cursor behavior over backend-owned durable lifecycle events. It does not prove WebSocket, broker fanout, multi-node stream routing, remote runner live streaming, production heartbeat/backpressure, or cross-node delivery guarantees.

## Phase J23A - Java Provider Runtime Raw Secret Config Guard

Status: implemented for J23A. The Java provider runtime reference policy now fails closed when a configured provider ref contains raw secret config keys. Safe metadata such as `provider`, `model`, `base-url`, `timeout-ms`, and `api-key-env-name` remains allowed, but non-empty `api-key`, `token`, or `authorization` config values are rejected before worker request construction.

Scope delivered:

- Added raw secret config key rejection to `ProviderRuntimePolicy`.
- Preserved J9A `providerRuntimeRef` behavior for safe configured refs and built-in `fake`.
- Preserved worker request behavior that may include `apiKeyEnvName`, but not raw `apiKey`, `token`, or Authorization values.
- Updated the Java backend platform spec and J23A plan archive.
- No public API response schema, DB schema, production dependency, credential table, secret manager, KMS integration, real provider call, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - Failed first with 5 tests / 1 failure.
  - Expected failure: `rejectsConfiguredProviderReferencesWithRawSecretValues` expected an exception, but current code did not reject raw secret config keys.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - 5 Java tests passed.
  - Covers blank default behavior, built-in fake, safe configured real provider metadata, raw secret config rejection, unknown ref rejection, and invalid ref rejection.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 7 Java tests passed.
  - Confirms the new guard does not break existing HTTP-level providerRuntimeRef propagation or request redaction tests.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 83 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/resources/db/migration/V9__provider_credential_metadata_baseline.sql backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialSchemaTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-metadata-schema.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24A archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-metadata-schema.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J23A tests use unit and MockMvc coverage; no real external provider call was executed.
- J23A is a backend config guard only. It does not implement credential DB, secret manager, KMS, key rotation, encrypted storage, public credential APIs, or remote runner secret distribution.
- Existing configured refs remain local/backend config metadata, not team-scoped credential records.

## Phase J23B - Java Provider Runtime Env Name Guard

Status: implemented for J23B. The Java provider runtime reference policy now requires `api-key-env-name` metadata to be an env-var-style identifier before it can be forwarded to the worker as `apiKeyEnvName`. This prevents a configured provider ref from smuggling an arbitrary secret-looking value through the field that is meant to name an environment variable.

Scope delivered:

- Added `api-key-env-name` format validation to `ProviderRuntimePolicy`.
- Preserved safe configured refs that use `MIMO_API_KEY`-style metadata.
- Preserved J23A raw secret config key rejection for `api-key`, `token`, and `authorization`.
- Preserved existing public `providerRuntimeRef` behavior and HTTP-level provider secret policy tests.
- No public API response schema, DB schema, production dependency, credential table, secret manager, KMS integration, real provider call, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - Failed first with 6 tests / 1 failure.
  - Expected failure: `rejectsConfiguredProviderReferencesWithInvalidApiKeyEnvName` expected an exception, but current code accepted `api-key-env-name=fixture-secret-value`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - 6 Java tests passed.
  - Covers blank default behavior, built-in fake, safe configured real provider metadata, raw secret config rejection, invalid `api-key-env-name` rejection, unknown ref rejection, and invalid ref rejection.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 8 Java tests passed.
  - Confirms the new env-name guard does not break existing HTTP-level providerRuntimeRef propagation or request redaction tests.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 84 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J23B archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicy.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicyTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-runtime-env-name-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J23B archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J23B archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-runtime-env-name-guard.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J23B tests use unit and MockMvc coverage; no real external provider call was executed.
- J23B is a backend config metadata guard only. It does not implement credential DB, secret manager, KMS, key rotation, encrypted storage, public credential APIs, or remote runner secret distribution.
- Existing configured refs remain local/backend config metadata, not team-scoped credential records.

## Phase J24A - Java Provider Credential Metadata Schema Baseline

Status: implemented for J24A. The Java backend now has a Flyway-managed `provider_credentials` metadata table for team/workspace-scoped provider credential references. The table stores metadata and `api_key_secret_ref`; it does not store raw provider token values.

Scope delivered:

- Added `V9__provider_credential_metadata_baseline.sql`.
- Added `provider_credentials` with:
  - `id`;
  - `credential_ref`;
  - `team_id`;
  - nullable `workspace_id`;
  - `provider`;
  - nullable `model`;
  - nullable `base_url`;
  - `api_key_secret_ref`;
  - `status`;
  - `created_at`;
  - `updated_at`.
- Added uniqueness for `(team_id, credential_ref)` and indexes for workspace/provider-status lookup.
- Added foreign keys to `teams` and `workspaces`.
- Added MySQL/Testcontainers schema coverage that proves the table exists after full Flyway migration and does not include obvious raw secret columns.
- No public credential API, repository/service behavior, secret manager, KMS integration, real provider call, DB-backed runtime ref resolution, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest test`
  - Failed first with 1 test / 1 failure.
  - Expected failure: Flyway migrated only to v8 and the table list did not contain `provider_credentials`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest test`
  - 1 Java test passed.
  - Flyway migrated to v9 and `provider_credentials` existed without raw secret columns such as `api_key`, `token`, `authorization`, `secret_value`, or `raw_secret`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 9 Java tests passed.
  - Confirms the new migration does not break existing provider runtime policy and HTTP-level providerRuntimeRef tests.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 85 Java tests passed.
  - Full suite includes Flyway migration to v9 across the existing Testcontainers MySQL coverage.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.

Evidence boundaries:

- J24A tests use MySQL/Testcontainers schema coverage; no real external provider call was executed.
- J24A is a provider credential metadata schema baseline only. It does not implement public credential CRUD, credential repository/service behavior, DB-backed `ProviderRuntimePolicy` resolution, secret manager, KMS, key rotation, encrypted storage, or remote runner secret distribution.
- `api_key_secret_ref` is a reference name for future secret lookup, not a raw secret value.

## Phase J24B - Java Provider Credential Repository Baseline

Status: implemented for J24B. The Java backend now has a narrow JDBC repository for provider credential metadata. It can save metadata rows and resolve only ACTIVE credential refs by team/workspace scope without reading or exposing raw provider token values.

Scope delivered:

- Added `ProviderCredentialRepository` under `providersecret`.
- Added nested `ProviderCredentialMetadata` with `apiKeySecretRef` reference metadata and no raw secret components.
- Added MySQL/Testcontainers repository coverage for:
  - team-scoped credential metadata with `workspace_id IS NULL`;
  - workspace-scoped credential metadata that only resolves for the matching workspace;
  - disabled credential metadata that does not resolve;
  - cross-team and cross-workspace lookups that do not resolve;
  - record component scan that rejects raw `apiKey`, `token`, `authorization`, `secretValue`, and `rawSecret` fields.
- Updated Java backend platform spec and J24B plan archive.
- No public credential API, secret manager, KMS integration, real provider call, DB-backed runtime policy switch, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - Failed first in `testCompile`.
  - Expected failure: `ProviderCredentialRepository` / `ProviderCredentialMetadata` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - 1 Java test passed.
  - Covers metadata save, scoped ACTIVE lookup, disabled/cross-scope rejection, and raw secret component absence.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 10 Java tests passed.
  - Confirms the repository baseline does not break J24A schema, J23 config policy, or HTTP-level provider runtime ref tests.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 86 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24B archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24B archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24B archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J24B tests use MySQL/Testcontainers repository coverage; no real external provider call was executed.
- J24B is a provider credential metadata repository baseline only. It does not implement public credential CRUD, DB-backed `ProviderRuntimePolicy` resolution, secret manager, KMS, key rotation, encrypted storage, real provider execution, or remote runner secret distribution.
- `apiKeySecretRef` is a future secret lookup reference, not a raw provider token value.

## Phase J24C - Java Provider Credential Service Scope Guard

Status: implemented for J24C. The Java backend now has an internal `ProviderCredentialService` for DB-backed credential metadata resolution through workspace/team authorization. The service validates credential refs, requires `WORKSPACE_EDITOR` access to the workspace, and resolves metadata using the workspace record's authoritative `teamId` and `workspaceId`.

Scope delivered:

- Added `ProviderCredentialService` under `providersecret`.
- Added `resolveForWorkspace(workspaceId, credentialRef)`.
- Added focused service tests for:
  - editor credential metadata resolution;
  - team-scoped and workspace-scoped credential refs;
  - viewer and cross-team rejection through workspace guard;
  - invalid credential ref rejection before repository lookup;
  - repository lookup receiving workspace-owned `teamId` and `workspaceId`.
- Scoped `ProviderCredentialService` to the `jdbc` profile, matching `ProviderCredentialRepository`.
- Updated Java backend platform spec and J24C plan archive.
- No public credential API, secret manager, KMS integration, real provider call, DB-backed runtime policy switch, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Failed first in `testCompile`.
  - Expected failure: `ProviderCredentialService` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - 3 Java tests passed.
  - Covers editor resolution, viewer/cross-team rejection, and invalid ref rejection before repository lookup.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - Failed once before the profile fix.
  - Root cause: default profile Spring context loaded `ProviderCredentialService`, but `ProviderCredentialRepository` is only a `jdbc` profile bean.
  - Fixed by adding `@Profile("jdbc")` to `ProviderCredentialService`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 13 Java tests passed after the profile fix.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 89 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24C archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24C archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24C archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J24C tests use in-memory workspace repository coverage for service authorization and MySQL/Testcontainers coverage through the adjacent repository/schema suite. No real external provider call was executed.
- J24C is an internal provider credential service scope guard only. It does not implement public credential CRUD, DB-backed `ProviderRuntimePolicy` resolution, secret manager, KMS, key rotation, encrypted storage, real provider execution, or remote runner secret distribution.
- `apiKeySecretRef` remains a future secret lookup reference, not a raw provider token value.

## Phase J24D - Java Provider Credential Runtime Descriptor Baseline

Status: implemented for J24D. The Java backend now has an internal runtime descriptor path on `ProviderCredentialService` that converts already-authorized provider credential metadata into secret-safe runtime metadata. The descriptor preserves provider/model/baseUrl and `apiKeySecretRef` reference metadata, rejects unsupported provider ids, and rejects plain/non-URI secret refs before returning a descriptor.

Scope delivered:

- Added `resolveRuntimeDescriptorForWorkspace(workspaceId, credentialRef)` to `ProviderCredentialService`.
- Added `ProviderCredentialRuntimeDescriptor` in the provider secret service boundary.
- Reused the existing workspace editor authorization and authoritative workspace team/workspace scope from `resolveForWorkspace`.
- Added secret reference shape validation for `env://`, `secret://`, `keychain://`, and `file://` refs.
- Added focused service tests for:
  - editor runtime descriptor resolution;
  - provider/model/baseUrl/apiKeySecretRef preservation;
  - unsupported provider rejection;
  - plain secret ref rejection.
- Updated Java backend platform spec and J24D plan archive.
- No public credential API, secret manager, KMS integration, worker secret injection, agent-run DB-backed credential ref wiring, real provider call, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Failed first in `testCompile`.
  - Expected failure: `ProviderCredentialService.resolveRuntimeDescriptorForWorkspace(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - 5 Java tests passed.
  - Covers metadata resolution, descriptor resolution, unsupported provider rejection, plain secret ref rejection, viewer/cross-team rejection, and invalid ref rejection before repository lookup.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 15 Java tests passed.
  - Confirms J24D does not break the J24A schema, J24B repository, J23 config policy, or HTTP-level provider runtime ref boundary.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 91 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24D archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24D archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24D archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J24D tests use in-memory workspace repository coverage for service authorization and descriptor validation. MySQL/Testcontainers coverage remains in the adjacent provider schema/repository suite. No real external provider call was executed.
- J24D is an internal descriptor baseline only. It does not implement public credential CRUD, DB-backed `ProviderRuntimePolicy` resolution, secret manager, KMS, key rotation, encrypted storage, worker secret injection, real provider execution, or remote runner secret distribution.
- `apiKeySecretRef` remains a secret reference URI, not a raw provider token value.

## Phase J24E - Java Agent Run DB-Backed Credential Ref Wiring

Status: implemented for J24E. The Java run creation path now recognizes existing public `providerRuntimeRef` values in the form `credential.<credentialRef>`, resolves the credential metadata through `ProviderCredentialService`, and maps env-backed credentials into worker-safe provider runtime metadata. The worker receives provider/model/baseUrl, `apiKeyEnvName`, and timeout metadata only; it does not receive `apiKeySecretRef` or raw provider secret values.

Scope delivered:

- Added `ProviderCredentialRunControllerTest` with JDBC profile and MySQL Testcontainers coverage for `POST /v1/workspaces/{workspaceId}/agent-runs`.
- Covered a workspace-scoped credential with `apiKeySecretRef = "env://MIMO_API_KEY"` and `providerRuntimeRef = "credential.workspace-mimo"`.
- Verified the capturing fake worker receives `provider`, `model`, `baseUrl`, `apiKeyEnvName`, and `timeoutMs`.
- Verified worker metadata and API error responses do not include raw secret fields such as `apiKey`, `token`, `authorization`, `Authorization`, or `apiKeySecretRef`.
- Covered rejection for non-env secret refs such as `secret://team-provider-credential-run/provider/mimo` before worker execution.
- Preserved config-backed `providerRuntimeRef` behavior through the adjacent provider runtime policy suite.
- Updated Java backend platform spec and J24E plan archive.
- No public request field, public credential CRUD API, secret manager lookup, raw secret read, real provider call, production dependency, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest test`
  - Failed with 2 tests run and 1 failure.
  - Expected failure: `runRequestUsesDbBackedCredentialRefWithoutPassingSecretMaterial` expected HTTP 200 but received HTTP 400 `VALIDATION_ERROR`; the current code still routed `credential.workspace-mimo` through config-backed provider runtime policy and treated it as an unknown provider runtime reference.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest test`
  - 2 Java tests passed.
  - Covers env-backed DB credential ref wiring and non-env secret ref rejection before worker execution.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 17 Java tests passed.
  - Confirms J24E does not break the J24A schema, J24B repository, J24C/J24D service contract, J23 config policy, or HTTP-level provider runtime ref boundary.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 93 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24E archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java backend/src/test/java/com/myworkflow/agent/backend/run/ProviderCredentialRunControllerTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-agent-run-db-backed-credential-ref-wiring.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24E archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24E archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-agent-run-db-backed-credential-ref-wiring.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J24E tests use a capturing fake worker; no real external provider call was executed.
- `env://MIMO_API_KEY` is metadata for worker-side environment lookup, not a provider token value.
- J24E proves the Java run path can consume DB-backed credential metadata for env-backed credentials. It does not implement public credential CRUD, secret manager/KMS/keychain/file lookup, key rotation, encrypted secret storage, non-env secret injection, real provider execution, or remote runner credential distribution.

## Phase J25A - Java Provider Credential Public Metadata API

Status: implemented for J25A. The Java backend now exposes a narrow JDBC-profile public metadata API for workspace owners to upsert and list workspace-scoped env-backed provider credential metadata. The run path still consumes `providerRuntimeRef = "credential.<credentialRef>"` from J24E; J25A only adds the management surface and keeps public responses secret-free.

Scope delivered:

- Added `ProviderCredentialController` with:
  - `GET /v1/workspaces/{workspaceId}/provider-credentials`;
  - `PUT /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}`.
- Added owner-only service methods to upsert/list workspace-scoped public metadata.
- Added repository `listByWorkspaceScope(teamId, workspaceId)`.
- API request accepts `apiKeyEnvName` and stores only `env://<name>` in the metadata table.
- API request explicitly rejects raw-secret aliases: `apiKey`, `token`, `authorization`, `Authorization`, and `apiKeySecretRef`.
- Unknown raw provider payload fields such as `rawProviderPayload` fail closed as an invalid request instead of being accepted or persisted.
- Public response includes only `credentialRef`, `workspaceId`, `scope`, `provider`, `model`, `baseUrl`, and `status`.
- Audit event `PROVIDER_CREDENTIAL_UPSERTED` records actor/team/workspace/ref/provider metadata without env name, secret ref, token, or Authorization material.
- Controller is `@Profile("jdbc")`, matching the provider credential service/repository baseline.
- Updated Java backend platform spec, phase-one audit, delivery report, and J25A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest test`
  - Initial RED: 4 tests failed as expected.
  - Expected failures: provider credential API endpoints returned HTTP 404 and OpenAPI path assertions failed because the controller/API did not exist yet.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - Initial RED: test compile failed because `ProviderCredentialRepository.listByWorkspaceScope(String, String)` did not exist yet.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Initial RED: test compile failed because the service constructor, public metadata record, `upsertWorkspaceCredential(...)`, and `listWorkspaceCredentials(...)` did not exist yet.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - 1 Java test passed.
  - Covers workspace-scoped metadata listing and raw-secret-field schema guard.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - 8 Java tests passed.
  - Covers owner-only upsert/list, public metadata redaction, audit redaction, viewer denial, invalid ref/env/provider rejection, and existing descriptor behavior.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest test`
  - 4 Java tests passed.
  - Covers owner upsert/list, OpenAPI path presence, viewer denial, raw secret alias rejection, unknown raw provider payload rejection, invalid env name rejection, repository persistence, public response redaction, and audit redaction.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialRunControllerTest test`
  - 15 Java tests passed.
  - Confirms the new public metadata API does not break J24E DB-backed run credential ref execution.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 100 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J25A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret backend/src/test/java/com/myworkflow/agent/backend/providersecret docs/architecture/java-team-backend-platform-spec.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/reports/java-backend-phase-one-completion-audit.md docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md --glob '!backend/target/**'`
  - no matches after J25A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J25A archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J25A tests use MockMvc, in-memory service fakes, JDBC repositories, and MySQL Testcontainers. No real external provider call was executed.
- `apiKeyEnvName` and `env://MIMO_API_KEY` are metadata references for worker-side environment lookup, not provider token values.
- J25A proves workspace owner upsert/list of workspace-scoped env-backed metadata and public redaction. It does not implement team-scoped credential API, secret manager/KMS/keychain/file lookup, delete/disable lifecycle, key rotation, encrypted secret storage, non-env secret injection, real provider validation, real provider execution, or remote runner credential distribution.

## Phase J26A - Java Provider Credential Lifecycle Guard

Status: implemented for J26A. The Java backend now exposes a narrow JDBC-profile owner-only disable lifecycle API for workspace-scoped provider credential metadata. Disable updates metadata status to `DISABLED`, preserves audit history, keeps owner list visibility, and prevents disabled credential refs from being resolved into worker provider runtime metadata.

Scope delivered:

- Added `POST /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable`.
- Added `ProviderCredentialService.disableWorkspaceCredential(...)` with workspace owner guard and credential ref validation.
- Added `ProviderCredentialRepository.disableWorkspaceCredential(teamId, workspaceId, credentialRef)` to update only matching workspace-scoped metadata rows.
- Public disable response reuses `ProviderCredentialPublicMetadata`; it does not expose `apiKeyEnvName`, `apiKeySecretRef`, raw token, or Authorization material.
- Owner list still returns disabled workspace metadata with `status = "DISABLED"`.
- Existing run path continues to resolve only ACTIVE credential metadata, so `providerRuntimeRef = "credential.<disabled-ref>"` is rejected before worker invocation.
- Audit event `PROVIDER_CREDENTIAL_DISABLED` records actor/team/workspace/ref metadata without env name, secret ref, token, or Authorization material.
- Updated Java backend platform spec, phase-one audit, delivery report, and J26A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialRunControllerTest test`
  - Initial RED: test compilation failed as expected.
  - Expected failures: `ProviderCredentialRepository.disableWorkspaceCredential(String, String, String)` and `ProviderCredentialService.disableWorkspaceCredential(String, String)` did not exist yet; the test fake override also failed because the production repository method was absent.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialRunControllerTest test`
  - 17 Java tests passed.
  - Covers owner disable, OpenAPI path presence, viewer denial, public response/list redaction, audit redaction, repository status update, active-resolution blocking, and run rejection before worker invocation.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 102 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.

Static verification:

- `git diff --check`
  - passed after J26A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret backend/src/test/java/com/myworkflow/agent/backend/providersecret backend/src/test/java/com/myworkflow/agent/backend/run/ProviderCredentialRunControllerTest.java docs/architecture/java-team-backend-platform-spec.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/reports/java-backend-phase-one-completion-audit.md docs/superpowers/plans/2026-06-15-java-provider-credential-lifecycle-guard.md --glob '!backend/target/**'`
  - no matches after J26A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J26A archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-15-java-provider-credential-lifecycle-guard.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- J26A tests use MockMvc, in-memory service fakes, JDBC repositories, a capturing fake worker, and MySQL Testcontainers. No real external provider call was executed.
- `apiKeyEnvName` and `env://MIMO_API_KEY` remain metadata references for worker-side environment lookup, not provider token values.
- J26A proves workspace owner disable of workspace-scoped env-backed metadata and disabled-ref run blocking. It does not implement physical delete, team-scoped credential API, secret manager/KMS/keychain/file lookup, key rotation, encrypted secret storage, non-env secret injection, real provider validation, real provider execution, or remote runner credential distribution.

## Phase J27A - Java Audit Retention Policy Baseline

Status: implemented for J27A. The Java backend now exposes owner-visible, report-only audit retention policy metadata. The policy is configuration-backed and explicitly states that destructive purge is disabled; existing audit events remain append-only and are not deleted, compacted, or mutated by this phase.

Scope delivered:

- Added `my-workflow.backend.audit.retention-days` metadata config with default `365`.
- Added `BackendProperties.AuditRetention` with `mode = "REPORT_ONLY"` and `destructivePurgeEnabled = false`.
- Added `AuditRetentionPolicyService` with `WORKSPACE_OWNER` guard.
- Added `GET /v1/workspaces/{workspaceId}/audit-events/retention-policy`.
- Public response includes only `workspaceId`, `retentionDays`, `mode`, `destructivePurgeEnabled`, and `policySource`.
- Existing audit list/export behavior remains unchanged.
- Updated Java backend platform spec, phase-one audit, delivery report, and J27A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - Initial RED: 1 test failed as expected.
  - Expected failure: `GET /v1/workspaces/{workspaceId}/audit-events/retention-policy` returned HTTP 404 because the endpoint did not exist yet.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - 4 Java tests passed.
  - Covers owner retention policy read, viewer denial, public response redaction, configured retention days, report-only mode, destructive purge disabled, and existing audit list/export behavior.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 103 Java tests passed.
- `npm test`
  - 44 TypeScript test files passed, 178 tests passed.
- `npm run typecheck`
  - Passed.

Static verification:

- `git diff --check`
  - Passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" ...`
  - No trailing whitespace or conflict marker matches in touched backend/docs files.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - No token pattern matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-17-java-audit-retention-policy-baseline.md`
  - No unchecked J27A plan tasks remain.

Evidence boundaries:

- J27A tests use MockMvc and in-memory repositories. No real external provider call was executed.
- J27A is policy metadata only. It does not implement destructive purge, retention execution, async export-before-purge, object storage, persisted signed audit records, hash chain, external KMS, or public audit write API.

## Phase J28A - Java Audit Signed Record Integrity Baseline

Status: implemented for J28A. The Java backend now persists audit integrity metadata at repository append time and exposes those persisted fields through the existing owner-only audit list/export APIs. This is a local SHA-256 hash-chain integrity baseline, not an external KMS/private-key signature system.

Scope delivered:

- Added `recordDigest`, `previousRecordDigest`, `chainDigest`, `signatureKind`, and `signatureValue` to `AuditEventRecord`.
- Added `AuditRecordIntegrity` for canonical public metadata digest and workspace-level chain digest generation.
- Updated in-memory audit append path to compute and store integrity metadata.
- Added Flyway migration `V10__audit_event_integrity_baseline.sql` for JDBC audit integrity columns.
- Updated JDBC audit append/query paths to persist and read integrity metadata.
- Updated audit list/export response to return persisted integrity fields instead of recalculating digest in the controller.
- Updated Java backend platform spec, phase-one audit, delivery report, and J28A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest,AuditControllerTest test`
  - Initial RED: test compilation failed as expected.
  - Expected failure: `AuditEventRecord` did not yet expose `recordDigest()`, `previousRecordDigest()`, `chainDigest()`, `signatureKind()`, or `signatureValue()`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest,AuditControllerTest test`
  - 5 Java tests passed.
  - Covers in-memory repository integrity chain contract and owner-only audit list/export public response fields.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcAuditRepositoryTest test`
  - 1 Java test passed.
  - Covers Flyway v10 migration, JDBC insert/select mapping, and repository contract under MySQL Testcontainers.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 103 Java tests passed.
- `npm test`
  - 44 TypeScript test files passed, 178 tests passed.
- `npm run typecheck`
  - Passed.

Static verification:

- `git diff --check`
  - Passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" ...`
  - No trailing whitespace or conflict marker matches in touched backend/docs files.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - No token pattern matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-17-java-audit-signed-record-baseline.md`
  - No unchecked J28A plan tasks remain.

Evidence boundaries:

- J28A tests use JUnit/MockMvc, in-memory repositories, and MySQL Testcontainers. No real external provider call was executed.
- `signatureKind="sha256-chain-v1"` and `signatureValue=chainDigest` are local integrity metadata. J28A does not implement external KMS, private-key signatures, non-repudiation, key rotation, historical row backfill, multi-node chain locking, retention execution, destructive purge, or public audit write API.

## Phase J29A - Java Provider Secret Injection Baseline

Status: implemented for J29A. The Java backend now supports a narrow backend-internal resolver SPI for DB-backed `secret://...` provider credential refs and passes resolved secret values only through out-of-band per-run worker environment injection.

Scope delivered:

- Added `ProviderSecretResolver` SPI for backend-owned `secret://...` lookup.
- Added `AgentWorkerSecretInjection` as a separate Java-side carrier for per-run environment variables.
- Extended `AgentWorker` with explicit `supportsSecretInjection()` and a secret-injection overload that fails closed by default.
- Updated `AgentRunService` to turn resolved `secret://...` refs into `apiKeyEnvName = "PROVIDER_CREDENTIAL_API_KEY"` plus out-of-band secret injection.
- Updated `LocalTsAgentWorker` to inject secrets into the child process environment while continuing to serialize only `AgentWorkerRequest` JSON to stdin.
- Updated provider credential run tests to prove the worker request/provider runtime map does not contain raw secret values or `apiKeySecretRef`.
- Updated Java backend platform spec, phase-one audit, delivery report, and J29A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest,LocalTsAgentWorkerTest test`
  - Initial RED: test compilation failed as expected.
  - Expected failure: `ProviderSecretResolver`, `AgentWorkerSecretInjection`, and worker secret injection overload did not exist yet.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest test`
  - Secondary RED: 1 test failed as expected after adding a `toString()` redaction assertion.
  - Expected failure: Java record default `toString()` printed the fake secret value.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest,LocalTsAgentWorkerTest test`
  - 5 Java tests passed.
  - Covers `secret://` resolver success, worker request redaction, `AgentWorkerSecretInjection.toString()` redaction, provider runtime metadata shape, local worker env injection, and request JSON no-secret boundary.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 104 Java tests passed.
- `npm test`
  - 44 TypeScript test files passed, 178 tests passed.
- `npm run typecheck`
  - Passed.

Static verification:

- `git diff --check`
  - Passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" ...`
  - No trailing whitespace or conflict marker matches in touched backend/docs/prototype files.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - No token pattern matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-17-java-provider-secret-injection-baseline.md`
  - No unchecked J29A plan tasks remain.

Evidence boundaries:

- J29A tests use MockMvc, a test resolver, a capturing fake worker, a fake local worker script, and MySQL Testcontainers. No real external provider call was executed.
- `RESOLVED_SECRET_VALUE` and `test-local-worker-secret` in tests are fake strings, not real provider tokens.
- J29A is a local backend/worker secret injection baseline. It does not implement production KMS, Keychain/file adapter, secret rotation, public secret registration API, remote runner secret distribution, or real provider execution.

## Phase J30A - Java Remote Runner Registry Lease Baseline

Status: implemented for J30A. The Java backend now has a workspace-scoped remote runner registry / heartbeat / lease metadata baseline under the JDBC profile. This is a control-plane metadata slice, not a production remote runner dispatch platform.

Scope delivered:

- Added Flyway migration `V11__remote_runner_registry_baseline.sql`.
- Added `RemoteRunnerRecord`, `RemoteRunnerStatus`, `RemoteRunnerRepository`, `RemoteRunnerService`, and `RemoteRunnerController`.
- Added owner-only public endpoints:
  - `GET /v1/workspaces/{workspaceId}/remote-runners`
  - `PUT /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}`
  - `POST /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat`
  - `POST /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease`
- Added workspace audit events for register, heartbeat, and lease metadata actions.
- Added URL/userinfo and raw-secret-alias rejection for runner registration.
- Updated Java backend platform spec, phase-one completion audit, delivery report, and J30A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=RemoteRunnerControllerTest,RemoteRunnerRepositoryTest`
  - Initial RED: test compilation failed as expected.
  - Expected failure: `RemoteRunnerRepository`, `RemoteRunnerRecord`, and `RemoteRunnerStatus` did not exist yet.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=RemoteRunnerControllerTest,RemoteRunnerRepositoryTest`
  - 5 Java tests passed.
  - Covers workspace owner runner registration/list, heartbeat, exclusive lease, viewer forbidden guard, endpoint URL credential rejection, raw secret alias rejection, OpenAPI path exposure, Flyway v11 migration, and JDBC repository lease conflict behavior.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 109 Java tests passed.
- `npm run typecheck`
  - Passed.

Static verification:

- `git diff --check`
  - Passed.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" ...`
  - No trailing whitespace or conflict marker matches in touched backend/docs/prototype files.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - No token pattern matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-17-java-remote-runner-registry-lease-baseline.md`
  - No unchecked J30A plan tasks remain.

Evidence boundaries:

- J30A tests use MockMvc, JUnit, and MySQL Testcontainers. No real remote runner service and no real external provider call were executed.
- The runner endpoint URL is metadata only. J30A does not dispatch jobs to registered runners.
- J30A does not implement runner identity tokens, mTLS, remote artifact upload, remote cancellation, multi-node scheduler, runner-scoped credential access, or remote runner secret distribution.

## Phase J31A - Java Identity Hardening Baseline

Status: implemented for J31A. The Java backend now fails closed for production-like identity profiles instead of trusting local development identity headers or silently using the configured dev principal.

Scope delivered:

- Added `BackendAuthMode` to detect production-like profiles.
- Added `AuthenticationRequiredException` and `AUTHENTICATION_REQUIRED` public API error mapping.
- Updated `DevHeaderAuthenticationFilter` so `prod` / `production` profiles do not set `BackendPrincipal` from `X-Dev-*` headers.
- Updated `DevPrincipalProvider` so `prod` / `production` profiles do not fall back to configured dev principal when no authenticated backend principal exists.
- Added `ProductionIdentityHardeningTest`.
- Updated Java backend platform spec, phase-one completion audit, delivery report, and J31A plan archive.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=ProductionIdentityHardeningTest`
  - Initial RED: 2 tests failed as expected.
  - Expected failure: prod profile returned HTTP 200 with `prod-fallback-user` when unauthenticated and HTTP 200 with `spoofed-user` from `X-Dev-*` headers.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=ProductionIdentityHardeningTest,SecurityPrincipalControllerTest,IdentityControllerTest`
  - 6 Java tests passed.
  - Covers production-like profile rejection of dev headers, production-like no-fallback behavior, and unchanged default/local dev principal behavior.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 111 Java tests passed.
- `npm run typecheck`
  - Passed.

Static verification:

- `git diff --check`
  - Passed.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - No token pattern matches.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-17-java-identity-hardening-baseline.md`
  - No unchecked J31A plan tasks remain after final plan update.

Evidence boundaries:

- J31A is an identity hardening baseline only. It does not implement OIDC/OAuth, SSO, session management, invite flow, global user/team directory CRUD, external IAM integration, or production claim-to-role sync.
- Tests use MockMvc/JUnit only. No external identity provider was called.

## Frontend Control Plane Static Prototype

Status: draft prototype for review. A static HTML console prototype and frontend design note now exist for the future Java backend control plane UI.

Scope delivered:

- Added `docs/architecture/frontend-control-plane-design.md`.
- Added `docs/prototypes/backend-console.html`.
- Prototype covers workspace overview, run queue, approvals, artifacts, audit evidence, and provider credential metadata.
- Prototype uses static sample data only and does not call backend APIs, store provider secrets, or parse runtime-private worker fields.

Verification:

- `git diff --check`
  - Passed.
- Static conflict/trailing-whitespace scan over docs/prototype files
  - No matches.

Evidence boundaries:

- This is not production frontend implementation and not an E2E browser/API test.
- No dev server is required; the HTML file can be opened directly for visual review.

## Boundaries

- 没有真实 DeepSeek / Claude Code 调用。
- MiMo 已执行一次 opt-in real smoke；这只证明 `provider-smoke` 到 `/chat/completions` 的真实调用可用，不等于完整 organize workflow 已用真实 MiMo 发布 workspace。
- MiMo 已执行一次 opt-in real workflow smoke；发布目标是临时 fixture workspace，不是用户真实 workspace。
- `mimo-vllm-fixture` 只证明无 token fixture/local-compatible 链路，不证明真实 MiMo API 可用。
- `mimo-real` 已接入 adapter/registry/CLI/smoke，并通过 `mimo-v2.5` 真实 smoke。
- 本报告不记录任何 API key/token 值。
- `--api-key-stdin` 只降低命令行泄密风险，不替代 token 轮换和本地 secret 管理。
- 没有引入新依赖。
- 没有改变现有 workspace public contract；`KnowledgeMethodology` 是新增内部 domain contract。
- run artifacts 已记录 `methodologyId` / `methodologyVersion`；后续新增 methodology profile 时必须补充 registry、planner、validator 和 workflow artifact tests。
- `same runId` 的语义是继续旧 run，不是重新规划新 workspace。

## Review Focus

- `planNode` 复用 `plan.json` 是否符合同 runId 的恢复语义。
- `inspectRuntimeResume` 的三态决策是否足够保守。
- `NEEDS_REPLAN` 是否正确阻断覆盖用户改动。
- failure report 是否足够可操作。
- retry 成功后 attempts 是否保留失败历史，而不是只记录最终状态。
- DeepSeek real adapter 是否严格保持 skip-by-default 和 explicit opt-in 语义。
- raw envelope capture 是否只暴露 redacted 数据，且不误删 usage token counts。
- raw_ref trace 是否只引用 artifact path，不内联 provider payload。
- `deepseek-real` runtime config 是否避免把 API key 放进 config/state。
- CLI 是否阻断未显式允许的 real provider。
- provider dependency injection 是否避免进入 LangGraph state/checkpoint。
- `mimo-vllm-fixture` 是否始终保持无 token、无网络、不过度承诺真实 provider 能力。
- `mimo-real` 是否严格保持 env-only secret、explicit opt-in、redacted raw envelope、no default real external call。
- `provider-smoke --api-key-stdin` 是否避免把 API key 暴露在 args/stdout/stderr。
- `agent-loop` artifact 是否只作为审计/eval 输入，不参与 resume/publish 判定。
- deterministic repair 是否只修复低风险格式质量问题，不伪造业务事实。
- `lmwiki-v1` profile 是否完全覆盖当前 deterministic repair 的真实 provider 变体。
- methodology registry 是否保持规则可替换，但不允许 profile 直接写 workspace 或执行任意脚本。
- `methodologyId` 是否保持 run-level truth source，避免 plan/workItem/eval/report 之间漂移。
- Validator 的 profile 化是否保持 `lmwiki-v1` 默认行为兼容。
- CLI unknown methodology gate 是否发生在 workspace 写入前。
- work item loop gate 是否足够早：必须先写并校验 `agent-loop`，再写 patch/quality artifact。
- `eval.agentLoop.total` 是否严格等于 plan 内 work item 数，而不是 artifact 数。
- `missingArtifacts` / `corruptArtifacts` 是否只影响 eval/report 可观测性，不改变 publish/resume truth source。
- atomic artifact write 是否覆盖所有关键 JSON/text 写入，避免半写文件进入 report/eval。
