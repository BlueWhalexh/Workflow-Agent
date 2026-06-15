import { createOrganizePlan } from "../../../domain/planning/organize-planner.js";
import type { WorkspaceInventory } from "../../../domain/workspace/inventory.js";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import type { GraphState } from "../state.js";
import { writePlanApproval } from "./approval-node.js";

export async function planNode(state: GraphState): Promise<Partial<GraphState>> {
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const existingPlan = await store.readJson("plan.json").catch((error: unknown) => {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    throw error;
  });
  if (existingPlan) {
    const existingMethodologyId = (existingPlan as { methodologyId?: string }).methodologyId ?? "lmwiki-v1";
    if (existingMethodologyId !== state.methodologyId) {
      throw new Error(`Existing plan methodology mismatch: ${existingMethodologyId} != ${state.methodologyId}`);
    }
    await writePlanApproval({
      store,
      status: state.autoApprove ? "APPROVED" : "PENDING",
      approvedAt: state.autoApprove ? new Date(0).toISOString() : undefined
    });
    return {
      status: state.autoApprove ? "RUNNING" : "WAITING_PLAN_APPROVAL",
      planPath: `.agent-runs/${state.runId}/plan.json`,
      pendingApproval: state.autoApprove
        ? undefined
        : {
            type: "PLAN",
            artifactPath: `.agent-runs/${state.runId}/approvals/plan-approval.json`
          }
    };
  }

  const inventory = await store.readJson<WorkspaceInventory>("inventory.json");
  const plan = createOrganizePlan({
    runId: state.runId,
    instruction: state.instruction,
    inventory,
    methodologyId: state.methodologyId
  });
  await store.writeJson("plan.json", plan);
  for (const item of plan.workItems) {
    await store.writeJson(`work-items/${item.id}.json`, item);
  }
  await writePlanApproval({
    store,
    status: state.autoApprove ? "APPROVED" : "PENDING",
    approvedAt: state.autoApprove ? new Date(0).toISOString() : undefined
  });
  return {
    status: state.autoApprove ? "RUNNING" : "WAITING_PLAN_APPROVAL",
    planPath: `.agent-runs/${state.runId}/plan.json`,
    pendingApproval: state.autoApprove
      ? undefined
      : {
          type: "PLAN",
          artifactPath: `.agent-runs/${state.runId}/approvals/plan-approval.json`
        }
  };
}
