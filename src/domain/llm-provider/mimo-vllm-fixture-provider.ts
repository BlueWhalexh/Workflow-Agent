import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

export interface MimoVllmFixtureOutput {
  model: string;
  generated_text?: string;
  finish_reason?: string | null;
  prompt_token_ids?: number[];
  output_token_ids?: number[];
}

const DEFAULT_MIMO_VLLM_FIXTURE_OUTPUT: MimoVllmFixtureOutput = {
  model: "XiaomiMiMo/MiMo-7B-RL-0530",
  generated_text: `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

MiMo vLLM fixture 生成结构化 note。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Provider 只能作为 adapter。

## 相关链接

暂无相关链接。
`,
  finish_reason: "stop",
  prompt_token_ids: [1, 2, 3, 4],
  output_token_ids: [5, 6, 7]
};

export function createMimoVllmFixtureNoteProvider(
  output: MimoVllmFixtureOutput = DEFAULT_MIMO_VLLM_FIXTURE_OUTPUT
): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      const inputTokens = output.prompt_token_ids?.length;
      const outputTokens = output.output_token_ids?.length;
      return {
        providerCallId: `${input.workItem.id}:mimo-vllm-fixture`,
        provider: "mimo-vllm",
        model: output.model,
        finishReason: output.finish_reason ?? null,
        usage: {
          inputTokens,
          outputTokens,
          totalTokens: inputTokens !== undefined && outputTokens !== undefined ? inputTokens + outputTokens : undefined
        },
        content: output.generated_text ?? ""
      };
    }
  };
}
