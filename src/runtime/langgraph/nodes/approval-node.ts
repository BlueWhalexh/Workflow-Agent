import type { AgentRunsStore } from "../../../storage/agent-runs-store.js";

export type PlanApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface PlanApprovalArtifact {
  type: "PLAN_APPROVAL";
  status: PlanApprovalStatus;
  approvedAt: string | null;
}

export async function writePlanApproval(input: {
  store: AgentRunsStore;
  status: PlanApprovalStatus;
  approvedAt?: string;
}): Promise<PlanApprovalArtifact> {
  const artifact: PlanApprovalArtifact = {
    type: "PLAN_APPROVAL",
    status: input.status,
    approvedAt: input.approvedAt ?? null
  };
  await input.store.writeJson("approvals/plan-approval.json", artifact);
  return artifact;
}
