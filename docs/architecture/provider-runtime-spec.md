# Provider Runtime Spec

> 状态：真实 LLM provider 接入前的主 spec。本文定义 provider runtime 的目标、边界、接口、验收和升级条件。它不替代 `llm-call-trace-contract.md`，而是规定 provider 如何进入现有 LangGraph-first / Domain-pure 架构。

## Current Progress

已经完成并有测试覆盖：

- LangGraph runtime 已接入 `@langchain/langgraph` `StateGraph`。
- Workflow 已包含 `inventory -> plan -> approval pause -> execute -> report`。
- `MemorySaver` checkpoint boundary 已接入，并通过同一 `runId/thread_id` 的集成测试验证。
- `.agent-runs` artifact-based resume 已接入，恢复判断基于 artifacts、workspace current sha、`PatchBundle` content sha。
- `LlmNoteProvider` adapter interface 已存在。
- `fake` note provider 已存在，并能驱动 note agent 生成 `PatchBundle`。
- `ProviderRuntimeConfig` 已存在，当前支持 `provider: "fake"`。
- Provider registry 已存在，默认选择 fake note provider。
- `runOrganizeWorkflow` 已接受 `providerRuntime` 配置。
- `organize` CLI 已支持 `--provider fake`。
- `deepseek-fixture` provider 已存在，使用本地 OpenAI-compatible fixture response。
- `claude-code-fixture` provider 已存在，使用本地 Claude Code result fixture。
- Provider error classification 已存在，覆盖 timeout/auth/schema 等稳定类别。
- Failure harness 已存在，能验证 timeout 不发布、invalid content 被 Validator 阻断。
- Optional real smoke harness 已存在，默认无 env 时跳过，不发起真实外部调用。
- LLM trace canonical JSONL、provider normalizers、mock agent trace 写入已存在。

尚未完成：

- 未接入真实 Claude Code Agent SDK。
- 未接入真实 DeepSeek API。
- 未接入真实 MiMo API 或本地 MiMo engine。
- 未实现真实 provider fallback、rate limit。
- 未实现 durable LangGraph checkpoint saver。

## Goal

在不破坏现有 Domain Core 的前提下，让 LangGraph runtime 可以选择一个 `LlmNoteProvider` adapter 处理 bounded work item，并把 provider 调用结果转成受控 `PatchBundle`。

目标状态：

```text
LangGraph execute node
  -> select provider adapter
  -> provider.generateNote(...)
  -> note agent wraps provider result as PatchBundle
  -> trace writer records provider call
  -> MergeGuard checks write boundary
  -> Validator checks quality boundary
  -> Publisher writes workspace
  -> artifact resume remains deterministic
```

## Non-Goals

- 不把 provider SDK 作为领域架构中心。
- 不让 provider 原始 response 直接写 workspace。
- 不让 trace、transcript、provider session id 参与 resume/publish 判定。
- 不在第一轮实现多 provider marketplace。
- 不优先实现 MiMo；MiMo 只要求接口和 trace contract 不排斥。
- 不把 real provider smoke 当作主验收。主验收必须在无 API key 环境稳定通过。
- 不在没有 durable checkpoint saver 前承诺跨进程长任务恢复。

## Architecture Rules

### 1. Provider 是 Adapter

Provider 只能实现接口：

```ts
interface LlmNoteProvider {
  generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult>;
}
```

Provider 输入只能包含：

- `runId`
- bounded `WorkItem`
- scoped source content
- 后续允许加入只读 config，例如 model、timeout、temperature

Provider 输出只能包含：

- `providerCallId`
- `provider`
- `model`
- `finishReason`
- `usage`
- candidate `content`

Provider 不能：

- 直接写 workspace。
- 写 `.agent-runs` domain artifacts。
- 决定 publish。
- 跳过 `PatchBundle`、`MergeGuard`、`Validator`。
- 读取超出 work item scope 的任意 workspace 内容。

### 2. Agent Node 负责封装

Agent node 可以调用 provider，但它对 workflow 暴露的输出仍然只能是：

```text
PatchBundle / QualityFindings
```

如果 provider 输出不满足 topic note quality contract，agent node 只能生成被 Validator 阻断的 patch，或返回失败状态；不能放宽 Validator。

### 3. LangGraph 负责 Runtime 编排

LangGraph 可以负责：

- provider selection。
- timeout / retry / fallback 的运行时顺序。
- approval pause。
- phase transition。
- checkpoint state。

LangGraph 不能成为 domain truth。恢复时仍以：

```text
.agent-runs artifacts
workspace current sha
PatchBundle contentSha
Validator result
```

为准。

### 4. Trace 是审计，不是事实源

Provider 调用必须写 canonical trace：

```text
.agent-runs/<runId>/traces/<workItemId>.jsonl
```

Raw provider envelope 只能作为 redacted debug attachment。Trace 不参与 publish/resume 判定。

## Provider Priority

当前优先级：

1. `fake`: 主测试 provider，必须稳定、无网络、无密钥。
2. `deepseek-fixture`: OpenAI-compatible fixture provider，用本地 fixture 验证 request/response mapping。
3. `claude-code-fixture`: Claude Code Agent SDK transcript fixture provider，用本地 fixture 验证 event/session/result mapping。
4. `deepseek-real-smoke`: optional real provider smoke，只在显式 env 和用户批准下运行。
5. `claude-code-real-smoke`: optional real SDK smoke，只在 SDK、认证、env 都明确时运行。
6. `mimo-fixture`: 次优先级，只验证接口不排斥 MiMo API / vLLM / SGLang raw envelope。

MiMo 不阻塞主线。

## Provider Runtime Config

