# LLM-backed Open Agent Orchestrator Spec

> 状态：Phase 31 candidate spec。目标是把当前 deterministic open agent runtime 升级为生产级的 LLM-backed knowledge agent orchestrator：能处理开放知识任务、进行多步工具循环、产出 grounded answer / draft / candidate patch，并在写入时显式交给固定 workflow 或确认链路。

## 1. Why This Phase Exists

Phase 28-30 已经证明了入口、安全边界和候选补丁：

- `handleCommand` 可以把请求分到 fixed workflow、open agent task、confirmation。
- `OpenAgentRuntime` 可以生成 answer、draft、candidate patch。
- MiMo real smoke 已证明真实 provider 可通过 secret-safe 方式调用。
- direct publish 仍被 `PatchBundle -> MergeGuard -> Validator -> Publisher` 约束。

但当前 open agent 仍然是 deterministic baseline。它还不能像 Claude Code 一样：

- 根据问题动态决定需要读哪些上下文；
- 在一个任务内进行多轮 tool loop；
- 用 LLM 做规划、综合、反思和必要的澄清；
- 把执行轨迹、工具调用、provider raw envelope、grounding 和 eval 形成统一审计证据；
- 支持后续更多 open-ended 知识场景，而不是继续增加意图表。

本阶段解决这个缺口。

## 2. Product Scenarios

### Scenario A: Knowledge-grounded Answer

用户问：

```text
根据知识库里关于 AI agent 的内容，总结一下当前架构为什么要分 fixed workflow 和 open agent。
```

系统应该：

- 判断为 `OPEN_AGENT_TASK / READ_ONLY / ANSWER_ONLY`；
- 动态读取 workspace inventory、相关 raw/knowledge pages、methodology profile；
- 必要时进行 1-3 轮 context expansion；
- 输出带 sources 的回答；
- 写 `.agent-runs/open-agent/<taskId>.json` 和 trace；
- 不写 workspace。

做与不做的区别：

- 不做：只能返回 deterministic 文件计数和 top paths，不能真正回答复杂问题。
- 做：后端可以把它当成知识库问答/研究 agent 的第一版生产能力。

### Scenario B: Draft Artifact

用户说：

```text
根据现有知识库，生成一份 AI 八股文问题清单。
```

系统应该：

- 判断为 `OPEN_AGENT_TASK / DRAFT_ONLY / DRAFT_ARTIFACT`；
- 用 LLM 生成结构化草稿；
- 草稿必须标记未发布；
- 输出 grounding refs 和 self-check；
- 不直接落库。

做与不做的区别：

- 不做：只能产出模板化草稿。
- 做：用户能拿到真正结合知识库的可用材料。

### Scenario C: Candidate Patch Proposal

用户说：

```text
把刚才这份问题清单准备落库，但先不要发布。
```

系统应该：

- 生成 `CANDIDATE_PATCH_PROPOSAL`；
- 给出 target paths、content sha、rationale、risk、handoff；
- 不写 `knowledge-base/`；
- 不产生 publishable `PatchBundle`；
- 后续只有用户确认后才能进入固定 workflow 或明确的 patch validation/publish 链路。

做与不做的区别：

- 不做：open agent 无法表达“我建议这样写，但还没写”。
- 做：开放 agent 可以参与写入规划，同时不破坏安全边界。

### Scenario D: Ambiguous Write Confirmation

用户说：

```text
把这个整理一下落库。
```

系统应该：

- 不猜测目标文件；
- 返回 `CONFIRMATION_REQUIRED`；
- 给出 fixed workflow handoff 或澄清问题；
- 不调用 publish。

做与不做的区别：

- 不做：要么过度拒绝，要么冒险写错。
- 做：后端可以用确认 UI 接住高风险动作。

## 3. Architecture Decision

本阶段采用：

```text
Hybrid LangGraph Orchestrator + Domain-pure Safety Core + Provider Adapter Boundary
```

含义：

- LangGraph 负责 open agent 的 node orchestration、loop budget、checkpoint、interrupt 和 trace correlation。
- Domain Core 继续负责 workspace inventory、methodology、patch、merge、validation、publisher。
- LLM provider 只能通过 adapter 被 agent node 调用，不能直接决定 publish。
- Open agent 可以生成 answer/draft/candidate patch，但不能写 workspace。
- Fixed workflow 仍是标准化落库和 publish 的主路径。

