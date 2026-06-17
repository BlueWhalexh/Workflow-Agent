import { type ApiFetch, requestApiJson } from "../../shared/api/envelope.js";
import { sanitizeForPublicUi } from "../../shared/safety/public-fields.js";

export type ApprovalDecisionView = "APPROVED" | "REJECTED";

type BackendApprovalResponse = {
  approvalId: string;
  runId: string;
  status: string;
  decision: ApprovalDecisionView | null;
  artifactRef: string | null;
  targetWorkspacePaths: string[];
  requestedByUserId: string;
  decidedByUserId: string | null;
  createdAt: string;
  decidedAt: string | null;
};

export type RunApprovalView = BackendApprovalResponse;

export type DecideRunApprovalInput = {
  approvalId: string;
  decision: ApprovalDecisionView;
};

export async function listRunApprovals(fetcher: ApiFetch, runId: string): Promise<RunApprovalView[]> {
  const approvals = await requestApiJson<BackendApprovalResponse[]>(
    fetcher,
    `/v1/agent-runs/${encodeURIComponent(runId)}/approvals`,
  );
  return approvals.map(toRunApprovalView);
}

export async function decideRunApproval(
  fetcher: ApiFetch,
  runId: string,
  input: DecideRunApprovalInput,
): Promise<RunApprovalView> {
  return toRunApprovalView(await requestApiJson<BackendApprovalResponse>(
    fetcher,
    `/v1/agent-runs/${encodeURIComponent(runId)}/approvals`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    },
  ));
}

function toRunApprovalView(approval: BackendApprovalResponse): RunApprovalView {
  const publicApproval = sanitizeForPublicUi(approval) as BackendApprovalResponse;
  return {
    approvalId: publicApproval.approvalId,
    runId: publicApproval.runId,
    status: publicApproval.status,
    decision: publicApproval.decision,
    artifactRef: publicApproval.artifactRef,
    targetWorkspacePaths: publicApproval.targetWorkspacePaths,
    requestedByUserId: publicApproval.requestedByUserId,
    decidedByUserId: publicApproval.decidedByUserId,
    createdAt: publicApproval.createdAt,
    decidedAt: publicApproval.decidedAt,
  };
}
