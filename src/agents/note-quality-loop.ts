import {
  getKnowledgeMethodology,
  type KnowledgeMethodology
} from "../domain/methodology/knowledge-methodology.js";

export type NoteQualityIssue =
  | "TOPIC_NOTE_WEAK_RELATIONS"
  | "TOPIC_NOTE_NON_CANONICAL_HEADINGS"
  | "TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS";

export interface NoteQualityLoopStep {
  name: "GENERATE_NOTE" | "SELF_CHECK" | "REPAIR_NOTE" | "SELF_CHECK_AFTER_REPAIR";
  status: "SUCCEEDED" | "SKIPPED";
  issues: NoteQualityIssue[];
  repairedIssues: NoteQualityIssue[];
}

export interface NoteQualityLoopReport {
  schemaVersion: "note-quality-loop.v1";
  workItemId: string;
  steps: NoteQualityLoopStep[];
  repairedIssues: NoteQualityIssue[];
  remainingIssues: NoteQualityIssue[];
}

export interface NoteQualityLoopResult {
  content: string;
  report: NoteQualityLoopReport;
}

const DEFAULT_METHODOLOGY = getKnowledgeMethodology();

function detectRepairableIssues(
  content: string,
  methodology: KnowledgeMethodology = DEFAULT_METHODOLOGY
): NoteQualityIssue[] {
  const issues: NoteQualityIssue[] = [];
  if (!content.includes("## 相关链接")) {
    issues.push("TOPIC_NOTE_WEAK_RELATIONS");
  }
  if (hasNonCanonicalHeadings(content, methodology)) {
    issues.push("TOPIC_NOTE_NON_CANONICAL_HEADINGS");
  }
  if (hasPlaceholderRelatedLinks(content, methodology)) {
    issues.push("TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS");
  }
  return issues;
}

function hasNonCanonicalHeadings(content: string, methodology: KnowledgeMethodology): boolean {
  return content
    .split("\n")
    .some((line) => canonicalHeadingFor(line, methodology) !== undefined);
}

function hasPlaceholderRelatedLinks(content: string, methodology: KnowledgeMethodology): boolean {
  const section = sectionByCanonicalHeading(content, "相关链接");
  return section.some(
    (line) => /^[-*]\s+/.test(line.trim()) && containsPlaceholderBlocker(line, methodology)
  );
}

function normalizeCanonicalHeadings(
  content: string,
  methodology: KnowledgeMethodology
): { content: string; repaired: boolean } {
  const normalized = content
    .split("\n")
    .map((line) => {
      const canonicalHeading = canonicalHeadingFor(line, methodology);
      return canonicalHeading ? `## ${canonicalHeading}` : line;
    })
    .join("\n");
  return {
    content: normalized,
    repaired: normalized !== content
  };
}

function repairPlaceholderRelatedLinks(
  content: string,
  methodology: KnowledgeMethodology
): { content: string; repaired: boolean } {
  const relatedHeadingMatch = /^##\s+相关链接\s*$/m.exec(content);
  if (!relatedHeadingMatch) {
    return { content, repaired: false };
  }
  const headingStart = relatedHeadingMatch.index;
  const nextHeadingMatch = /^##\s+.+\s*$/m.exec(content.slice(headingStart + relatedHeadingMatch[0].length));
  const sectionEnd = nextHeadingMatch
    ? headingStart + relatedHeadingMatch[0].length + nextHeadingMatch.index
    : content.length;
  const before = content.slice(0, headingStart);
  const section = content.slice(headingStart, sectionEnd);
  const after = content.slice(sectionEnd);
  if (!containsPlaceholderBlocker(section, methodology)) {
    return { content, repaired: false };
  }
  const keptLines = section
    .split("\n")
    .filter((line) => !(/^[-*]\s+/.test(line.trim()) && containsPlaceholderBlocker(line, methodology)))
    .join("\n")
    .trimEnd();
  const repairedSection = /^##\s+相关链接\s*$/m.test(keptLines) && /[-*]\s+/.test(keptLines)
    ? keptLines
    : "## 相关链接\n\n暂无相关链接。";
  return {
    content: `${before}${repairedSection}${after}`,
    repaired: true
  };
}

