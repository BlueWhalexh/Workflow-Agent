import type { WorkspaceInventory } from "../workspace/inventory.js";
import type { OrganizePlan } from "./plan.js";
import type { WorkItem } from "./work-item.js";

function slugFromPath(filePath: string): string {
  return filePath
    .replace(/^raw\//, "")
    .replace(/\.md$/, "")
    .replace(/[\/\s]+/g, "-")
    .toLowerCase();
}

function targetPathForRaw(rawPath: string): string {
  return rawPath.replace(/^raw\//, "knowledge-base/topics/");
}

function topicIndexPathForTarget(targetPath: string): string {
  const parts = targetPath.split("/");
  if (parts.length >= 3 && parts[0] === "knowledge-base" && parts[1] === "topics") {
    return parts.slice(0, 3).join("/") + "/index.md";
  }
  return parts.slice(0, -1).join("/") + "/index.md";
}

export function createOrganizePlan(input: {
  runId: string;
  instruction: string;
  inventory: WorkspaceInventory;
}): OrganizePlan {
  const noteWorkItems: WorkItem[] = input.inventory.rawFiles.map((rawFile) => {
    const targetPath = targetPathForRaw(rawFile.path);
    const existingPage = input.inventory.knowledgeBasePages.find((page) => page.path === targetPath);
    const isMirror = existingPage?.state === "BOOTSTRAP_MIRROR";

    return {
      id: `${isMirror ? "rewrite" : "create"}-${slugFromPath(rawFile.path)}`,
      type: isMirror ? "REWRITE_TOPIC_NOTE" : "CREATE_TOPIC_NOTE",
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
      publishPolicy: "AUTO_PUBLISH"
    };
  });

  const topicIndexItems: WorkItem[] = Array.from(
    new Set(noteWorkItems.map((item) => topicIndexPathForTarget(item.targetPaths[0])))
  ).map((targetPath) => ({
    id: `maintain-${targetPath
      .replace(/^knowledge-base\/topics\//, "")
      .replace(/\/index\.md$/, "")
      .replace(/\//g, "-")}-index`,
    type: "MAINTAIN_TOPIC_INDEX",
    phase: "phase-b-indexes",
    status: "PLANNED",
    sourcePaths: [],
    targetPaths: [targetPath],
    baseShas: {},
    risk: "LOW",
    requiresApproval: false,
    reason: "topic index must link organized notes",
    attempts: [],
    publishPolicy: "AUTO_PUBLISH"
  }));

  const globalItems: WorkItem[] = [
    {
      id: "maintain-moc",
      type: "MAINTAIN_MOC",
      phase: "phase-c-global",
      status: "PLANNED",
      sourcePaths: [],
      targetPaths: ["knowledge-base/moc.md"],
      baseShas: {},
      risk: "LOW",
      requiresApproval: false,
      reason: "global MOC must link topic indexes",
      attempts: [],
      publishPolicy: "AUTO_PUBLISH"
    },
    {
      id: "quality-review",
      type: "QUALITY_REVIEW",
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
  const schemaSha = input.inventory.schemaFiles.find((file) => file.path === "schema/CLAUDE.md")?.sha ?? null;

  return {
    runId: input.runId,
    instruction: input.instruction,
    mode: "SEMI_AUTOMATIC",
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
