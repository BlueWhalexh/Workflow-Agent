import { sha256 } from "../../storage/sha.js";
import type { LlmCallCompleted } from "./trace-event.js";

interface NormalizerBase {
  runId: string;
  workItemId: string;
  providerCallId: string;
  timestamp: string;
}

export function normalizeClaudeCodeResult(input: NormalizerBase & {
  model: string;
  message: {
    type: "result";
    subtype: string;
    result?: string;
    session_id?: string;
    usage?: { input_tokens?: number; output_tokens?: number };
    total_cost_usd?: number;
    stop_reason?: string | null;
  };
}): LlmCallCompleted {
  return {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${input.providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: "claude-code",
    model: input.model,
    timestamp: input.timestamp,
    finishReason: input.message.stop_reason ?? input.message.subtype,
    outputTextSha: input.message.result ? sha256(input.message.result) : undefined,
    usage: {
      inputTokens: input.message.usage?.input_tokens,
      outputTokens: input.message.usage?.output_tokens,
      costUsd: input.message.total_cost_usd
    }
  };
}

export function normalizeDeepSeekChatCompletion(input: NormalizerBase & {
  response: {
    model: string;
    choices: Array<{
      finish_reason: string | null;
      message: {
        content?: string | null;
        reasoning_content?: string | null;
        role: string;
      };
    }>;
    usage?: {
      prompt_tokens?: number;
      completion_tokens?: number;
      total_tokens?: number;
      completion_tokens_details?: { reasoning_tokens?: number };
    };
  };
}): LlmCallCompleted {
  const choice = input.response.choices[0];
  const output = choice?.message.content ?? undefined;
  const reasoning = choice?.message.reasoning_content ?? undefined;
  return {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${input.providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: "deepseek",
    model: input.response.model,
    timestamp: input.timestamp,
    finishReason: choice?.finish_reason ?? null,
    outputTextSha: output ? sha256(output) : undefined,
    reasoningTextSha: reasoning ? sha256(reasoning) : undefined,
    usage: {
      inputTokens: input.response.usage?.prompt_tokens,
      outputTokens: input.response.usage?.completion_tokens,
      totalTokens: input.response.usage?.total_tokens,
      reasoningTokens: input.response.usage?.completion_tokens_details?.reasoning_tokens
    }
  };
}

export function normalizeMimoVllmOutput(input: NormalizerBase & {
  model: string;
  output: {
    generated_text?: string;
    finish_reason?: string | null;
    prompt_token_ids?: number[];
    output_token_ids?: number[];
  };
}): LlmCallCompleted {
  const inputTokens = input.output.prompt_token_ids?.length;
  const outputTokens = input.output.output_token_ids?.length;
  return {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${input.providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: "mimo-vllm",
    model: input.model,
    timestamp: input.timestamp,
    finishReason: input.output.finish_reason ?? null,
    outputTextSha: input.output.generated_text ? sha256(input.output.generated_text) : undefined,
    usage: {
      inputTokens,
      outputTokens,
      totalTokens: inputTokens !== undefined && outputTokens !== undefined ? inputTokens + outputTokens : undefined
    }
  };
}