function canonicalHeadingFor(line: string, methodology: KnowledgeMethodology): string | undefined {
  const match = /^##\s+(.+?)\s*$/.exec(line);
  if (!match) {
    return undefined;
  }
  return headingAliasMap(methodology).get(normalizeHeading(match[1]));
}

function headingAliasMap(methodology: KnowledgeMethodology): Map<string, string> {
  const aliases = new Map<string, string>();
  for (const [canonical, values] of Object.entries(methodology.noteSchema.acceptedSectionAliases)) {
    for (const alias of values) {
      aliases.set(normalizeHeading(alias), canonical);
    }
  }
  return aliases;
}

function normalizeHeading(value: string): string {
  return value.trim().replace(/\s+/g, " ").toLocaleLowerCase();
}

function sectionByCanonicalHeading(content: string, heading: string): string[] {
  const lines = content.split("\n");
  const startIndex = lines.findIndex((line) => line.trim() === `## ${heading}`);
  if (startIndex === -1) {
    return [];
  }
  const nextHeadingIndex = lines.findIndex((line, index) => index > startIndex && /^##\s+.+\s*$/.test(line));
  return lines.slice(startIndex, nextHeadingIndex === -1 ? undefined : nextHeadingIndex);
}

function containsPlaceholderBlocker(content: string, methodology: KnowledgeMethodology): boolean {
  const lowerContent = content.toLocaleLowerCase();
  return methodology.noteSchema.placeholderBlockers.some((blocker) =>
    lowerContent.includes(blocker.toLocaleLowerCase())
  );
}

function canRepairWeakRelations(content: string): boolean {
  return /^#\s+.+/m.test(content) && content.includes("## 摘要") && content.includes("## 来源追踪");
}

function repairWeakRelations(content: string): string {
  const trimmed = content.trimEnd();
  return `${trimmed}

## 相关链接

暂无相关链接。
`;
}

export function runNoteQualityLoop(input: { workItemId: string; draftContent: string }): NoteQualityLoopResult {
  const methodology = DEFAULT_METHODOLOGY;
  const steps: NoteQualityLoopStep[] = [
    {
      name: "GENERATE_NOTE",
      status: "SUCCEEDED",
      issues: [],
      repairedIssues: []
    }
  ];
  let content = input.draftContent;
  const headingRepair = normalizeCanonicalHeadings(content, methodology);
  content = headingRepair.content;
  const initialIssues = detectRepairableIssues(content, methodology);
  steps.push({
    name: "SELF_CHECK",
    status: "SUCCEEDED",
    issues: initialIssues,
    repairedIssues: []
  });

  const repairedIssues: NoteQualityIssue[] = [];
  if (headingRepair.repaired) {
    repairedIssues.push("TOPIC_NOTE_NON_CANONICAL_HEADINGS");
  }
  const relatedPlaceholderRepair = repairPlaceholderRelatedLinks(content, methodology);
  content = relatedPlaceholderRepair.content;
  if (relatedPlaceholderRepair.repaired) {
    repairedIssues.push("TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS");
  }
  if (initialIssues.includes("TOPIC_NOTE_WEAK_RELATIONS") && canRepairWeakRelations(content)) {
    content = repairWeakRelations(content);
    repairedIssues.push("TOPIC_NOTE_WEAK_RELATIONS");
    steps.push({
      name: "REPAIR_NOTE",
      status: "SUCCEEDED",
      issues: initialIssues,
      repairedIssues
    });
    steps.push({
      name: "SELF_CHECK_AFTER_REPAIR",
      status: "SUCCEEDED",
      issues: detectRepairableIssues(content, methodology),
      repairedIssues
    });
  } else {
    steps.push({
      name: "REPAIR_NOTE",
      status: "SKIPPED",
      issues: initialIssues,
      repairedIssues: []
    });
  }

  const remainingIssues = detectRepairableIssues(content, methodology);
  return {
    content,
    report: {
      schemaVersion: "note-quality-loop.v1",
      workItemId: input.workItemId,
      steps,
      repairedIssues,
      remainingIssues
    }
  };
}
