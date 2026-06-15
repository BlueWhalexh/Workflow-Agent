import { getKnowledgeMethodology, type KnowledgeMethodology } from "../methodology/knowledge-methodology.js";
import type { PatchFile } from "../patch/patch-bundle.js";

export interface ValidationResult {
  allowed: boolean;
  hardBlockers: string[];
  qualityIssues: string[];
}

export function validateBundle(input: { targetPaths: string[]; files: PatchFile[]; methodologyId?: string }): ValidationResult {
  const methodology = getKnowledgeMethodology(input.methodologyId);
  const hardBlockers: string[] = [];
  const qualityIssues: string[] = [];

  for (const file of input.files) {
    if (isInsideDir(file.path, methodology.layout.rawDir) || isInsideDir(file.path, methodology.layout.rulesDir)) {
      hardBlockers.push("RAW_OR_SCHEMA_WRITE_BLOCKED");
    }
    if (containsPlaceholderBlocker(file.content, methodology)) {
      hardBlockers.push("PLACEHOLDER_CONTENT_BLOCKED");
    }
    const isTopicNote = isInsideDir(file.path, methodology.layout.topicDir) && !file.path.endsWith("/index.md");
    if (isTopicNote) {
      const missingSections = missingMethodologyRequiredSections(file.content, methodology);
      if (file.content.includes("Raw mirror:") || file.content.includes("## Content")) {
        hardBlockers.push("TOPIC_NOTE_STILL_RAW_MIRROR");
      }
      if (!/^#\s+.+/m.test(file.content)) {
        hardBlockers.push("TOPIC_NOTE_MISSING_TITLE");
      }
      if (missingSections.includes("摘要")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_SUMMARY");
      }
      if (missingSections.includes("来源追踪")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_SOURCE_TRACKING");
      }
      if (missingSections.includes("关键决策|关键概念|关键步骤")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_KEY_CONTENT");
      }
      if (missingSections.includes("相关链接")) {
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

function isInsideDir(filePath: string, directory: string): boolean {
  return filePath === directory || filePath.startsWith(`${directory}/`);
}

function containsPlaceholderBlocker(content: string, methodology: KnowledgeMethodology): boolean {
  const lowerContent = content.toLocaleLowerCase();
  return methodology.noteSchema.placeholderBlockers.some((blocker) =>
    lowerContent.includes(blocker.toLocaleLowerCase())
  );
}

function hasRequiredSection(content: string, section: string): boolean {
  return content.includes(`## ${section}`);
}

function hasAnyRequiredSectionGroup(content: string, sectionGroup: string): boolean {
  return sectionGroup.split("|").some((section) => hasRequiredSection(content, section));
}

function missingMethodologyRequiredSections(content: string, methodology: KnowledgeMethodology): string[] {
  return methodology.noteSchema.requiredSections.filter((section) =>
    section.includes("|") ? !hasAnyRequiredSectionGroup(content, section) : !hasRequiredSection(content, section)
  );
}
