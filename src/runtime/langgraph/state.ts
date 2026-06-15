import type { ProviderRuntimeConfig } from "../provider/provider-runtime-config.js";

export interface GraphState {
  runId: string;
  workspaceRoot: string;
  instruction: string;
  methodologyId: string;
  autoApprove: boolean;
  providerRuntime?: ProviderRuntimeConfig;
  status: "CREATED" | "WAITING_PLAN_APPROVAL" | "RUNNING" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  planPath?: string;
  reportPath?: string;
  pendingApproval?: {
    type: "PLAN";
    artifactPath: string;
  };
  lastError?: string;
}
