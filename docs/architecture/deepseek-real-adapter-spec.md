# DeepSeek Real Adapter Spec

> 状态：Phase 10 spec。本文定义 DeepSeek real adapter 的最小接入边界。默认测试和 CLI smoke 不发起真实外部调用。

## Current Official Facts

DeepSeek 官方 API 文档当前说明：

- API 兼容 OpenAI / Anthropic 格式。
- OpenAI-compatible `base_url` 是 `https://api.deepseek.com`。
- Chat API endpoint 示例是 `/chat/completions`。
- 示例模型包括 `deepseek-v4-pro`。
- `deepseek-chat` 和 `deepseek-reasoner` 标注将在 2026-07-24 15:59 UTC 弃用。

Source: `https://api-docs.deepseek.com/`

## Goal

提供一个 fetch-based `LlmNoteProvider` adapter，使 DeepSeek real provider 可以在显式 opt-in 的 smoke 命令中被调用，同时默认测试保持无网络、无密钥可通过。

## Runtime Boundary

Adapter 输入仍然是：

- `runId`
- bounded `WorkItem`
- scoped source content

Adapter 输出仍然是 `LlmNoteProviderResult`：

- `providerCallId`
- `provider: "deepseek"`
- `model`
- `finishReason`
- `usage`
- `content`

Provider 不能：

- 写 workspace。
- 写 `.agent-runs` artifacts。
- 决定 publish。
- 绕过 `PatchBundle`、`MergeGuard`、`Validator`。

## Config

DeepSeek real adapter 使用显式 config：

```ts
interface DeepSeekRealProviderConfig {
  apiKey: string;
  baseUrl: string;
  model: string;
  maxTokens?: number;
  temperature?: number;
}
```

Smoke env:

- `DEEPSEEK_API_KEY`
- `DEEPSEEK_BASE_URL`
- `DEEPSEEK_MODEL`

Recommended default values when the user opts in:

- `DEEPSEEK_BASE_URL=https://api.deepseek.com`
- `DEEPSEEK_MODEL=deepseek-v4-pro`

## Smoke Policy

`provider-smoke` must remain skip-by-default:

- Missing env => `SKIPPED`, no external call.
- Env present but no `--execute-real` => `SKIPPED`, no external call.
- `--execute-real` + required env => real external call.

Tests must use injected fake fetch and assert request/response mapping without network.

## Phase 11: Redacted Raw Envelope Capture

DeepSeek real adapter 必须支持可选 raw envelope capture hook，用于后续写入 `.agent-runs` debug artifacts。

要求：

- capture hook 只能接收 redacted envelope。
- `Authorization`、`api_key`、`token`、`cookie` 等字段必须写成 `[REDACTED]`。
- request body 可以保留 model、messages、max token 等调试字段。
- response body 可以保留 provider 返回的 content、usage、finish reason 等调试字段。
- 默认不捕获 raw envelope。
- 单测必须使用 injected fake fetch，不允许真实网络。

## Phase 12: Raw Envelope Artifacts And Trace Ref

当 real smoke 提供 `AgentRunsStore` 时，runtime 必须把 redacted raw envelope 写入 `.agent-runs` artifacts，并追加 canonical raw-ref trace。

Artifact paths:

```text
raw-provider/<workItemId>/request.json
raw-provider/<workItemId>/response.json
traces/<workItemId>.jsonl
```

Trace event:

```text
type: llm.provider.raw_ref
requestPath: raw-provider/<workItemId>/request.json
responsePath: raw-provider/<workItemId>/response.json
redaction: applied
```

要求：

- artifact 内容必须再次经过 redaction。
- trace 只保存路径，不内联 raw payload。
- 默认不写 artifact；必须显式提供 store。
- 单测使用 injected fake fetch，不允许真实网络。

## Phase 13: Opt-In Runtime Provider Registry

`deepseek-real` 可以进入 provider registry，但必须保持 opt-in 和 no-secret-in-state。

Config 只允许保存：

- `provider: "deepseek-real"`
- `baseUrl`
- `model`
- `apiKeyEnvName`
- `temperature`
- `maxTokens`

Config 不能保存 API key。Registry 必须从 env 读取 API key：

```text
env[apiKeyEnvName ?? "DEEPSEEK_API_KEY"]
```

默认值：

- `baseUrl=https://api.deepseek.com`
- `model=deepseek-v4-pro`

测试必须通过 dependency injection 提供 fake env 和 fake fetch，不允许真实网络。

## Phase 14/15: CLI Guard And Workflow Injection

CLI 可以识别 `--provider deepseek-real`，但必须显式提供 `--allow-real-provider` 才能继续。

Guard rules:

- `--provider deepseek-real` without `--allow-real-provider` => exit non-zero before workflow execution.
- `--provider deepseek-real --allow-real-provider` requires `DEEPSEEK_API_KEY` in env.
- Default CLI provider remains `fake`.

Workflow tests must not use CLI for successful real-provider path. They must call `runOrganizeWorkflow` with injected provider dependencies:

```ts
runOrganizeWorkflow({
  providerRuntime: { provider: "deepseek-real", ... },
  providerRuntimeDependencies: {
    env: { TEST_DEEPSEEK_API_KEY: "test-api-key" },
    fetch: fakeFetch
  }
})
```

`providerRuntimeDependencies` must not be stored in LangGraph state or artifacts.

## Acceptance

- Unit test verifies DeepSeek adapter POSTs to `${baseUrl}/chat/completions`.
- Unit test verifies Authorization header is set but never logged.
- Unit test verifies OpenAI-compatible response maps to `LlmNoteProviderResult`.
- CLI smoke without env remains `SKIPPED` and `realExternalCall=false`.
- DeepSeek adapter raw envelope capture redacts Authorization before exposing request data.
- Real smoke with store writes redacted raw request/response artifacts and raw_ref trace.
- Provider registry can select `deepseek-real` with fake env/fetch and without storing API key in config.
- CLI blocks `deepseek-real` unless `--allow-real-provider` is present.
- Workflow can run `deepseek-real` using injected fake env/fetch without network.
- Full verification passes without API key and without network.
