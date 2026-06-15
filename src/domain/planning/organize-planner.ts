import { getKnowledgeMethodology, type KnowledgeMethodology } from "../methodology/knowledge-methodology.js";
import type { WorkspaceInventory } from "../workspace/inventory.js";
import type { OrganizePlan } from "./plan.js";
import type { WorkItem } from "./work-item.js";

function slugFromPath(filePath: string, methodology: KnowledgeMethodology): string {
  return filePath
    .replace(new RegExp(`^${methodology.layout.rawDir}/`), "")
    .replace(/\.md$/, "")
    .replace(/[\/\s]+/g, "-")
    .toLowerCase();
}

function targetPathForRaw(rawPath: string, methodology: KnowledgeMethodology): string {
  return rawPath.replace(new RegExp(`^${methodology.layout.rawDir}/`), `${methodology.layout.topicDir}/`);
}

function topicIndexPathForTarget(targetPath: string, methodology: KnowledgeMethodology): string {
  const parts = targetPath.split("/");
  const topicParts = methodology.layout.topicDir.split("/");
  if (parts.length > topicParts.length && topicParts.every((part, index) => parts[index] === part)) {
    return parts.slice(0, topicParts.length + 1).join("/") + "/index.md";
  }
  return parts.slice(0, -1).join("/") + "/index.md";
}

export function createOrganizePlan(input: {
  runId: string;
  instruction: string;
  inventory: WorkspaceInventory;
  methodologyId?: string;
}): OrganizePlan {
  const methodology = getKnowledgeMethodology(input.methodologyId);
  const noteWorkItems: WorkItem[] = input.inventory.rawFiles.map((rawFile) => {
    const targetPath = targetPathForRaw(rawFile.path, methodology);
    const existingPage = input.inventory.knowledgeBasePages.find((page) => page.path === targetPath);
    const isMirror = existingPage?.state === "BOOTSTRAP_MIRROR";

    return {
      id: `${isMirror ? "rewrite" : "create"}-${slugFromPath(rawFile.path, methodology)}`,
      type: isMirror ? "REWRITE_TOPIC_NOTE" : "CREATE_TOPIC_NOTE",
      methodologyId: methodology.id,
      phase: "phase-a-notes",
      status: "PLANNED",
      sourcePaths: [rawFile.path],
      targetPaths: [targetPath],
      baseShas: {
        [rawFile.path]: rawFile.sha,
        ...(existingPage ? { [targetPath]: existingPage.sha } : {})
      },
      risk: "LOW",
      requiresApproval: false,
      reason: isMirror ? "existing page is bootstrap raw mirror" : "raw file has no organized note",
      attempts: [],
      publishPolicy: methodology.planner.defaultPublishPolicy
    };
  });

  const topicIndexItems: WorkItem[] = Array.from(
    new Set(noteWorkItems.map((item) => topicIndexPathForTarget(item.targetPaths[0], methodology)))
  ).map((targetPath) => ({
    id: `maintain-${targetPath
      .replace(new RegExp(`^${methodology.layout.topicDir}/`), "")
      .replace(/\/index\.md$/, "")
      .replace(/\//g, "-")}-index`,
    type: "MAINTAIN_TOPIC_INDEX",
    methodologyId: methodology.id,
    phase: "phase-b-indexes",
    status: "PLANNED",
    sourcePaths: [],
    targetPaths: [targetPath],
    baseShas: {},
    risk: "LOW",
    requiresApproval: false,
    reason: "topic index must link organized notes",
    attempts: [],
    publishPolicy: methodology.planner.defaultPublishPolicy
  }));

  const globalItems: WorkItem[] = [
    {
      id: "maintain-moc",
      type: "MAINTAIN_MOC",
      methodologyId: methodology.id,
      phase: "phase-c-global",
      status: "PLANNED",
      sourcePaths: [],
      targetPaths: [methodology.layout.mocPath],
      baseShas: {},
      risk: "LOW",
      requiresApproval: false,
      reason: "global MOC must link topic indexes",
      attempts: [],
      publishPolicy: methodology.planner.defaultPublishPolicy
    },
    {
      id: "quality-review",
      type: "QUALITY_REVIEW",
      methodologyId: methodology.id,
      phase: "phase-c-global",
      status: "PLANNED",
      sourcePaths: [],
      targetPaths: [],
      baseShas: {},
      risk: "LOW",
      requiresApproval: false,
      reason: "final report must include quality issues",
      attempts: []
    }
  ];

  const workItems = [...noteWorkItems, ...topicIndexItems, ...globalItems];
  const schemaSha = input.inventory.schemaFiles.find((file) => file.path === `${methodology.layout.rulesDir}/CLAUDE.md`)?.sha ?? null;

  return {
    runId: input.runId,
    instruction: input.instruction,
    mode: "SEMI_AUTOMATIC",
    methodologyId: methodology.id,
    methodologyVersion: methodology.version,
    workspaceSnapshot: {
      workspaceRoot: input.inventory.workspaceRoot,
      rawCount: input.inventory.rawFiles.length,
      knowledgeBasePageCount: input.inventory.knowledgeBasePages.length,
      schemaSha
    },
    approval: {
      status: "PENDING",
      approvedAt: null
    },
    phases: [
      {
        id: "phase-a-notes",
        type: "NOTE_WRITES",
        workItemIds: workItems.filter((item) => item.phase === "phase-a-notes").map((item) => item.id)
      },
      {
        id: "phase-b-indexes",
        type: "TOPIC_INDEXES",
        workItemIds: workItems.filter((item) => item.phase === "phase-b-indexes").map((item) => item.id)
      },
      {
        id: "phase-c-global",
        type: "GLOBAL_REVIEW",
        workItemIds: workItems.filter((item) => item.phase === "phase-c-global").map((item) => item.id)
      }
    ],
    workItems
  };
}
