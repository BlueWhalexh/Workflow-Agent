export interface GraphState {
  runId: string;
  workspaceRoot: string;
  instruction: string;
  autoApprove: boolean;
  status: "CREATED" | "WAITING_PLAN_APPROVAL" | "RUNNING" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  planPath?: string;
  reportPath?: string;
  pendingApproval?: {
    type: "PLAN";
    artifactPath: string;
  };
  lastError?: string;
}
