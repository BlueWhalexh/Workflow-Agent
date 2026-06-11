export type LlmTraceProvider =
  | "anthropic"
  | "claude-code"
  | "openai-compatible"
  | "deepseek"
  | "mimo-api"
  | "mimo-vllm"
  | "mimo-sglang"
  | "fake";

export type LlmAgentNode = "note" | "topic-index" | "moc" | "quality-review";

export interface LlmTraceBase {
  schemaVersion: "llm-trace.v1";
  eventId: string;
  runId: string;
  workItemId: string;
  agentNode: LlmAgentNode;
  providerCallId: string;
  provider: LlmTraceProvider;
  model: string;
  timestamp: string;
}

export interface LlmCallStarted extends LlmTraceBase {
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

export interface LlmCallCompleted extends LlmTraceBase {
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

export interface LlmToolCall extends LlmTraceBase {
  type: "llm.tool.call";
  toolCallId: string;
  toolName: string;
  argumentsSha: string;
  argumentsPreview: string;
}

export interface LlmToolResult extends LlmTraceBase {
  type: "llm.tool.result";
  toolCallId: string;
  status: "ok" | "error" | "denied";
  resultSha: string;
  resultPreview: string;
}

export interface LlmStreamDelta extends LlmTraceBase {
  type: "llm.stream.delta";
  deltaKind: "text" | "reasoning" | "tool_input" | "unknown";
  textSha: string;
  charCount: number;
}

export interface LlmCallFailed extends LlmTraceBase {
  type: "llm.call.failed";
  errorClass: "timeout" | "rate_limit" | "auth" | "provider" | "network" | "schema" | "unknown";
  retryable: boolean;
  message: string;
}

export interface LlmCompaction extends LlmTraceBase {
  type: "llm.context.compacted";
  trigger: "manual" | "auto" | "provider";
  beforeTokens?: number;
  afterTokens?: number;
  summarySha: string;
}

export interface LlmProviderRawRef extends LlmTraceBase {
  type: "llm.provider.raw_ref";
  requestPath?: string;
  responsePath?: string;
  redaction: "required" | "applied" | "not-stored";
}

export type LlmTraceEvent =
  | LlmCallStarted
  | LlmCallCompleted
  | LlmToolCall
  | LlmToolResult
  | LlmStreamDelta
  | LlmCallFailed
  | LlmCompaction
  | LlmProviderRawRef;
