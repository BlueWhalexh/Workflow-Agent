import { describe, expect, it } from "vitest";
import {
  normalizeClaudeCodeResult,
  normalizeDeepSeekChatCompletion,
  normalizeMimoVllmOutput
} from "../../src/domain/llm-trace/provider-normalizers.js";

describe("provider normalizers", () => {
  it("maps Claude Code result message to canonical completion", () => {
    const event = normalizeClaudeCodeResult({
      runId: "run-a",
      workItemId: "work-a",
      providerCallId: "call-a",
      timestamp: "2026-06-11T00:00:00.000Z",
      model: "claude-sonnet-4-6",
      message: {
        type: "result",
        subtype: "success",
        result: "done",
        session_id: "session-a",
        usage: { input_tokens: 10, output_tokens: 5 },
        total_cost_usd: 0.01,
        stop_reason: "end_turn"
      }
    });

    expect(event).toMatchObject({
      type: "llm.call.completed",
      provider: "claude-code",
      finishReason: "end_turn",
      usage: { inputTokens: 10, outputTokens: 5, costUsd: 0.01 }
    });
  });

  it("maps DeepSeek reasoning and usage fields", () => {
    const event = normalizeDeepSeekChatCompletion({
      runId: "run-a",
      workItemId: "work-a",
      providerCallId: "call-b",
      timestamp: "2026-06-11T00:00:00.000Z",
      response: {
        model: "deepseek-v4-pro",
        choices: [
          {
            finish_reason: "stop",
            message: {
              content: "final",
              reasoning_content: "reasoning",
              role: "assistant"
            }
          }
        ],
        usage: {
          prompt_tokens: 11,
          completion_tokens: 7,
          total_tokens: 18,
          completion_tokens_details: { reasoning_tokens: 3 }
        }
      }
    });

    expect(event.provider).toBe("deepseek");
    expect(event.usage).toMatchObject({
      inputTokens: 11,
      outputTokens: 7,
      totalTokens: 18,
      reasoningTokens: 3
    });
    expect(event.reasoningTextSha).toBeDefined();
  });

  it("maps MiMo local vLLM generated text without tool calls", () => {
    const event = normalizeMimoVllmOutput({
      runId: "run-a",
      workItemId: "work-a",
      providerCallId: "call-c",
      timestamp: "2026-06-11T00:00:00.000Z",
      model: "XiaomiMiMo/MiMo-7B-RL-0530",
      output: {
        generated_text: "answer",
        finish_reason: "stop",
        prompt_token_ids: [1, 2],
        output_token_ids: [3, 4, 5]
      }
    });

    expect(event).toMatchObject({
      type: "llm.call.completed",
      provider: "mimo-vllm",
      model: "XiaomiMiMo/MiMo-7B-RL-0530",
      usage: { inputTokens: 2, outputTokens: 3, totalTokens: 5 }
    });
  });
});
