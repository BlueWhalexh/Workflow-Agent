import { describe, expect, it } from "vitest";
import { validateBundle } from "../../src/domain/validation/validator.js";
import { decideResumeAction } from "../../src/domain/validation/resume-decision.js";
import { sha256 } from "../../src/storage/sha.js";

describe("validator", () => {
  it("blocks topic notes that still look like raw mirrors", () => {
    const content = "# Skill vs CLI Tool 决策\n\nRaw mirror: true\nSource path: raw/tools/a.md\n\n## Content\n\nraw";
    const result = validateBundle({
      targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
      files: [
        {
          path: "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md",
          changeType: "MODIFIED",
          baseSha: "base",
          contentSha: sha256(content),
          content
        }
      ]
    });

    expect(result.allowed).toBe(false);
    expect(result.hardBlockers).toContain("TOPIC_NOTE_STILL_RAW_MIRROR");
  });

  it("allows organized topic note with required sections", () => {
    const content = `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - raw/tools/Skill vs CLI Tool 决策.md
sourceShas:
  raw/tools/Skill vs CLI Tool 决策.md: abc
lastRunId: run-test
contentSha: def
-->

## 摘要

沉淀 skill 与 CLI tool 的适用边界。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Skill 适合流程和判断。
- CLI tool 适合确定性动作。

## 相关链接

暂无相关链接。`;

    const result = validateBundle({
      targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
      files: [
        {
          path: "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md",
          changeType: "MODIFIED",
          baseSha: "base",
          contentSha: sha256(content),
          content
        }
      ]
    });

    expect(result.allowed).toBe(true);
    expect(result.hardBlockers).toEqual([]);
  });
});

describe("resume decision", () => {
  it("skips published work item when current sha matches content sha", () => {
    expect(
      decideResumeAction({
        status: "PUBLISHED",
        currentSha: "same",
        contentSha: "same"
      })
    ).toBe("SKIP");
  });

  it("retries timeout and validator-blocked failures during resume", () => {
    expect(decideResumeAction({ status: "FAILED_TIMEOUT" })).toBe("RETRY");
    expect(decideResumeAction({ status: "BLOCKED_BY_VALIDATOR" })).toBe("RETRY");
    expect(decideResumeAction({ status: "FAILED_EXECUTOR", retryable: false })).toBe("REPORT_FAILED");
  });
});
