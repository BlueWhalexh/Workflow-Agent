# Agent SDK MVP Phase 1 Spec

> 状态：Phase 35 candidate spec。目标是把当前已经具备的 fixed workflow、OpenAgentGraph、provider smoke 和 artifact 证据收敛成后端可接入的第一版 Agent SDK，而不是开始做完整后端平台。

## 1. Current Truth

当前 SDK 已有以下能力：

- `createKnowledgeWorkflowAgent()` 返回 facade，包含 `runOrganize`、`inspectRun`、`handleCommand`、`runOpenAgentTask`、`runOpenAgentGraph`、`runOpenAgentRealSmoke`。
- `handleCommand()` 已经能把用户消息路由到 `FIXED_WORKFLOW`、`OPEN_AGENT_TASK` 或 `CONFIRMATION_REQUIRED`。
- `OpenAgentGraph` 已迁到 LangGraph `StateGraph`，并能输出 answer、draft artifact、candidate patch proposal 和 provider call evidence。
- `.agent-runs` 已经承担当前 artifact-backed persistence：run report、trace、raw provider request/response、open-agent report。
- MiMo real smoke 已验证过真实 provider 调用链路；Keychain / hidden stdin / ignored `.env` 可作为本地凭证入口。

当前缺口：

- 后端如果直接调用 `handleCommand()`，需要理解多个可选字段：`workflow`、`openAgent`、`openAgentGraph`、`confirmation`。
- fixed workflow 和 open-agent 的 result shape 不统一，后端需要自己判断 output kind、artifact path、status 和下一步动作。
- artifact 证据已经存在，但 SDK 没有一个统一的 data-layer response 明确告诉调用方“本次运行写了哪些证据、是否写了用户 workspace、是否需要确认”。
- 一期缺少一个面向后端的 smoke harness：不用真实后端，也能模拟“用户请求 -> SDK -> runtime -> artifact -> response”的数据流。

## 2. Goal

Agent SDK Phase 1 的完成定义是：后端可以只依赖 public SDK，一次调用完成用户指令处理，并拿到稳定、可审计、可展示的结构化结果。

具体目标：

- 提供一个统一入口，例如 `agent.runAgent(request)`。
- 统一 fixed workflow、open-agent deterministic、open-agent llm graph、confirmation 的返回 envelope。
- 返回明确的 `route`、`status`、`outputKind`、`output`、`artifacts`、`diagnostics`。
- 保留现有低层接口作为 advanced APIs，不要求后端深层 import `src/runtime/*`。
- 用 mock / fake / injected provider / optional real smoke 证明链路，而不是只靠抽象接口说明。
- 继续坚持 no direct publish：OpenAgent candidate patch 只写 `.agent-runs`，不直接写目标 workspace 文件。

## 3. Non-goals

一期不做：

- HTTP backend server、controller、DB schema、用户/组织/权限/登录。
- 前端 approval UI。
- 跨进程 durable checkpoint store。
- 把 LangGraph internal node、provider raw envelope writer 或 publisher 暴露成 public tool。
- 让 open-agent 直接发布到 `knowledge-base`。
- 在 repo 文档、fixture、snapshot、artifact、stdout/stderr 中记录真实 token。

## 4. Public SDK Contract

新增统一入口：

```ts
interface KnowledgeWorkflowAgent {
  runAgent(request: RunAgentRequest): Promise<AgentSdkRunResult>;
}
```

现有接口保留：

- `runOrganize`
- `inspectRun`
- `handleCommand`
- `runOpenAgentTask`
- `runOpenAgentGraph`
- `runOpenAgentRealSmoke`

后端一期推荐只调用 `runAgent()`；CLI 和开发 smoke 可以继续调用低层接口。

### Request

```ts
interface RunAgentRequest {
  workspaceRoot: string;
  message: string;
  runId?: string;
  methodologyId?: string;
  execute?: boolean;
  autoApprove?: boolean;
  mode?: "auto" | "deterministic-open-agent" | "llm-open-agent" | "fixed-workflow";
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}
```

规则：

- `mode: "auto"` 或未传时，先走现有 `classifyCommand()`。
- `mode: "llm-open-agent"` 强制 open-agent 使用 `StateGraph`。
- `mode: "deterministic-open-agent"` 强制 open-agent 使用 deterministic runtime。
- `mode: "fixed-workflow"` 只允许固定 workflow；如果消息不匹配固定 workflow，也返回 `FAILED_ROUTE`，不静默改道。
- `execute` 默认 `true`，但 fixed workflow 可由调用方传 `execute: false` 只做 route preview。

### Result