## 4. Why LangGraph Here

当前 LangGraph 已经用于固定 organize workflow。继续使用 LangGraph 的原因：

- open agent 需要明确的 node 边界、状态流转和可恢复 artifact；
- tool loop 需要 step budget、失败状态、trace 和 checkpoint；
- 后续后端接入需要稳定的 graph input/output contract；
- 与现有 fixed workflow runtime 技术栈一致，减少双 runtime 成本。

暂不引入 DeepAgents SDK 作为主架构中心。DeepAgents/Claude Code/OpenAI Agents SDK 可以作为未来 provider/tool-loop adapter 参考，但本项目的领域安全边界必须留在 Domain Core。

## 5. Deep Agent Reference Pattern

生产级 deep agent 通常包含：

- Router: 判断固定流程、开放任务、确认、拒绝。
- Planner: 生成可执行步骤，不直接执行写入。
- Context Engine: 检索/读取相关上下文。
- Tool Runtime: 受 policy 限制地调用工具。
- Agent Loop: `think -> tool -> observe -> revise` 的 bounded loop。
- Synthesizer: 产出 answer/draft/proposal。
- Critic/Evaluator: 自检 grounding、幻觉、写入边界、格式。
- Memory/Trace: 记录 canonical trace、raw envelope、redaction、artifact refs。
- Human Gate: 对高风险写入或低置信度任务做确认。

本阶段实现其中的最小生产闭环：Router 已有，新增 LLM-backed planner/tool-loop/synthesizer/critic，并复用现有 trace/provider/validation。

## 6. Open Agent Graph

新增 `OpenAgentGraph`：

```text
CommandRoute / OpenAgentTaskEnvelope
  -> PolicyGateNode
  -> PlanNode
  -> ContextGatherNode
  -> AgentToolLoopNode
  -> SynthesizeNode
  -> SelfCheckNode
  -> ArtifactNode
  -> HandoffNode
```

### PolicyGateNode

输入：

- route risk；
- requested output policy；
- allowed/blocked tool names；
- methodology id；
- provider runtime config。

职责：

- 阻断 `patch.publish`、raw/schema writes、unknown methodology；
- 设置 loop budget；
- 判断是否需要 human confirmation。

### PlanNode

职责：

- 用 LLM 或 deterministic fallback 生成 task plan；
- plan 必须是 JSON schema constrained；
- plan 不能包含 workspace write action；
- plan 只能引用 allowed tool names。

### ContextGatherNode

职责：

- 先 deterministic scan workspace；
- 根据 plan 读取候选 raw/knowledge pages；
- 未来可替换为 vector search，但本阶段不引入数据库。

### AgentToolLoopNode

这是 agent 的多步推理/循环所在节点。

职责：

- 执行 bounded loop；
- 每轮记录 thought summary、tool call、observation ref；
- 允许的工具第一版仅包括 read/eval/validate 类工具；
- loop 结束条件包括 solved、needs more context、needs confirmation、budget exhausted。

### SynthesizeNode

职责：

- 产出 `ANSWER_ONLY`、`DRAFT_ARTIFACT` 或 `CANDIDATE_PATCH`；
- answer/draft 必须带 grounding refs；
- candidate patch 必须 `publishable: false`。

### SelfCheckNode

职责：

- 检查 grounding refs 是否非空；
- 检查输出是否引用不存在的文件；
- 检查 candidate patch target 是否在 `knowledge-base/`；
- 检查是否试图直接 publish；
- 对 LLM 输出做 schema validation。

### ArtifactNode

职责：

- 写 `.agent-runs/open-agent/<taskId>.json`；
- 写 `traces/<taskId>.jsonl`；
- 写 redacted raw provider envelope refs；
- 不写 workspace content。

### HandoffNode

职责：

- 对确认/写入类结果生成 fixed workflow handoff；
- 后端可以据此展示确认 UI 或调用 `runOrganize`；
- open agent candidate patch 不能自动进入 publisher。

## 7. Public SDK Contract

新增：