第一版 config 只放 runtime 层：

```ts
interface ProviderRuntimeConfig {
  provider: "fake" | "deepseek-fixture" | "claude-code-fixture";
  model?: string;
  timeoutMs: number;
  temperature?: number;
  maxTokens?: number;
}
```

CLI 可以后续支持：

```bash
node --import tsx src/cli/organize.ts <workspaceRoot> "整理全部知识库" \
  --auto-approve \
  --provider fake
```

默认必须是 `fake`，保证本地无密钥可跑。

## Error Classification

Provider runtime 必须把错误归类为稳定枚举：

```ts
type ProviderErrorClass =
  | "timeout"
  | "rate_limit"
  | "auth"
  | "provider"
  | "network"
  | "schema"
  | "unknown";
```

映射规则：

| Error | Work item result | Retryable |
| --- | --- | --- |
| timeout | `FAILED_TIMEOUT` | yes |
| rate_limit | `FAILED_EXECUTOR` | yes |
| network | `FAILED_EXECUTOR` | yes |
| auth | `FAILED_EXECUTOR` | no |
| schema | `BLOCKED_BY_VALIDATOR` or `FAILED_EXECUTOR` | no by default |
| provider | `FAILED_EXECUTOR` | provider-specific |
| unknown | `FAILED_EXECUTOR` | no by default |

错误必须写 trace `llm.call.failed`，但 resume 仍使用 work item status 和 artifacts。

## Acceptance Matrix

主验收必须全部不依赖真实 API key。

| Layer | Acceptance | Test type |
| --- | --- | --- |
| Provider interface | fake provider returns deterministic `LlmNoteProviderResult` | unit |
| Provider injection | note agent uses injected provider before building `PatchBundle` | unit |
| Trace | provider call writes `llm.call.started` and `llm.call.completed` | integration |
| DeepSeek fixture | OpenAI-compatible response maps to `LlmNoteProviderResult` and canonical trace fields | unit |
| Claude Code fixture | transcript/result fixture maps to `LlmNoteProviderResult` and canonical trace fields | unit |
| Runtime selection | execute node chooses provider from runtime config, defaulting to fake | integration |
| Failure classification | timeout/auth/schema map to stable work item behavior and trace events | unit + integration |
| Guardrail | invalid provider content is blocked by Validator, not published | integration |
| CLI | `--provider fake` smoke succeeds with same artifacts as default | integration |
| Resume | provider trace does not change artifact-based resume decisions | integration |

## Unit Test Requirements

每个 provider adapter 至少要有：

- request building test。
- response normalization test。
- usage mapping test。
- finish reason mapping test。
- error classification test。
- redaction test if raw envelope is stored.

每个 runtime integration 至少要断言：

- `PatchBundle` artifact exists。
- trace artifact exists。
- workspace target content matches `PatchBundle` content sha。
- `resume` reports `SKIP` when workspace sha matches。
- invalid content never reaches workspace.

## Implementation Phases

### Phase 1: Runtime Selection With Fake Provider

状态：已完成。

目标：

- 增加 `ProviderRuntimeConfig`。
- `runOrganizeWorkflow` 接受 provider config。
- execute node 通过 provider registry 选择 `fake` provider。
- CLI 支持 `--provider fake`。

验收：

- unit: provider registry default is fake。
- integration: CLI `--provider fake` smoke passes。
- integration: trace still written。
- full: `npm test`, `npm run typecheck`, `git diff --check`。

### Phase 2: Fixture Providers

状态：已完成。

目标：

- `deepseek-fixture` provider：读取本地 fixture response，不联网。
- `claude-code-fixture` provider：读取本地 transcript/result fixture，不启动 SDK。
- 两者都输出 `LlmNoteProviderResult`。

验收：

- unit: DeepSeek fixture maps content、reasoning、usage。
- unit: Claude Code fixture maps result、usage、cost、stop reason。
- unit: redaction removes auth-like fields。
- integration: note agent can use fixture provider and still pass Validator。

### Phase 3: Failure And Guardrail Harness

状态：已完成。

目标：

- provider timeout/auth/schema failure classification。
- failed provider call emits `llm.call.failed`。
- bad content is blocked by Validator。

验收：

- unit: `classifyProviderError` covers all stable classes。
- integration: timeout marks work item failure without publishing。
- integration: invalid provider output does not write workspace。
- resume: failed/retryable status maps to correct resume action。

### Phase 4: Optional Real Smoke

状态：harness 已完成；真实外部调用未执行。真实调用需要网络、凭证或 SDK 条件，必须单独确认。

目标：

- 在用户显式允许、env 存在、网络可用时，跑一个最小 real provider smoke。

验收：

- real smoke 必须单独命令触发。
- smoke result 必须标注 `real external call`。
- smoke 失败不能影响主 test suite。
- 不提交任何 token、cookie、API key、真实用户内容。

## Stop Conditions

必须暂停并重新对齐的情况：

- 需要引入真实 SDK 依赖。
- 需要访问网络或真实 provider。
- 需要写入 token、cookie、API key 或账号信息。
- provider 输出要求绕过 Validator。
- LangGraph state 开始承载 domain truth。
- 修改 `PatchBundle`、`WorkItem`、workspace contract 等 public contract。
- 测试需要真实 provider 才能通过。

## Review Focus

- Provider adapter 是否保持纯边界，不污染 domain core。
- Trace 是否只用于审计，不影响 resume。
- Error classification 是否稳定、可测试。
- CLI provider selection 是否默认 fake。
- Fixture provider 是否足以证明真实 provider 接入前的 contract。
- MiMo 是否保持兼容但不阻塞主线。
