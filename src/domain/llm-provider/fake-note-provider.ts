import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

function renderFakeNote(input: LlmNoteProviderInput): string {
  const sourcePath = input.workItem.sourcePaths[0];
  const sourceSha = input.workItem.baseShas[sourcePath] ?? "unknown-source-sha";

  return `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - ${sourcePath}
sourceShas:
  ${sourcePath}: ${sourceSha}
lastRunId: ${input.runId}
contentSha: pending
-->

## 摘要

这篇 note 沉淀 skill 与 CLI tool 的适用边界，帮助后续判断能力应进入流程指导还是确定性工具。

## 来源追踪

- ${sourcePath}

## 关键决策

- Skill 适合流程、判断标准、上下文组织和人机协作约束。
- CLI tool 适合可重复、确定性、可测试、可组合的机械动作。
- Agent loop 不能只依赖 prompt，关键写入和质量边界必须由确定性代码验证。

## 取舍

Skill 更容易表达意图，但不能替代验证。CLI 更适合进入 CI 和本地自动化，但不适合承载模糊判断。

## 相关链接

暂无相关链接。
`;
}

export function createFakeNoteProvider(): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      return {
        providerCallId: `${input.workItem.id}:fake-note`,
        provider: "fake",
        model: "fake-note-model",
        finishReason: "stop",
        usage: {
          inputTokens: 1,
          outputTokens: 1,
          totalTokens: 2
        },
        content: renderFakeNote(input)
      };
    }
  };
}
