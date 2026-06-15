# Open Agent Provider-backed Graph Spec

> 状态：Phase 32 candidate spec。目标是把 Phase 31 的 `OpenAgentGraph` 从 deterministic provider 骨架升级为 provider-backed graph：真实 MiMo/DeepSeek/OpenAI-compatible provider 直接参与 plan/action，而不是只在 smoke harness 前置调用。

## Problem

Phase 31 已经有 graph runner、policy gate、context gather、tool loop、synthesis、artifact/trace。但真实 MiMo smoke 的外部调用发生在 smoke harness 前置步骤，graph 本体仍使用 deterministic open-agent provider。

这说明真实链路只证明了 “MiMo API 可达 + graph artifact 可写”，还没证明 “graph 的 plan/action 能由真实 provider 驱动”。

## Goal

新增 OpenAI-compatible `OpenAgentProvider`：

```text
runOpenAgentGraph
  -> selectOpenAgentProvider(providerRuntime)
  -> provider.plan()
  -> context gather
  -> provider.nextAction()
  -> synthesize/self-check/artifact
```

Provider-backed graph 必须：

- 支持 `mimo-real` 和 `deepseek-real`；
- 使用 env-only API key；
- 对 raw request/response 做 redaction；
- 把 raw provider refs 写入 `.agent-runs/open-agent/raw-provider/...`；
- provider parse 失败返回 `FAILED_VALIDATION`；
- provider HTTP/auth 失败返回 `FAILED_PROVIDER`；
- 不改变 open graph 的 no-publish 边界。

## Non-goals

- 不让 provider 生成 publishable `PatchBundle`。
- 不让 provider 直接调用 workspace write tool。
- 不引入新依赖。
- 不做 streaming。
- 不把 provider output 当成 chain-of-thought 存储。

## Provider Contract

`OpenAgentProvider.plan()` 请求 provider 只返回 JSON：

```json
{
  "objective": "string",
  "outputPolicy": "ANSWER_ONLY",
  "steps": ["scan workspace", "read context", "answer"],
  "contextHints": ["raw", "knowledge-base"]
}
```

`OpenAgentProvider.nextAction()` 请求 provider 只返回 JSON：

```json
{
  "action": "SOLVED",
  "summary": "Enough context gathered."
}
```

Allowed actions:

- `READ_CONTEXT`
- `SOLVED`
- `REQUEST_WRITE_CONFIRMATION`

If provider wraps JSON in ```json fences, parser may strip fences. If no valid JSON remains, graph returns `FAILED_VALIDATION`.

## Raw Envelope Contract

Graph-owned raw refs:

```text
.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/request.json
.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/response.json
```

Rules:

- `Authorization` must be `[REDACTED]`。
- API key must not appear in report、trace、raw artifacts、stdout/stderr。
- raw refs are audit/eval evidence only, not publish/resume truth source。

## Acceptance

- Unit tests cover provider request mapping and JSON parse.
- Unit tests cover markdown-fenced JSON parse.
- Unit tests cover invalid JSON -> `FAILED_VALIDATION`.
- Unit tests cover injected MiMo provider-backed graph with two provider calls.
- Real MiMo `--mode llm-graph` smoke shows graph provider calls come from MiMo adapter.
- Full tests/typecheck/diff pass.
