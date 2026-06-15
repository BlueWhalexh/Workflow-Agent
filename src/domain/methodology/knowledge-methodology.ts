export type PublishPolicy = "AUTO_PUBLISH" | "CANDIDATE_PATCH_ONLY";

export interface KnowledgeMethodology {
  id: string;
  displayName: string;
  version: string;
  layout: {
    rawDir: string;
    rulesDir: string;
    knowledgeBaseDir: string;
    topicDir: string;
    mocPath: string;
  };
  noteSchema: {
    requiredSections: string[];
    acceptedSectionAliases: Record<string, string[]>;
    placeholderBlockers: string[];
  };
  planner: {
    defaultPublishPolicy: PublishPolicy;
    createTopicIndex: boolean;
    createMoc: boolean;
    finalQualityReview: boolean;
  };
  validation: {
    hardBlockers: string[];
    repairableIssues: string[];
  };
}

export interface KnowledgeMethodologySummary {
  id: string;
  displayName: string;
  version: string;
}

const LMWIKI_V1_PROFILE = {
  id: "lmwiki-v1",
  displayName: "LMWiki",
  version: "1",
  layout: {
    rawDir: "raw",
    rulesDir: "schema",
    knowledgeBaseDir: "knowledge-base",
    topicDir: "knowledge-base/topics",
    mocPath: "knowledge-base/moc.md"
  },
  noteSchema: {
    requiredSections: ["摘要", "来源追踪", "关键决策|关键概念|关键步骤", "相关链接"],
    acceptedSectionAliases: {
      摘要: ["总结", "Summary", "Overview"],
      来源追踪: ["源追踪", "源信息", "源文件", "源文件追踪", "来源", "Source Tracking", "Source", "Sources"],
      关键概念: ["关键内容", "Key Content", "Key Points", "Key Concepts"],
      相关链接: ["Related Links", "References"]
    },
    placeholderBlockers: ["TODO", "TBD", "<placeholder>", "后续补充", "待补充"]
  },
  planner: {
    defaultPublishPolicy: "AUTO_PUBLISH",
    createTopicIndex: true,
    createMoc: true,
    finalQualityReview: true
  },
  validation: {
    hardBlockers: ["CONTENT_SHA_PENDING", "RAW_SOURCE_MISSING", "RAW_LAYER_MUTATION"],
    repairableIssues: [
      "TOPIC_NOTE_WEAK_RELATIONS",
      "TOPIC_NOTE_NON_CANONICAL_HEADINGS",
      "TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS"
    ]
  }
} satisfies KnowledgeMethodology;

const LMWIKI_V1: KnowledgeMethodology = Object.freeze(LMWIKI_V1_PROFILE);

const registeredMethodologies = new Map<string, KnowledgeMethodology>([[LMWIKI_V1.id, LMWIKI_V1]]);

export function getKnowledgeMethodology(id = "lmwiki-v1"): KnowledgeMethodology {
  const methodology = registeredMethodologies.get(id);
  if (!methodology) {
    throw new Error(`Unknown knowledge methodology: ${id}`);
  }
  return methodology;
}

export function listKnowledgeMethodologies(): KnowledgeMethodologySummary[] {
  return Array.from(registeredMethodologies.values()).map((methodology) => ({
    id: methodology.id,
    displayName: methodology.displayName,
    version: methodology.version
  }));
}
