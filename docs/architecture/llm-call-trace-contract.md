# LLM Call Trace Contract

> 状态：接真实 provider 前的记录契约。本文定义 `.agent-runs/<runId>/traces/*.jsonl` 的稳定格式，避免后续只适配某一家 SDK 的日志形态。

## Goal

LLM trace 必须同时满足三件事：

- 支持 agent node 内部多步推理、tool loop、self-check、revise 的可审计记录。
- 支持 Claude/Claude Code 风格、OpenAI-compatible 风格、DeepSeek thinking/tool 风格、小米 MiMo API 或本地 vLLM/SGLang 风格。
- 不让 provider raw response 成为领域事实源。恢复、发布和校验仍以 `.agent-runs` artifacts、workspace current sha、Validator 为准。

## Source Facts Checked

已在 2026-06-11 检查以下公开资料，用于确定兼容边界：

- Claude Code Agent SDK 会产生 `SystemMessage`、`AssistantMessage`、`UserMessage`、`StreamEvent`、`ResultMessage`，最终 result 带 `usage`、cost、session id、stop reason。Claude Code 还支持将 session transcript mirror 到外部 `SessionStore`。
- Claude/Anthropic tool use 以 `tool_use` / `tool_result` content block 表达；streaming 可暴露 raw stream events。
- DeepSeek API 明确兼容 OpenAI/Anthropic API 格式；Chat Completion response 包含 `message.content`、`message.reasoning_content`、`tool_calls`、`finish_reason`、`usage`、`reasoning_tokens`，streaming chunk 也可能带 `reasoning_content`。
- 小米 MiMo 官方站点提供 API Access；开源 MiMo-7B 文档也给出 vLLM/SGLang/HuggingFace inference 形态。因此第一版不能假设只有 SaaS API，也要支持 local engine raw envelope。

## File Layout

```text
.agent-runs/<runId>/
  traces/
    <workItemId>.jsonl
    <workItemId>.raw/
      <providerCallId>.request.redacted.json
      <providerCallId>.response.redacted.json
```

`<workItemId>.jsonl` 是 canonical trace。`raw/` 是调试附件，不参与 resume 决策。

## Canonical JSONL Event

每一行是一个 JSON object：

```ts
type LlmTraceEvent =
  | LlmCallStarted
  | LlmStreamDelta
  | LlmToolCall
  | LlmToolResult
  | LlmCallCompleted
  | LlmCallFailed
  | LlmCompaction
  | LlmProviderRawRef;
```

公共字段：

```ts
interface LlmTraceBase {
  schemaVersion: "llm-trace.v1";
  eventId: string;
  runId: string;
  workItemId: string;
  agentNode: "note" | "topic-index" | "moc" | "quality-review";
  providerCallId: string;
  provider:
    | "anthropic"
    | "claude-code"
    | "openai-compatible"
    | "deepseek"
    | "mimo-api"
    | "mimo-vllm"
    | "mimo-sglang"
    | "fake";
  model: string;
  timestamp: string;
}
```

### Required Events

