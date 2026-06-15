import { promises as fs } from "node:fs";
import path from "node:path";
import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import type { OrganizePlan } from "../domain/planning/plan.js";
import type { WorkItem, WorkItemStatus } from "../domain/planning/work-item.js";
import { inspectResumeWorkItem, type ResumeInspection } from "../domain/validation/resume-inspector.js";
import { AgentRunsStore } from "../storage/agent-runs-store.js";

export interface InspectRunRequest {
  workspaceRoot: string;
  runId?: string;
}

export interface InspectRunResult {
  workspaceRoot: string;
  runId: string | null;
  status: "FOUND" | "NO_RUNS_FOUND";
  artifactRoot?: string;
  methodology?: {
    id: string;
    version: string;
  };
  plan?: OrganizePlan;
  eval?: {
    methodology?: {
      id: string;
      version: string;
    };
    agentLoop?: {
      missingArtifacts?: string[];
      corruptArtifacts?: string[];
    };
    [key: string]: unknown;
  };
  reportPath?: string;
  workItemStatuses: Record<string, WorkItemStatus>;
  decisions: ResumeInspection[];
}

async function readJsonOrNull<T>(store: AgentRunsStore, relativePath: string): Promise<T | null> {
  return store.readJson<T>(relativePath).catch((error: unknown) => {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    throw error;
  });
}

async function readWorkItems(store: AgentRunsStore): Promise<WorkItem[]> {
  try {
    const entries = await fs.readdir(store.artifactPath("work-items"));
    return Promise.all(
      entries
        .filter((entry) => entry.endsWith(".json"))
        .map(async (entry) => JSON.parse(await fs.readFile(store.artifactPath(`work-items/${entry}`), "utf8")) as WorkItem)
    );
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return [];
    }
    throw error;
  }
}

export async function inspectRun(request: InspectRunRequest): Promise<InspectRunResult> {
  const runId = request.runId ?? (await AgentRunsStore.latestRunId(request.workspaceRoot));
  if (!runId) {
    return {
      workspaceRoot: request.workspaceRoot,
      runId: null,
      status: "NO_RUNS_FOUND",
      workItemStatuses: {},
      decisions: []
    };
  }

  const store = new AgentRunsStore(request.workspaceRoot, runId);
  const plan = await readJsonOrNull<OrganizePlan>(store, "plan.json");
  const evalReport = await readJsonOrNull<InspectRunResult["eval"]>(store, "eval.json");
  const workItems = await readWorkItems(store);
  const workItemStatuses = Object.fromEntries(workItems.map((item) => [item.id, item.status]));
  const decisions = await Promise.all(
    workItems.map(async (workItem) => {
      const latestAttempt = workItem.attempts.at(-1);
      const patch = await readJsonOrNull<PatchBundle>(store, `patches/${workItem.id}.patch.json`);
      return inspectResumeWorkItem({
        workspaceRoot: request.workspaceRoot,
        workItem: {
          id: workItem.id,
          status: workItem.status,
          targetPaths: workItem.targetPaths,
          contentSha: patch?.files[0]?.contentSha,
          retryable: latestAttempt?.retryable
        }
      });
    })
  );

  return {
    workspaceRoot: request.workspaceRoot,
    runId,
    status: "FOUND",
    artifactRoot: path.posix.join(".agent-runs", runId),
    methodology: plan
      ? {
          id: plan.methodologyId,
          version: plan.methodologyVersion
        }
      : evalReport?.methodology,
    plan: plan ?? undefined,
    eval: evalReport ?? undefined,
    reportPath: path.posix.join(".agent-runs", runId, "report.md"),
    workItemStatuses,
    decisions
  };
}
