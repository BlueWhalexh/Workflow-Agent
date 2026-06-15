import { promises as fs } from "node:fs";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import type { PatchBundle } from "../../../domain/patch/patch-bundle.js";
import type { OrganizePlan } from "../../../domain/planning/plan.js";
import type { WorkItem } from "../../../domain/planning/work-item.js";
import type { ValidationResult } from "../../../domain/validation/validator.js";
import type { GraphState } from "../state.js";
import {
  loopBudgetExceeded,
  type WorkItemAgentLoopReport,
  type WorkItemAgentNode
} from "../../../agents/work-item-agent-runtime.js";

interface QualityFindingsArtifact {
  issues: string[];
}

interface AgentLoopArtifactsRead {
  reports: WorkItemAgentLoopReport[];
  corruptArtifacts: string[];
}

async function readJsonFiles<T>(store: AgentRunsStore, relativeDir: string): Promise<T[]> {
  try {
    const entries = await fs.readdir(store.artifactPath(relativeDir));
    return Promise.all(
      entries
        .filter((entry) => entry.endsWith(".json"))
        .map(async (entry) => JSON.parse(await fs.readFile(store.artifactPath(`${relativeDir}/${entry}`), "utf8")) as T)
    );
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return [];
    }
    throw error;
  }
}

async function readAgentLoopArtifacts(store: AgentRunsStore): Promise<AgentLoopArtifactsRead> {
  try {
    const entries = await fs.readdir(store.artifactPath("agent-loop"));
    const reports: WorkItemAgentLoopReport[] = [];
    const corruptArtifacts: string[] = [];
    for (const entry of entries.filter((item) => item.endsWith(".json"))) {
      try {
        reports.push(
          JSON.parse(await fs.readFile(store.artifactPath(`agent-loop/${entry}`), "utf8")) as WorkItemAgentLoopReport
        );
      } catch {
        corruptArtifacts.push(entry);
      }
    }
    return { reports, corruptArtifacts };
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return { reports: [], corruptArtifacts: [] };
    }
    throw error;
  }
}

export async function reportNode(state: GraphState): Promise<Partial<GraphState>> {
  if (!state.autoApprove) {
    return {};
  }
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  if (state.status === "FAILED") {
    const storedWorkItems = await readJsonFiles<WorkItem>(store, "work-items");
    const needsReplanItems = storedWorkItems.filter((item) => item.status === "NEEDS_REPLAN");
    const needsReplanSection =
      needsReplanItems.length === 0
        ? ""
        : `
- Needs replan:
${needsReplanItems.map((item) => `  - ${item.id}`).join("\n")}
`;
    await store.writeText(
      "report.md",
      `# Agent Run Report

- Status: FAILED
- Methodology: ${state.methodologyId}@unknown
- Error: ${state.lastError ?? "UNKNOWN"}
${needsReplanSection}
`
    );
    return {
      status: "FAILED",
      reportPath: `.agent-runs/${state.runId}/report.md`,
      lastError: state.lastError
    };
  }

  const plan = await store.readJson<OrganizePlan>("plan.json");
  const bundles = await readJsonFiles<PatchBundle>(store, "patches");
  const validationArtifacts = await readJsonFiles<{ validation: ValidationResult }>(store, "validation");
  const qualityArtifacts = await readJsonFiles<QualityFindingsArtifact>(store, "quality");
  const agentLoopArtifacts = await readAgentLoopArtifacts(store);
  const storedWorkItems = await readJsonFiles<WorkItem>(store, "work-items");
  const storedStatuses = new Map(storedWorkItems.map((item) => [item.id, item.status]));
  const rawFilesSeen = new Set(bundles.flatMap((bundle) => bundle.eval.rawFilesSeen));
  const publishedNoteBundles = bundles.filter((bundle) => {
    const status = storedStatuses.get(bundle.workItemId);
    const writesTopicNote = bundle.files.some(
      (file) => file.path.startsWith("knowledge-base/topics/") && !file.path.endsWith("/index.md")
    );
    return status === "PUBLISHED" && writesTopicNote;
  });
  const rawMirrorConverted = bundles.filter((bundle) => bundle.eval.rawMirrorConverted).length;
  const qualityIssues = [
    ...validationArtifacts.flatMap((artifact) => artifact.validation.qualityIssues),
    ...qualityArtifacts.flatMap((artifact) => artifact.issues)
  ];
  const workItemStatuses = Object.fromEntries(
    plan.workItems.map((item) => [item.id, storedStatuses.get(item.id) ?? item.status])
  );
  const loopByWorkItemId = new Map(agentLoopArtifacts.reports.map((report) => [report.workItemId, report]));
  const missingArtifacts = plan.workItems
    .filter((item) => !loopByWorkItemId.has(item.id))
    .map((item) => item.id);
  const byNode = agentLoopArtifacts.reports.reduce(
    (accumulator, report) => ({
      ...accumulator,
      [report.agentNode]: (accumulator[report.agentNode] ?? 0) + 1
    }),
    {} as Partial<Record<WorkItemAgentNode, number>>
  );
  const budgetExceeded = agentLoopArtifacts.reports
    .filter((report) => loopBudgetExceeded(report))
    .map((report) => report.workItemId);

  await store.writeJson("eval.json", {
    methodology: {
      id: plan.methodologyId,
      version: plan.methodologyVersion
    },
    rawCoverage: {
      total: plan.workspaceSnapshot.rawCount,
      seen: rawFilesSeen.size
    },
    pagesRewritten: publishedNoteBundles.length,
    rawMirrorConverted,
    qualityIssues,
    agentLoop: {
      total: plan.workItems.length,
      reports: agentLoopArtifacts.reports.length,
      byNode,
      providerCalls: agentLoopArtifacts.reports.reduce((total, report) => total + report.usage.providerCalls, 0),
      repairedIssues: agentLoopArtifacts.reports.flatMap((report) => report.repairedIssues),
      remainingIssues: agentLoopArtifacts.reports.flatMap((report) => report.remainingIssues),
      budgetExceeded,
      missingArtifacts,
      corruptArtifacts: agentLoopArtifacts.corruptArtifacts
    },
    workItemStatuses
  });
  await store.writeText(
    "report.md",
    `# Agent Run Report

- Status: SUCCEEDED_WITH_WARNINGS
- Methodology: ${plan.methodologyId}@${plan.methodologyVersion}
- Pages rewritten: ${publishedNoteBundles.length}
- Raw mirror converted: ${rawMirrorConverted}
- Agent loop artifacts: ${agentLoopArtifacts.reports.length}/${plan.workItems.length}
`
  );
  return {
    status: "SUCCEEDED_WITH_WARNINGS",
    reportPath: `.agent-runs/${state.runId}/report.md`
  };
}
