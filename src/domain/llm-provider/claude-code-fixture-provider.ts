import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

export interface ClaudeCodeFixtureResult {
  type: "result";
  subtype: string;
  result?: string;
  session_id?: string;
  usage?: {
    input_tokens?: number;
    output_tokens?: number;
  };
  total_cost_usd?: number;
  stop_reason?: string | null;
}

const DEFAULT_CLAUDE_CODE_FIXTURE_RESULT: ClaudeCodeFixtureResult = {
  type: "result",
  subtype: "success",
  result: `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

Claude Code fixture 生成结构化 note。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Agent SDK 只能作为 provider adapter。

## 相关链接

暂无相关链接。
`,
  session_id: "fixture-session",
  usage: {
    input_tokens: 20,
    output_tokens: 8
  },
  total_cost_usd: 0.02,
  stop_reason: "end_turn"
};

export function createClaudeCodeFixtureNoteProvider(
  result: ClaudeCodeFixtureResult = DEFAULT_CLAUDE_CODE_FIXTURE_RESULT
): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      const inputTokens = result.usage?.input_tokens;
      const outputTokens = result.usage?.output_tokens;
      return {
        providerCallId: `${input.workItem.id}:claude-code-fixture`,
        provider: "claude-code",
        model: "claude-code-fixture",
        finishReason: result.stop_reason ?? result.subtype,
        usage: {
          inputTokens,
          outputTokens,
          totalTokens: inputTokens !== undefined && outputTokens !== undefined ? inputTokens + outputTokens : undefined,
          costUsd: result.total_cost_usd
        },
        content: result.result ?? ""
      };
    }
  };
}
