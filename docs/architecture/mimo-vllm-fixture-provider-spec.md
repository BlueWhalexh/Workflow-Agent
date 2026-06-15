# MiMo vLLM Fixture Provider Spec

> 状态：Phase 16 spec。目标是先提供无需 token、无需真实网络的 MiMo-compatible provider runtime，用于验证 agent loop 的多 provider 边界。本文不声明真实小米 MiMo API 已接通。

## Current Public Facts

- 小米 MiMo 官网提供 Web Demo 和 API Access 入口，但当前公开页面没有给出稳定可直接实现的 API schema。
- 官方 `XiaomiMiMo/MiMo` GitHub 仓库发布 MiMo-7B 系列模型，并给出 SGLang、vLLM、HuggingFace 本地推理路径。
- 官方 GitHub 的 vLLM 示例把生成结果读作 `output.outputs[0].text`，本项目已有 `normalizeMimoVllmOutput` 用 `generated_text`、`finish_reason`、`prompt_token_ids`、`output_token_ids` 表达可审计的本地推理 envelope。

参考来源：

- `https://mimo.xiaomi.com/`
- `https://github.com/XiaomiMiMo/MiMo`
- `https://arxiv.org/abs/2505.07608`

## Goal

新增 `mimo-vllm-fixture` provider runtime，使没有 DeepSeek token 或 MiMo API token 的环境也能执行完整 organize workflow：

```text
CLI --provider mimo-vllm-fixture
  -> ProviderRegistry
  -> MiMo vLLM fixture note provider
  -> runMockNoteAgent
  -> PatchBundle / MergeGuard / Validator / Publisher
  -> llm trace provider = "mimo-vllm"
```

## Non-Goals

- 不调用真实 `mimo.xiaomi.com` API。
- 不启动本地 vLLM/SGLang 服务。
- 不下载模型权重。
- 不引入新依赖。
- 不把 fixture 结果描述为真实 MiMo 模型输出。

## Runtime Contract

`ProviderRuntimeName` 增加：

```ts
"mimo-vllm-fixture"
```

`LlmNoteProviderResult` 映射：

| Field | Value |
| --- | --- |
| `providerCallId` | `<workItemId>:mimo-vllm-fixture` |
| `provider` | `"mimo-vllm"` |
| `model` | fixture output model，默认 `XiaomiMiMo/MiMo-7B-RL-0530` |
| `finishReason` | `finish_reason ?? null` |
| `usage.inputTokens` | `prompt_token_ids.length` |
| `usage.outputTokens` | `output_token_ids.length` |
| `usage.totalTokens` | input + output |
| `content` | `generated_text ?? ""` |

默认 fixture content 必须是通过 Validator 的 topic note，保证 workflow 可以稳定验收。

## Acceptance

- unit: MiMo fixture output maps to `LlmNoteProviderResult`。
- unit: Provider registry can select `mimo-vllm-fixture`。
- integration: LangGraph workflow with `providerRuntime.provider = "mimo-vllm-fixture"` succeeds without env/token/network。
- integration: CLI `--provider mimo-vllm-fixture` succeeds without `--allow-real-provider`。
- full: `npm test`、`npm run typecheck`、`git diff --check` 通过。

## Upgrade Conditions

后续只有满足以下条件，才升级到真实 MiMo provider：

- 有稳定官方 API schema 或确定采用 OpenAI-compatible third-party endpoint。
- 用户明确提供 token/env 名称和允许真实外部调用。
- adapter 有 injected fetch/env 测试，不把 API key 写入 config、trace、raw artifact。
- 真实调用仍然默认跳过，只能通过显式 opt-in smoke 触发。
