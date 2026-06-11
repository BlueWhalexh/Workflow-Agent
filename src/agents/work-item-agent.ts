import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import type { LlmNoteProvider } from "../domain/llm-provider/provider.js";
import type { WorkItem } from "../domain/planning/work-item.js";
import type { AgentRunsStore } from "../storage/agent-runs-store.js";

export interface WorkItemAgentInput {
  runId: string;
  workItem: WorkItem;
  sourceContent: string;
  store?: AgentRunsStore;
  provider?: LlmNoteProvider;
}

export type WorkItemAgent = (input: WorkItemAgentInput) => Promise<PatchBundle>;
