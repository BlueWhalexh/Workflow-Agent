import type { ApiFetch } from "../../shared/api/envelope.js";
import {
  decideRunApproval,
  listRunApprovals,
  type ApprovalDecisionView,
  type RunApprovalView,
} from "./approval-api.js";

export class NoPendingApprovalError extends Error {
  constructor(runId: string) {
    super(`Run ${runId} has no pending approvals`);
    this.name = "NoPendingApprovalError";
  }
}

export async function decideLatestRunApproval(
  fetcher: ApiFetch,
  runId: string,
  decision: ApprovalDecisionView,
): Promise<RunApprovalView> {
  const approvals = await listRunApprovals(fetcher, runId);
  const pendingApproval = approvals.find((approval) => approval.status === "PENDING" && approval.decision === null);
  if (!pendingApproval) {
    throw new NoPendingApprovalError(runId);
  }

  return decideRunApproval(fetcher, runId, {
    approvalId: pendingApproval.approvalId,
    decision,
  });
}
