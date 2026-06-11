import { createOrganizePlan } from "../../../domain/planning/organize-planner.js";
import type { WorkspaceInventory } from "../../../domain/workspace/inventory.js";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import type { GraphState } from "../state.js";

export async function planNode(state: GraphState): Promise<Partial<GraphState>> {
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const inventory = await store.readJson<WorkspaceInventory>("inventory.json");
  const plan = createOrganizePlan({
    runId: state.runId,
    instruction: state.instruction,
    inventory
  });
  await store.writeJson("plan.json", plan);
  for (const item of plan.workItems) {
    await store.writeJson(`work-items/${item.id}.json`, item);
  }
  return {
    status: state.autoApprove ? "RUNNING" : "WAITING_PLAN_APPROVAL",
    planPath: `.agent-runs/${state.runId}/plan.json`
  };
}
