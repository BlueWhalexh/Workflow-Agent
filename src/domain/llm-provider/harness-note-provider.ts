import { ProviderRuntimeError, type ProviderErrorClass } from "./provider-error.js";
import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

export function createFailingNoteProvider(errorClass: ProviderErrorClass): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      throw new ProviderRuntimeError(errorClass, `${input.workItem.id} ${errorClass}`);
    }
  };
}

export function createInvalidContentNoteProvider(): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      return {
        providerCallId: `${input.workItem.id}:invalid-content-fixture`,
        provider: "fake",
        model: "invalid-content-fixture",
        finishReason: "stop",
        usage: {
          inputTokens: 1,
          outputTokens: 1,
          totalTokens: 2
        },
        content: "# Invalid Provider Output\n\ncontentSha: pending\n"
      };
    }
  };
}

export function createWeakRelationsNoteProvider(): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      const sourcePath = input.workItem.sourcePaths[0];
      return {
        providerCallId: `${input.workItem.id}:weak-relations-fixture`,
        provider: "fake",
        model: "weak-relations-fixture",
        finishReason: "stop",
        usage: {
          inputTokens: 1,
          outputTokens: 1,
          totalTokens: 2
        },
        content: `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

这篇 note 用于验证 agent node 会执行确定性质量自检。

## 来源追踪

- ${sourcePath}

## 关键决策

- 缺失的低风险质量段落应由 agent loop 修复。
`
      };
    }
  };
}
