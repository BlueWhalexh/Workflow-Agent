import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import type { WorkItem } from "../domain/planning/work-item.js";

export interface WorkItemAgentInput {
  runId: string;
  workItem: WorkItem;
  sourceContent: string;
}

export type WorkItemAgent = (input: WorkItemAgentInput) => Promise<PatchBundle>;
