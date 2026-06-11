import { runMockNoteAgent } from "../../../agents/mock-note-agent.js";
import type { OrganizePlan } from "../../../domain/planning/plan.js";
import { checkMerge } from "../../../domain/patch/merge-guard.js";
import { publishBundle } from "../../../domain/patch/publisher.js";
import { validateBundle } from "../../../domain/validation/validator.js";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import { readWorkspaceFile } from "../../../storage/workspace-fs.js";
import type { GraphState } from "../state.js";

export async function executePhaseNode(state: GraphState): Promise<Partial<GraphState>> {
  if (!state.autoApprove) {
    return {};
  }

  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const plan = await store.readJson<OrganizePlan>("plan.json");
  const noteItem = plan.workItems.find((item) => item.type === "REWRITE_TOPIC_NOTE");
  if (!noteItem) {
    return { status: "FAILED", lastError: "NO_REWRITE_WORK_ITEM" };
  }

  const sourceContent = await readWorkspaceFile(state.workspaceRoot, noteItem.sourcePaths[0]);
  const bundle = await runMockNoteAgent({
    runId: state.runId,
    workItem: noteItem,
    sourceContent,
    store
  });
  await store.writeJson(`patches/${noteItem.id}.patch.json`, bundle);

  const mergeDecision = await checkMerge({
    workspaceRoot: state.workspaceRoot,
    authorizedTargetPaths: noteItem.targetPaths,
    bundle
  });
  const validation = validateBundle({
    targetPaths: noteItem.targetPaths,
    files: bundle.files
  });

  await store.writeJson("validation.json", { mergeDecision, validation });
  if (!mergeDecision.allowed || !validation.allowed) {
    return { status: "FAILED", lastError: "PATCH_BLOCKED" };
  }

  await publishBundle({ workspaceRoot: state.workspaceRoot, bundle });
  await store.writeJson(`work-items/${noteItem.id}.json`, {
    ...noteItem,
    status: "PUBLISHED"
  });

  return {};
}
