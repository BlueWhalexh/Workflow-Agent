import { describe, expect, it } from "vitest";
import { runNoteQualityLoop } from "../../src/agents/note-quality-loop.js";
import { getKnowledgeMethodology } from "../../src/domain/methodology/knowledge-methodology.js";

const baseNote = `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
contentSha: pending
-->

## 摘要

这篇 note 沉淀 skill 与 CLI tool 的适用边界。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Provider 输出必须通过确定性质量边界。
`;

describe("note quality loop", () => {
  it("repairs missing related-links section and records loop steps", () => {
    const result = runNoteQualityLoop({
      workItemId: "rewrite-tools",
      draftContent: baseNote
    });

    expect(result.content).toContain("## 相关链接");
    expect(result.report.repairedIssues).toEqual(["TOPIC_NOTE_WEAK_RELATIONS"]);
    expect(result.report.remainingIssues).toEqual([]);
    expect(result.report.steps.map((step) => step.name)).toEqual([
      "GENERATE_NOTE",
      "SELF_CHECK",
      "REPAIR_NOTE",
      "SELF_CHECK_AFTER_REPAIR"
    ]);
  });

  it("does not pretend to repair structural hard blockers", () => {
    const result = runNoteQualityLoop({
      workItemId: "invalid",
      draftContent: "# Invalid\n\ncontentSha: pending\n"
    });

    expect(result.content).toContain("# Invalid");
    expect(result.report.repairedIssues).toEqual([]);
    expect(result.report.remainingIssues).toEqual(["TOPIC_NOTE_WEAK_RELATIONS"]);
  });

  it("normalizes common real-provider heading variants before validation", () => {
    const result = runNoteQualityLoop({
      workItemId: "real-provider-heading-variants",
      draftContent: `# Go 基础语法

## 总结

Go 语言基础语法说明。

## 源追踪

- raw/go/Go 基础语法.md

## 关键内容

- package、func 和 error 是基础概念。
`
    });

    expect(result.content).toContain("## 摘要");
    expect(result.content).toContain("## 来源追踪");
    expect(result.content).toContain("## 关键概念");
    expect(result.content).toContain("## 相关链接");
    expect(result.content).not.toContain("## 总结");
    expect(result.content).not.toContain("## 源追踪");
    expect(result.content).not.toContain("## 关键内容");
    expect(result.report.repairedIssues).toContain("TOPIC_NOTE_NON_CANONICAL_HEADINGS");
  });

  it("normalizes source-file tracking headings emitted by real providers", () => {
    const result = runNoteQualityLoop({
      workItemId: "real-provider-source-file-heading",
      draftContent: `# Agent Loop 失败复盘

## 摘要

复盘内容。

## 源文件追踪

- raw/agent/Agent Loop 失败复盘.md

## 关键概念

- Work Item。

## 相关链接

- [[PatchBundle]]
`
    });

    expect(result.content).toContain("## 来源追踪");
    expect(result.content).not.toContain("## 源文件追踪");
    expect(result.report.repairedIssues).toContain("TOPIC_NOTE_NON_CANONICAL_HEADINGS");
  });

  it("removes placeholder related links instead of publishing fake completion", () => {
    const result = runNoteQualityLoop({
      workItemId: "placeholder-related-links",
      draftContent: `${baseNote}
## 相关链接

- 待补充：与 PatchBundle 相关的文档
`
    });

    expect(result.content).toContain("## 相关链接\n\n暂无相关链接。");
    expect(result.content).not.toContain("待补充");
    expect(result.report.repairedIssues).toContain("TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS");
  });

  it("normalizes common English headings emitted by real providers", () => {
    const result = runNoteQualityLoop({
      workItemId: "real-provider-english-headings",
      draftContent: `# Go 基础语法

## Summary

Go syntax basics.

## Source Tracking

- raw/go/Go 基础语法.md

## Key Content

- package, func, and errors.

## Related Links

- Go docs
`
    });

    expect(result.content).toContain("## 摘要");
    expect(result.content).toContain("## 来源追踪");
    expect(result.content).toContain("## 关键概念");
    expect(result.content).toContain("## 相关链接");
    expect(result.content).not.toContain("## Summary");
    expect(result.content).not.toContain("## Source Tracking");
    expect(result.content).not.toContain("## Key Content");
    expect(result.content).not.toContain("## Related Links");
    expect(result.report.repairedIssues).toContain("TOPIC_NOTE_NON_CANONICAL_HEADINGS");
  });

  it("uses the default methodology profile for heading aliases", () => {
    const methodology = getKnowledgeMethodology("lmwiki-v1");
    expect(methodology.noteSchema.acceptedSectionAliases["摘要"]).toContain("Overview");

    const result = runNoteQualityLoop({
      workItemId: "overview-heading",
      draftContent: `# Topic

## Overview

body

## Source

- raw/a.md

## Key Points

- point
`
    });

    expect(result.content).toContain("## 摘要");
    expect(result.content).toContain("## 来源追踪");
    expect(result.content).toContain("## 关键概念");
    expect(result.report.repairedIssues).toContain("TOPIC_NOTE_NON_CANONICAL_HEADINGS");
  });
});