```ts
type AgentSdkRunStatus =
  | "SUCCEEDED"
  | "SUCCEEDED_WITH_WARNINGS"
  | "WAITING_APPROVAL"
  | "NEEDS_CONFIRMATION"
  | "FAILED"
  | "FAILED_ROUTE"
  | "FAILED_PROVIDER"
  | "FAILED_POLICY";

type AgentSdkOutputKind =
  | "answer"
  | "draft"
  | "candidate-patch"
  | "workflow-report"
  | "confirmation"
  | "route-preview"
  | "none";

interface AgentSdkRunResult {
  schemaVersion: "agent-sdk-run.v1";
  runId: string;
  status: AgentSdkRunStatus;
  route: CommandRoute;
  capabilityId: string;
  outputKind: AgentSdkOutputKind;
  output?: {
    answer?: string;
    draftArtifact?: DraftArtifact;
    candidatePatch?: CandidatePatchProposal;
    workflow?: RunOrganizeResult;
    confirmation?: CommandConfirmation;
  };
  artifacts: {
    artifactRoot?: string;
    artifactPath?: string;
    reportPath?: string;
    tracePath?: string;
    rawProviderRefs: string[];
    wroteWorkspace: boolean;
    targetWorkspacePaths: string[];
  };
  diagnostics: {
    methodologyId: string;
    providerBacked: boolean;
    providerRuntime?: string;
    warnings: string[];
    error?: string;
  };
}
```

## 5. Status Mapping

`runAgent()` 必须把现有结果映射到统一 status：

| Source | Source Status | SDK Status |
| --- | --- | --- |
| fixed workflow | `WAITING_PLAN_APPROVAL` | `WAITING_APPROVAL` |
| fixed workflow | `SUCCEEDED_WITH_WARNINGS` | `SUCCEEDED_WITH_WARNINGS` |
| fixed workflow | `FAILED` | `FAILED` |
| open-agent deterministic | `SUCCEEDED` | `SUCCEEDED` |
| open-agent deterministic | `FAILED_POLICY` | `FAILED_POLICY` |
| open-agent graph | `SUCCEEDED` | `SUCCEEDED` |
| open-agent graph | `NEEDS_CONFIRMATION` | `NEEDS_CONFIRMATION` |
| open-agent graph | `FAILED_PROVIDER` | `FAILED_PROVIDER` |
| open-agent graph | other failed terminal status | `FAILED` |
| confirmation route | n/a | `NEEDS_CONFIRMATION` |
| route preview | n/a | `WAITING_APPROVAL` |

## 6. Data-layer Contract

一期没有 DB。SDK 的持久化事实来自 workspace 下的 `.agent-runs`：

- fixed workflow：`.agent-runs/<runId>/...`
- open-agent：`.agent-runs/open-agent/<taskId>.json`
- open-agent trace/raw provider artifacts：继续使用当前 graph artifact 写入规则。

`AgentSdkRunResult.artifacts` 是后端展示和审计的第一层索引：

- `artifactRoot` 和 `artifactPath` 给调用方定位 run 证据；
- `rawProviderRefs` 只保存 artifact ref，不保存 token；
- `wroteWorkspace` 必须区分 fixed workflow 真实 publish 与 open-agent artifact-only；
- `targetWorkspacePaths` 对 candidate patch 只表示建议目标，不表示已写入。

## 7. Security And Token Rules

- SDK request 允许通过 `providerRuntimeDependencies`、当前进程 env、Keychain helper 或 ignored `.env` 获取 token。
- 真实 token 不得写入代码、docs、fixture、snapshot、report、trace、raw provider artifact 或 stdout/stderr。
- raw provider artifact 的 `Authorization` 必须保持 `[REDACTED]`。
- 单测只能使用 fake value，例如 `test-api-key`。
- 文档可以写 base URL 和 model，不写 token。

## 8. Acceptance Criteria

一期完成必须满足：

1. Public SDK 导出 `RunAgentRequest`、`AgentSdkRunResult`、`AgentSdkRunStatus`、`AgentSdkOutputKind` 和 `agent.runAgent()`。
2. 后端模拟测试可以用同一个入口覆盖：
   - read-only answer；
   - draft artifact；
   - candidate patch proposal；
   - confirmation required；
   - fixed workflow route preview；
   - fixed workflow execution。
3. `llm-open-agent` mock 或 injected provider 测试能返回 provider-backed answer，并记录 `rawProviderRefs`。
4. candidate patch 不写入 `knowledge-base` 目标文件，`wroteWorkspace` 为 `false`。
5. fixed workflow execution 的 `wroteWorkspace` 按实际 workflow result/report 映射，不伪装成 open-agent artifact-only。
6. artifact path、report path、trace/raw refs 在 result 中可被后端直接展示。
7. focused tests、full tests、typecheck、diff check 通过。
8. 如果执行 real MiMo smoke，报告必须明确标注 `real external call`，并附 redaction、token search、no-write evidence。

## 9. Review Focus

实现阶段需要重点 review：

- public SDK contract 是否稳定、是否泄漏 runtime internal 类型；
- status/output mapping 是否会让后端误判已发布、待确认或失败；
- `wroteWorkspace` 是否真实反映副作用；
- provider raw artifacts 是否只暴露 ref 且不泄漏 token；
- mock/fake/injected/real evidence 是否被清楚区分。
