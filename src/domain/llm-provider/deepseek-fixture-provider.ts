import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

export interface DeepSeekFixtureResponse {
  model: string;
  choices: Array<{
    finish_reason: string | null;
    message: {
      role: string;
      content?: string | null;
      reasoning_content?: string | null;
    };
  }>;
  usage?: {
    prompt_tokens?: number;
    completion_tokens?: number;
    total_tokens?: number;
    completion_tokens_details?: {
      reasoning_tokens?: number;
    };
  };
}

const DEFAULT_DEEPSEEK_FIXTURE_RESPONSE: DeepSeekFixtureResponse = {
  model: "deepseek-fixture",
  choices: [
    {
      finish_reason: "stop",
      message: {
        role: "assistant",
        content: `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

DeepSeek fixture 生成结构化 note。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Provider 只能作为 adapter。

## 相关链接

暂无相关链接。
`,
        reasoning_content: "validated fixture structure"
      }
    }
  ],
  usage: {
    prompt_tokens: 11,
    completion_tokens: 7,
    total_tokens: 18,
    completion_tokens_details: {
      reasoning_tokens: 3
    }
  }
};

export function createDeepSeekFixtureNoteProvider(
  response: DeepSeekFixtureResponse = DEFAULT_DEEPSEEK_FIXTURE_RESPONSE
): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      const choice = response.choices[0];
      return {
        providerCallId: `${input.workItem.id}:deepseek-fixture`,
        provider: "deepseek",
        model: response.model,
        finishReason: choice?.finish_reason ?? null,
        usage: {
          inputTokens: response.usage?.prompt_tokens,
          outputTokens: response.usage?.completion_tokens,
          totalTokens: response.usage?.total_tokens,
          reasoningTokens: response.usage?.completion_tokens_details?.reasoning_tokens
        },
        content: choice?.message.content ?? ""
      };
    }
  };
}
