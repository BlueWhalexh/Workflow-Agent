import type { WorkItem, WorkItemType } from "../domain/planning/work-item.js";
import type { AgentRunsStore } from "../storage/agent-runs-store.js";

export type WorkItemAgentNode = "note" | "topic-index" | "moc" | "quality-review";

export interface WorkItemAgentBudget {
  maxIterations: number;
  maxProviderCalls: number;
  timeoutMs: number;
}

export interface WorkItemAgentUsage {
  iterations: number;
  providerCalls: number;
}

export interface WorkItemAgentLoopStep {
  name: string;
  kind: "draft" | "self_check" | "repair" | "validate" | "summarize";
  status: "SUCCEEDED" | "SKIPPED" | "FAILED";
  issues: string[];
  repairedIssues: string[];
  message?: string;
}

export interface WorkItemAgentLoopReport {
  schemaVersion: "work-item-agent-loop.v1";
  runId: string;
  workItemId: string;
  workItemType: WorkItemType;
  agentNode: WorkItemAgentNode;
  status: "SUCCEEDED" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  budget: WorkItemAgentBudget;
  usage: WorkItemAgentUsage;
  steps: WorkItemAgentLoopStep[];
  repairedIssues: string[];
  remainingIssues: string[];
  outputRef?: {
    kind: "patch" | "quality";
    path: string;
  };
}

export interface WorkItemContextContract {
  workItemType: WorkItemType;
  allowedWorkspaceReads: string[];
  allowedArtifactReads: string[];
  requiredShas: Record<string, string>;
  forbiddenReads: string[];
}

export function budgetForWorkItemType(type: WorkItemType): WorkItemAgentBudget {
  if (type === "CREATE_TOPIC_NOTE" || type === "REWRITE_TOPIC_NOTE") {
    return { maxIterations: 2, maxProviderCalls: 1, timeoutMs: 30000 };
  }
  if (type === "MERGE_USER_EDITED_NOTE") {
    return { maxIterations: 0, maxProviderCalls: 0, timeoutMs: 0 };
  }
  return { maxIterations: 1, maxProviderCalls: 0, timeoutMs: 5000 };
}

export function agentNodeForWorkItemType(type: WorkItemType): WorkItemAgentNode {
  if (type === "CREATE_TOPIC_NOTE" || type === "REWRITE_TOPIC_NOTE") {
    return "note";
  }
  if (type === "MAINTAIN_TOPIC_INDEX") {
    return "topic-index";
  }
  if (type === "MAINTAIN_MOC") {
    return "moc";
  }
  return "quality-review";
}

export function buildLoopReport(
  input: Omit<WorkItemAgentLoopReport, "schemaVersion">
): WorkItemAgentLoopReport {
  return {
    schemaVersion: "work-item-agent-loop.v1",
    ...input
  };
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isBudget(value: unknown): value is WorkItemAgentBudget {
  return (
    isObject(value) &&
    typeof value.maxIterations === "number" &&
    typeof value.maxProviderCalls === "number" &&
    typeof value.timeoutMs === "number"
  );
}

function isUsage(value: unknown): value is WorkItemAgentUsage {
  return isObject(value) && typeof value.iterations === "number" && typeof value.providerCalls === "number";
}

export function isWorkItemAgentLoopReport(value: unknown): value is WorkItemAgentLoopReport {
  if (!isObject(value)) {
    return false;
  }
  return (
    value.schemaVersion === "work-item-agent-loop.v1" &&
    typeof value.runId === "string" &&
    typeof value.workItemId === "string" &&
    typeof value.workItemType === "string" &&
    typeof value.agentNode === "string" &&
    typeof value.status === "string" &&
    isBudget(value.budget) &&
    isUsage(value.usage) &&
    Array.isArray(value.steps) &&
    Array.isArray(value.repairedIssues) &&
    Array.isArray(value.remainingIssues)
  );
}

export function loopBudgetExceeded(report: WorkItemAgentLoopReport): boolean {
  return (
    report.usage.iterations > report.budget.maxIterations ||
    report.usage.providerCalls > report.budget.maxProviderCalls
  );
}

export async function writeLoopReport(store: AgentRunsStore, report: WorkItemAgentLoopReport): Promise<void> {
  await store.writeJson(`agent-loop/${report.workItemId}.json`, report);
}

export function outputRefForWorkItem(workItem: WorkItem): WorkItemAgentLoopReport["outputRef"] {
  if (workItem.type === "QUALITY_REVIEW") {
    return { kind: "quality", path: `quality/${workItem.id}.json` };
  }
  return { kind: "patch", path: `patches/${workItem.id}.patch.json` };
}

export function contextContractForWorkItem(workItem: WorkItem): WorkItemContextContract {
  if (workItem.type === "CREATE_TOPIC_NOTE" || workItem.type === "REWRITE_TOPIC_NOTE") {
    return {
      workItemType: workItem.type,
      allowedWorkspaceReads: [...workItem.sourcePaths, ...workItem.targetPaths],
      allowedArtifactReads: [],
      requiredShas: Object.fromEntries(
        workItem.targetPaths
          .filter((targetPath) => workItem.baseShas[targetPath])
          .map((targetPath) => [targetPath, workItem.baseShas[targetPath]])
      ),
      forbiddenReads: []
    };
  }
  if (workItem.type === "MAINTAIN_TOPIC_INDEX") {
    return {
      workItemType: workItem.type,
      allowedWorkspaceReads: ["knowledge-base/topics/*/*.md"],
      allowedArtifactReads: ["work-items/*.json", "patches/*.patch.json"],
      requiredShas: {},
      forbiddenReads: ["raw/**"]
    };
  }
  if (workItem.type === "MAINTAIN_MOC") {
    return {
      workItemType: workItem.type,
      allowedWorkspaceReads: ["knowledge-base/topics/*/index.md"],
      allowedArtifactReads: ["work-items/*.json", "patches/*.patch.json"],
      requiredShas: {},
      forbiddenReads: ["raw/**"]
    };
  }
  if (workItem.type === "QUALITY_REVIEW") {
    return {
      workItemType: workItem.type,
      allowedWorkspaceReads: ["knowledge-base/topics/**/*.md"],
      allowedArtifactReads: ["validation/*.json", "work-items/*.json"],
      requiredShas: {},
      forbiddenReads: ["raw/**"]
    };
  }
  return {
    workItemType: workItem.type,
    allowedWorkspaceReads: [],
    allowedArtifactReads: [],
    requiredShas: {},
    forbiddenReads: ["raw/**", "knowledge-base/**"]
  };
}
