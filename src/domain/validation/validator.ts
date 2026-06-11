import type { PatchFile } from "../patch/patch-bundle.js";

export interface ValidationResult {
  allowed: boolean;
  hardBlockers: string[];
  qualityIssues: string[];
}

export function validateBundle(input: { targetPaths: string[]; files: PatchFile[] }): ValidationResult {
  const hardBlockers: string[] = [];
  const qualityIssues: string[] = [];

  for (const file of input.files) {
    if (file.path.startsWith("raw/") || file.path.startsWith("schema/")) {
      hardBlockers.push("RAW_OR_SCHEMA_WRITE_BLOCKED");
    }
    if (file.content.includes("<placeholder>") || file.content.includes("后续补充")) {
      hardBlockers.push("PLACEHOLDER_CONTENT_BLOCKED");
    }
    const isTopicNote = file.path.startsWith("knowledge-base/topics/") && !file.path.endsWith("/index.md");
    if (isTopicNote) {
      if (file.content.includes("Raw mirror:") || file.content.includes("## Content")) {
        hardBlockers.push("TOPIC_NOTE_STILL_RAW_MIRROR");
      }
      if (!/^#\s+.+/m.test(file.content)) {
        hardBlockers.push("TOPIC_NOTE_MISSING_TITLE");
      }
      if (!file.content.includes("## 摘要")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_SUMMARY");
      }
      if (!file.content.includes("## 来源追踪")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_SOURCE_TRACKING");
      }
      if (
        !file.content.includes("## 关键决策") &&
        !file.content.includes("## 关键概念") &&
        !file.content.includes("## 关键步骤")
      ) {
        hardBlockers.push("TOPIC_NOTE_MISSING_KEY_CONTENT");
      }
      if (!file.content.includes("## 相关链接")) {
        qualityIssues.push("TOPIC_NOTE_WEAK_RELATIONS");
      }
    }
  }

  return {
    allowed: hardBlockers.length === 0,
    hardBlockers: Array.from(new Set(hardBlockers)),
    qualityIssues: Array.from(new Set(qualityIssues))
  };
}
