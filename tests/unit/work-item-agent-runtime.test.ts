import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  buildLoopReport,
  budgetForWorkItemType,
  contextContractForWorkItem,
  writeLoopReport
} from "../../src/agents/work-item-agent-runtime.js";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

describe("work item agent runtime", () => {
  it("uses bounded budgets by work item type", () => {
    expect(budgetForWorkItemType("REWRITE_TOPIC_NOTE")).toEqual({
      maxIterations: 2,
      maxProviderCalls: 1,
      timeoutMs: 30000
    });
    expect(budgetForWorkItemType("MAINTAIN_TOPIC_INDEX")).toEqual({
      maxIterations: 1,
      maxProviderCalls: 0,
      timeoutMs: 5000
    });
    expect(budgetForWorkItemType("MERGE_USER_EDITED_NOTE")).toEqual({
      maxIterations: 0,
      maxProviderCalls: 0,
      timeoutMs: 0
    });
  });

  it("builds work-item-agent-loop.v1 reports", () => {
    const report = buildLoopReport({
      runId: "run-loop",
      workItemId: "rewrite-tools",
      workItemType: "REWRITE_TOPIC_NOTE",
      agentNode: "note",
      status: "SUCCEEDED",
      budget: { maxIterations: 2, maxProviderCalls: 1, timeoutMs: 30000 },
      usage: { iterations: 2, providerCalls: 1 },
      steps: [],
      repairedIssues: ["TOPIC_NOTE_WEAK_RELATIONS"],
      remainingIssues: [],
      outputRef: { kind: "patch", path: "patches/rewrite-tools.patch.json" }
    });

    expect(report.schemaVersion).toBe("work-item-agent-loop.v1");
    expect(report.agentNode).toBe("note");
    expect(report.repairedIssues).toEqual(["TOPIC_NOTE_WEAK_RELATIONS"]);
    expect(report.outputRef).toEqual({ kind: "patch", path: "patches/rewrite-tools.patch.json" });
  });

  it("writes loop reports to the agent-loop directory", async () => {
    const tempRoot = await mkdtemp(path.join(os.tmpdir(), "work-item-agent-runtime-"));
    const store = new AgentRunsStore(tempRoot, "run-loop");
    try {
      await writeLoopReport(
        store,
        buildLoopReport({
          runId: "run-loop",
          workItemId: "quality-review",
          workItemType: "QUALITY_REVIEW",
          agentNode: "quality-review",
          status: "SUCCEEDED_WITH_WARNINGS",
          budget: budgetForWorkItemType("QUALITY_REVIEW"),
          usage: { iterations: 1, providerCalls: 0 },
          steps: [],
          repairedIssues: [],
          remainingIssues: ["TOPIC_NOTE_WEAK_RELATIONS"],
          outputRef: { kind: "quality", path: "quality/quality-review.json" }
        })
      );

      const artifact = JSON.parse(
        await readFile(path.join(tempRoot, ".agent-runs/run-loop/agent-loop/quality-review.json"), "utf8")
      ) as { schemaVersion: string; outputRef: { kind: string } };
      expect(artifact.schemaVersion).toBe("work-item-agent-loop.v1");
      expect(artifact.outputRef.kind).toBe("quality");
    } finally {
      await rm(tempRoot, { recursive: true, force: true });
    }
  });

  it("declares scoped context contracts by work item type", () => {
    const noteContract = contextContractForWorkItem({
      id: "rewrite-tools",
      type: "REWRITE_TOPIC_NOTE",
      phase: "phase-a-notes",
      status: "PLANNED",
      sourcePaths: ["raw/tools/a.md"],
      targetPaths: ["knowledge-base/topics/tools/a.md"],
      baseShas: { "knowledge-base/topics/tools/a.md": "target-sha" },
      risk: "LOW",
      requiresApproval: false,
      reason: "test",
      attempts: []
    });
    expect(noteContract.allowedWorkspaceReads).toEqual(["raw/tools/a.md", "knowledge-base/topics/tools/a.md"]);
    expect(noteContract.requiredShas).toEqual({ "knowledge-base/topics/tools/a.md": "target-sha" });

    const mocContract = contextContractForWorkItem({
      id: "maintain-moc",
      type: "MAINTAIN_MOC",
      phase: "phase-c-global",
      status: "PLANNED",
      sourcePaths: [],
      targetPaths: ["knowledge-base/moc.md"],
      baseShas: {},
      risk: "LOW",
      requiresApproval: false,
      reason: "test",
      attempts: []
    });
    expect(mocContract.allowedWorkspaceReads).toEqual(["knowledge-base/topics/*/index.md"]);
    expect(mocContract.forbiddenReads).toEqual(["raw/**"]);
  });
});
