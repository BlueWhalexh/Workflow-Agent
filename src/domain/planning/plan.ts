import type { WorkItem } from "./work-item.js";

export interface PlanPhase {
  id: "phase-a-notes" | "phase-b-indexes" | "phase-c-global";
  type: "NOTE_WRITES" | "TOPIC_INDEXES" | "GLOBAL_REVIEW";
  workItemIds: string[];
}

export interface OrganizePlan {
  runId: string;
  instruction: string;
  mode: "SEMI_AUTOMATIC";
  workspaceSnapshot: {
    workspaceRoot: string;
    rawCount: number;
    knowledgeBasePageCount: number;
    schemaSha: string | null;
  };
  approval: {
    status: "PENDING" | "APPROVED" | "REJECTED";
    approvedAt: string | null;
  };
  phases: PlanPhase[];
  workItems: WorkItem[];
}