```ts
interface LlmCallStarted extends LlmTraceBase {
  type: "llm.call.started";
  request: {
    messagesSha: string;
    systemSha?: string;
    toolSchemaSha?: string;
    temperature?: number;
    maxTokens?: number;
    reasoningEffort?: "low" | "medium" | "high" | "max";
    thinkingEnabled?: boolean;
  };
}

interface LlmStreamDelta extends LlmTraceBase {
  type: "llm.stream.delta";
  deltaKind: "text" | "reasoning" | "tool_input" | "unknown";
  textSha: string;
  charCount: number;
}

interface LlmToolCall extends LlmTraceBase {
  type: "llm.tool.call";
  toolCallId: string;
  toolName: string;
  argumentsSha: string;
  argumentsPreview: string;
}

interface LlmToolResult extends LlmTraceBase {
  type: "llm.tool.result";
  toolCallId: string;
  status: "ok" | "error" | "denied";
  resultSha: string;
  resultPreview: string;
}

interface LlmCallCompleted extends LlmTraceBase {
  type: "llm.call.completed";
  finishReason: string | null;
  outputTextSha?: string;
  reasoningTextSha?: string;
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
    reasoningTokens?: number;
    cacheReadTokens?: number;
    cacheWriteTokens?: number;
    totalTokens?: number;
    costUsd?: number;
  };
}

interface LlmCallFailed extends LlmTraceBase {
  type: "llm.call.failed";
  errorClass: "timeout" | "rate_limit" | "auth" | "provider" | "network" | "schema" | "unknown";
  retryable: boolean;
  message: string;
}

interface LlmCompaction extends LlmTraceBase {
  type: "llm.context.compacted";
  trigger: "manual" | "auto" | "provider";
  beforeTokens?: number;
  afterTokens?: number;
  summarySha: string;
}

interface LlmProviderRawRef extends LlmTraceBase {
  type: "llm.provider.raw_ref";
  requestPath?: string;
  responsePath?: string;
  redaction: "required" | "applied" | "not-stored";
}
```

## Provider Mapping

| Provider shape | Adapter rule |
| --- | --- |
| Claude Code Agent SDK | Map `SystemMessage` to `llm.call.started`, `AssistantMessage` content blocks to text/tool events, `UserMessage` tool results to `llm.tool.result`, `StreamEvent` to `llm.stream.delta`, `ResultMessage` to `llm.call.completed`. Preserve `session_id` only in raw ref metadata, not as resume source. |
| Anthropic Messages API | Map `tool_use` to `llm.tool.call`, `tool_result` to `llm.tool.result`, `usage` to canonical usage. Extended thinking must be recorded as reasoning SHA/length, not full private chain unless explicitly allowed. |
| OpenAI-compatible | Map chat/completions `messages`, `choices[].message.content`, `tool_calls`, `finish_reason`, `usage`. Streaming chunks become `llm.stream.delta`. |
| DeepSeek | Same as OpenAI-compatible plus `thinking`, `reasoning_effort`, `reasoning_content`, `completion_tokens_details.reasoning_tokens`, and provider `finish_reason` values such as `insufficient_system_resource`. |
| 小米 MiMo API | Treat as provider-specific until official response schema is pinned. If API is OpenAI-compatible, use OpenAI-compatible adapter with provider `mimo-api`; otherwise store redacted raw request/response and map only known content/tool/usage fields. |
| 小米 MiMo local vLLM/SGLang | Record engine as `mimo-vllm` or `mimo-sglang`. Map `prompt`, generated text, sampling params, token stats when available. If engine lacks tool-call semantics, `tool_call` events are absent and agent node must enforce structured output separately. |

## Redaction Rules

- 不写入 API key、cookie、Authorization header、账号、用户隐私原文。
- 默认只保存 prompt/output 的 SHA 和短 preview；完整 prompt/output 只有在 fixture、mock、或用户明确允许的开发环境中保存。
- `raw/*.json` 必须是 redacted 后的 provider envelope。
- trace 可以用于 debug 和 eval，但不能绕过 Validator。

## Resume Boundary

Trace 只回答“agent node 做过什么”。Resume 决策必须继续使用：

```text
.agent-runs artifacts
workspace current sha
PatchBundle contentSha
Validator result
```

LLM trace 不能单独证明 patch 已发布，也不能证明 workspace 当前仍安全。

## First Implementation Slice

下一步只实现：

- canonical trace type 和 JSONL writer。
- fake provider trace fixture。
- DeepSeek/OpenAI-compatible normalizer 的纯函数测试。
- Claude Code event normalizer 的纯函数测试。
- MiMo local engine raw envelope normalizer 的纯函数测试。
- mock note agent 写 trace，但仍不调用真实 provider。

真实 provider smoke 必须等上述 contract tests 通过后单独计划。
