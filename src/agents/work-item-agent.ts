import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import type { WorkItem } from "../domain/planning/work-item.js";
import type { AgentRunsStore } from "../storage/agent-runs-store.js";

export interface WorkItemAgentInput {
  runId: string;
  workItem: WorkItem;
  sourceContent: string;
  store?: AgentRunsStore;
}

export type WorkItemAgent = (input: WorkItemAgentInput) => Promise<PatchBundle>;
