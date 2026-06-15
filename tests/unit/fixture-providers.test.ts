import { describe, expect, it } from "vitest";
import { createClaudeCodeFixtureNoteProvider } from "../../src/domain/llm-provider/claude-code-fixture-provider.js";
import { createDeepSeekFixtureNoteProvider } from "../../src/domain/llm-provider/deepseek-fixture-provider.js";
import { createMimoVllmFixtureNoteProvider } from "../../src/domain/llm-provider/mimo-vllm-fixture-provider.js";
import { redactProviderEnvelope } from "../../src/domain/llm-provider/redaction.js";
import type { WorkItem } from "../../src/domain/planning/work-item.js";

const workItem: WorkItem = {
  id: "rewrite-tools",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/tools/Skill vs CLI Tool 决策.md"],
  targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  baseShas: {
    "raw/tools/Skill vs CLI Tool 决策.md": "source-sha"
  },
  risk: "LOW",
  requiresApproval: false,
  reason: "fixture",
  attempts: [],
  publishPolicy: "AUTO_PUBLISH"
};

const validNote = `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

Fixture provider 生成结构化 note。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Provider 只能作为 adapter。

## 相关链接

暂无相关链接。
`;

describe("fixture providers", () => {
  it("maps DeepSeek fixture response to note provider result", async () => {
    const provider = createDeepSeekFixtureNoteProvider({
      model: "deepseek-chat",
      choices: [
        {
          finish_reason: "stop",
          message: {
            role: "assistant",
            content: validNote,
            reasoning_content: "checked structure"
          }
        }
      ],
      usage: {
        prompt_tokens: 11,
        completion_tokens: 7,
        total_tokens: 18,
        completion_tokens_details: { reasoning_tokens: 3 }
      }
    });

    const result = await provider.generateNote({
      runId: "run-fixture",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("deepseek");
    expect(result.model).toBe("deepseek-chat");
    expect(result.finishReason).toBe("stop");
    expect(result.usage).toEqual({ inputTokens: 11, outputTokens: 7, totalTokens: 18, reasoningTokens: 3 });
    expect(result.content).toContain("state: AGENT_ORGANIZED");
  });

  it("maps Claude Code fixture result to note provider result", async () => {
    const provider = createClaudeCodeFixtureNoteProvider({
      type: "result",
      subtype: "success",
      result: validNote,
      session_id: "session-fixture",
      usage: { input_tokens: 20, output_tokens: 8 },
      total_cost_usd: 0.02,
      stop_reason: "end_turn"
    });

    const result = await provider.generateNote({
      runId: "run-fixture",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("claude-code");
    expect(result.model).toBe("claude-code-fixture");
    expect(result.finishReason).toBe("end_turn");
    expect(result.usage).toEqual({ inputTokens: 20, outputTokens: 8, totalTokens: 28, costUsd: 0.02 });
    expect(result.content).toContain("## 来源追踪");
  });

  it("maps MiMo vLLM fixture output to note provider result", async () => {
    const provider = createMimoVllmFixtureNoteProvider({
      model: "XiaomiMiMo/MiMo-7B-RL-0530",
      generated_text: validNote,
      finish_reason: "stop",
      prompt_token_ids: [1, 2, 3],
      output_token_ids: [4, 5]
    });

    const result = await provider.generateNote({
      runId: "run-fixture",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("mimo-vllm");
    expect(result.model).toBe("XiaomiMiMo/MiMo-7B-RL-0530");
    expect(result.finishReason).toBe("stop");
    expect(result.usage).toEqual({ inputTokens: 3, outputTokens: 2, totalTokens: 5 });
    expect(result.content).toContain("state: AGENT_ORGANIZED");
  });

  it("redacts auth-like fields from provider envelopes", () => {
    const redacted = redactProviderEnvelope({
      Authorization: "Bearer secret",
      api_key: "secret",
      access_token: "secret",
      prompt_tokens: 12,
      nested: {
        cookie: "session=secret",
        safe: "kept"
      }
    });

    expect(redacted).toEqual({
      Authorization: "[REDACTED]",
      api_key: "[REDACTED]",
      access_token: "[REDACTED]",
      prompt_tokens: 12,
      nested: {
        cookie: "[REDACTED]",
        safe: "kept"
      }
    });
  });
});