```ts
interface RunOpenAgentGraphRequest {
  workspaceRoot: string;
  taskId?: string;
  message: string;
  methodologyId?: string;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
  executeReal?: boolean;
  loopBudget?: {
    maxIterations: number;
    maxToolCalls: number;
    timeoutMs: number;
  };
}

interface RunOpenAgentGraphResult {
  taskId: string;
  status:
    | "SUCCEEDED"
    | "NEEDS_CONFIRMATION"
    | "FAILED_POLICY"
    | "FAILED_PROVIDER"
    | "FAILED_VALIDATION"
    | "FAILED_BUDGET";
  route: CommandRoute;
  outputPolicy: OpenAgentOutputPolicy;
  answer?: string;
  draftArtifact?: DraftArtifact;
  candidatePatch?: CandidatePatchProposal;
  confirmation?: CommandConfirmation;
  artifactPath: string;
  tracePath: string;
  providerCalls: number;
  realExternalCall: boolean;
}
```

`handleCommand` 后续可以选择：

- lightweight route only；
- deterministic open runtime；
- LLM-backed open graph。

第一版建议通过显式 option 启用 LLM-backed open graph，避免破坏当前 SDK 行为。

## 8. Provider Strategy

Provider 层沿用 OpenAI-compatible adapter 思路：

- MiMo: priority real smoke provider。
- DeepSeek: supported but not default。
- Claude Code / OpenAI Agents: future adapter，不能成为领域架构中心。
- Fake/fixture provider: unit/integration test default。

Provider 输出必须经过：

```text
raw envelope redaction
  -> normalized provider result
  -> schema parse
  -> agent node boundary
  -> artifact writer
```

不得把 API key、token、cookie 写入：

- source code；
- docs；
- `.agent-runs`；
- trace；
- stdout/stderr；
- test snapshot。

## 9. Trace And Artifact Contract

新增 open agent graph artifact：

```text
.agent-runs/open-agent/<taskId>.json
.agent-runs/open-agent/traces/<taskId>.jsonl
.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/request.json
.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/response.json
```

Report 必须包含：

- route；
- methodology；
- loop budget；
- steps；
- tool calls；
- grounding refs；
- provider calls；
- realExternalCall；
- outputRef；
- confirmation/handoff；
- validation findings。

Trace 只能作为审计/eval 输入，不能作为 publish/resume truth source。

## 10. Safety Rules

Hard blockers:

- open graph allowed tools 包含 `patch.publish`；
- LLM 输出包含 raw/schema write；
- candidate patch target 不在 `knowledge-base/`；
- grounding refs 为空但输出声明 based on workspace；
- provider raw envelope 未 redacted；
- loop budget exhausted but status 仍标记 success；
- schema parse failed but继续产出结果。

Human confirmation required:

- message implies workspace write but target/range unclear；
- candidate patch 被用户要求落库；
- validator 发现可能丢弃用户内容；
- methodology profile 不确定或用户指定未知 profile。

## 11. Upgrade Conditions

只有满足以下条件，才从 deterministic open runtime 升级为默认 LLM-backed graph：

- fake provider tests cover all statuses；
- MiMo real smoke passes；
- at least one real MiMo open answer or candidate patch eval passes；
- no token leakage in artifacts；
- full tests/typecheck/diff pass；
- output quality eval 能识别 weak grounding / empty sources / invalid schema；
- confirmation path 不会自动 publish。

## 12. Acceptance

- `runOpenAgentGraph` public SDK exported。
- `handleCommand` can route to LLM-backed graph behind explicit option。
- Unit tests cover policy gate, plan parse, context gather, tool loop budget, synthesize, self-check, artifact contract。
- Integration tests cover fake provider answer/draft/candidate patch。
- Real MiMo smoke covers one `ANSWER_ONLY` or `CANDIDATE_PATCH` open graph run。
- Artifact redaction tests prove token not persisted。
- Full `npm test`, `npm run typecheck`, `git diff --check` pass。

## 13. Review Focus

- Does the graph preserve fixed workflow as the only publish path?
- Does AgentToolLoopNode make multi-step reasoning visible without storing private chain-of-thought?
- Are provider adapters replaceable without changing Domain Core?
- Does the backend integration surface have enough information for confirmation UI?
- Are tests strong enough to catch fake success and token leakage?
