# Hybrid Agent Command Router Spec

> 状态：Phase 28 candidate spec。目标是在 public SDK 上增加一个开放 command router，使固定 workflow 和开放 agent 能力共存；不把用户未来可能提出的业务场景枚举成硬编码 intent 表。

## 1. Background

当前系统已经具备：

- 固定 deposition workflow：`runOrganize`；
- methodology-aware workflow contract：`methodologyId` 贯穿 plan / validator / eval / report；
- public SDK：`runOrganize` / `inspectRun`；
- internal tool metadata registry：工具有 risk / exposure 分类。

现在缺口是：用户自然语言进入系统后，后端仍然只能选择固定 SDK 方法。这样会把产品退化成“按钮式 API”，而不是 agent 能力系统。

但是用户示例，例如“整理知识库”“总结 AI 知识”“生成八股文清单”，都只是可能场景。系统不能把这些场景全部预设为固定 intent。

## 2. Design Principle

第一版 command router 不做“完整意图枚举”，只做 **execution lane selection**：

```text
incoming message
  -> command envelope
  -> execution lane
  -> risk policy
  -> allowed capability surface
  -> optional execution / confirmation
```

Execution lanes:

- `FIXED_WORKFLOW`: 高确定性、高风险或标准化写入流程，例如整理整个 workspace。
- `OPEN_AGENT_TASK`: 开放请求，由后续 agent runtime 处理；第一版只生成 task envelope，不执行 LLM。
- `CONFIRMATION_REQUIRED`: 可能写 workspace 但缺少对象、范围或用户确认。

这比枚举业务场景更稳定，因为新增场景通常只需要进入开放 agent task envelope，而不需要新增 router 分支。

## 3. Public SDK API

新增：

```ts
interface HandleCommandRequest {
  workspaceRoot: string;
  message: string;
  runId?: string;
  methodologyId?: string;
  autoApprove?: boolean;
  execute?: boolean;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}

interface HandleCommandResult {
  route: CommandRoute;
  workflow?: RunOrganizeResult;
  agentTask?: OpenAgentTaskEnvelope;
  confirmation?: CommandConfirmation;
}
```

`execute` 默认为 `false`。router 可以先返回 route/confirmation，后端再决定是否执行。

## 4. Command Route

```ts
type ExecutionLane = "FIXED_WORKFLOW" | "OPEN_AGENT_TASK" | "CONFIRMATION_REQUIRED";
type CommandRisk = "READ_ONLY" | "DRAFT_ONLY" | "WORKSPACE_WRITE";

interface CommandRoute {
  lane: ExecutionLane;
  capabilityId: string;
  risk: CommandRisk;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  reason: string;
}
```

Capability ids are broad, not scenario-specific:

- `workflow.organizeWorkspace`
- `agent.openTask`
- `agent.draftArtifact`
- `confirmation.workspaceWrite`

## 5. First Classifier Rules

第一版是 deterministic classifier，用于锁定 contract，不追求语义智能。

High-confidence fixed workflow:

- message 明确包含整理整个/全部知识库、organize workspace 等。

Confirmation required:

- message 包含写入类动作，例如落库、保存、写入、发布、导入；
- 但不是 high-confidence fixed workflow；
- 因为可能产生 workspace write，必须先确认范围和目标。

Open agent task:

- 其他请求默认进入开放 agent envelope。
- 如果 message 包含生成、草稿、清单、题目、计划等输出词，risk 为 `DRAFT_ONLY`。
- 否则 risk 为 `READ_ONLY`。

## 6. Open Agent Task Envelope

```ts
interface OpenAgentTaskEnvelope {
  objective: string;
  risk: "READ_ONLY" | "DRAFT_ONLY";
  outputPolicy: "ANSWER_ONLY" | "DRAFT_ARTIFACT";
  allowedToolNames: string[];
  blockedToolNames: string[];
}
```

Rules:

- `READ_ONLY` 只允许 read tools；
- `DRAFT_ONLY` 允许 read tools 和 draft result，但不能 publish；
- `patch.publish` 永远不能出现在 allowed tools；
- 第一版不执行真实 LLM，不写 workspace。

## 7. Fixed Workflow Execution

如果 route 是 `FIXED_WORKFLOW` 且 `execute=true`：

- SDK 调用 `runOrganize`；
- `autoApprove` 默认为 `false`；
- 默认只生成 plan approval，不直接写 workspace；
- 如果调用方显式传 `autoApprove=true`，沿用现有 workflow 安全边界。

## 8. Acceptance

- SDK 导出 `handleCommand` 和相关 types。
- `createKnowledgeWorkflowAgent()` 返回对象包含 `handleCommand`。
- “整理全部知识库” route 到 `FIXED_WORKFLOW`，`execute=false` 时不创建 run artifacts。
- `execute=true` 时通过 SDK 执行 organize workflow，返回 `workflow` result。
- “根据知识库总结 AI 知识” route 到 `OPEN_AGENT_TASK` / `READ_ONLY`，只允许 read tools。
- “生成一份问题清单” route 到 `OPEN_AGENT_TASK` / `DRAFT_ONLY`，不允许 `patch.publish`。
- “把这个落库” route 到 `CONFIRMATION_REQUIRED` / `WORKSPACE_WRITE`。
- Unknown methodology fail-fast。
- full tests / typecheck / diff check 通过。

## 9. Review Focus

- Router 是否避免把业务场景枚举成长期架构中心。
- Workspace write 是否仍然只通过 fixed workflow 或 confirmation path。
- Open agent task 是否默认不可写 workspace。
- 后续 LLM classifier 是否可以替换 deterministic classifier，而不改变 SDK contract。
