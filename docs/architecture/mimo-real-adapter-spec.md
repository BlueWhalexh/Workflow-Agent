# MiMo Real Adapter Spec

> 状态：Phase 17 spec。目标是把小米 MiMo OpenAI-compatible endpoint 接入 provider runtime，但保持真实调用显式 opt-in。本文不记录任何 API key、token、cookie 或用户凭证。

## Endpoint Contract

当前用户提供的 MiMo endpoint base URL：

```text
https://token-plan-cn.xiaomimimo.com/v1
```

真实 smoke 验收记录：

- `/v1/models` 返回当前 token 可用模型：`mimo-v2-pro`、`mimo-v2.5`、`mimo-v2.5-pro` 等。
- 使用旧 fixture 模型名 `XiaomiMiMo/MiMo-7B-RL-0530` 调用真实 API 返回 HTTP 400：`Not supported model ...`。
- 使用 `mimo-v2.5` 执行 `mimo-real-smoke` 返回 `PASSED`，`realExternalCall: true`。
- 本文不记录 API key/token 值。

本阶段按 OpenAI-compatible chat completions 适配：

```text
POST <baseUrl>/chat/completions
Authorization: Bearer <MIMO_API_KEY>
Content-Type: application/json
```

Request body：

```json
{
  "model": "<MIMO_MODEL>",
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user", "content": "..." }
  ],
  "stream": false,
  "max_tokens": 1024,
  "temperature": 0
}
```

Response body 按 OpenAI-compatible schema 读取：

- `model`
- `choices[0].finish_reason`
- `choices[0].message.content`
- `choices[0].message.reasoning_content` if present
- `usage.prompt_tokens`
- `usage.completion_tokens`
- `usage.total_tokens`

## Runtime Names

- `mimo-real`: workflow runtime provider。
- `mimo-real-smoke`: optional real external smoke provider。
- `mimo-vllm-fixture`: 仍保留为无 token fixture/local-compatible provider。

## Secret Rules

- API key 只允许从 `MIMO_API_KEY` 或配置的 `apiKeyEnvName` 读取。
- 不允许把 API key 写入 `ProviderRuntimeConfig`、`GraphState`、trace、raw envelope、测试 fixture、文档或报告。
- raw envelope 必须经过 redaction，并只在 opt-in smoke 且提供 `AgentRunsStore` 时写入 artifact。
- CLI 必须要求 `--allow-real-provider` 才能使用 `--provider mimo-real`。

## Goal

让用户在本地设置 env 后可以运行：

```bash
MIMO_API_KEY=... \
MIMO_BASE_URL=https://token-plan-cn.xiaomimimo.com/v1 \
MIMO_MODEL=<model> \
node --import tsx src/cli/organize.ts <workspaceRoot> "整理全部知识库" \
  --auto-approve \
  --provider mimo-real \
  --allow-real-provider
```

或通过 stdin 传入 API key，避免 token 出现在 shell history / process args：

```bash
printf '%s' "$MIMO_API_KEY" | \
node --import tsx src/cli/provider-smoke.ts \
  --provider mimo-real-smoke \
  --execute-real \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5
```

默认 test suite 不需要这些 env，也不会发起真实外部调用。

## Non-Goals

- 不在测试里执行真实 MiMo API 调用。
- 不把 fixture/open-source 模型名当成真实 API 模型名；真实 API 默认模型使用 smoke 验证过的 `mimo-v2.5`。
- 不实现 streaming。
- 不实现 provider fallback、rate limit queue 或 long-running local vLLM server。

## Acceptance

- unit: `createMimoRealNoteProvider` 用 injected fake fetch 构建 `/chat/completions` request，并映射 OpenAI-compatible response。
- unit: MiMo raw envelope hook 只收到 redacted payload。
- unit: `mimo-real-smoke` 缺 env 时 skip，不执行真实外部调用。
- unit: `mimo-real-smoke` 用 injected fake fetch + explicit `executeReal=true` 返回 passed。
- unit: raw envelope artifacts + `llm.provider.raw_ref` 可写入 store。
- unit: Provider registry 选择 `mimo-real` 时只从 injected env 或 `process.env` 读取 key；缺 key 时抛 `ProviderRuntimeError("auth", "MISSING_MIMO_API_KEY", false)`。
- integration: workflow 可用 injected env/fetch 跑通 `mimo-real`，不触发真实网络。
- integration: CLI `--provider mimo-real` 未加 `--allow-real-provider` 时阻断。
- CLI: `provider-smoke --api-key-stdin --base-url ... --model ...` 可构造完整 env，但不打印 API key。
- full: `npm test`、`npm run typecheck`、`git diff --check` 通过。

## Upgrade Conditions

后续如果要宣称真实 MiMo 链路跑通，必须单独执行 opt-in smoke，并在报告里标注：

- real external call = true。
- 使用的 env 名称，不记录 env 值。
- HTTP status / provider result。
- raw artifacts 已 redacted。
