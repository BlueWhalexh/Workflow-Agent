import { scanWorkspace } from "../../../domain/workspace/inventory.js";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import type { GraphState } from "../state.js";

export async function inventoryNode(state: GraphState): Promise<Partial<GraphState>> {
  const inventory = await scanWorkspace({ workspaceRoot: state.workspaceRoot });
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  await store.writeJson("inventory.json", inventory);
  return {};
}
