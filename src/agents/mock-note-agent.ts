import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import { sha256 } from "../storage/sha.js";
import type { WorkItemAgentInput } from "./work-item-agent.js";

export async function runMockNoteAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const sourcePath = input.workItem.sourcePaths[0];
  const sourceSha = input.workItem.baseShas[sourcePath] ?? "unknown-source-sha";
  const baseSha = input.workItem.baseShas[targetPath] ?? null;
  const content = `# Skill vs CLI Tool 决策

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
  const contentSha = sha256(content);
  const finalizedContent = content.replace("contentSha: pending", `contentSha: ${contentSha}`);

  return {
    workItemId: input.workItem.id,
    status: "SUCCEEDED",
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: baseSha ? "MODIFIED" : "CREATED",
        baseSha,
        contentSha: sha256(finalizedContent),
        content: finalizedContent
      }
    ],
    eval: {
      rawFilesSeen: [sourcePath],
      rawMirrorConverted: input.workItem.type === "REWRITE_TOPIC_NOTE",
      placeholderIntroduced: false,
      wikilinksCreated: 0
    }
  };
}
