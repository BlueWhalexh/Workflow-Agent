import { describe, expect, it } from "vitest";
import { runWorkItemRuntimeBoundary } from "../../src/agents/work-item-runtime-boundary.js";
import type { WorkItem } from "../../src/domain/planning/work-item.js";

const workItem: WorkItem = {
  id: "rewrite-tools",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/tools/a.md"],
  targetPaths: ["knowledge-base/topics/tools/a.md"],
  baseShas: {},
  risk: "LOW",
  requiresApproval: false,
  reason: "test",
  attempts: []
};

describe("work item runtime boundary", () => {
  it("rejects invalid loop output before writing a publishable patch", async () => {
    const result = await runWorkItemRuntimeBoundary({
      runId: "run-loop",
      workspaceRoot: "/tmp/workspace",
      workItem,
      runtime: {
        type: "REWRITE_TOPIC_NOTE",
        async buildContext() {
          return { ok: true };
        },
        async runLoop() {
          return {
            output: null,
            report: {
              schemaVersion: "work-item-agent-loop.v1",
              runId: "run-loop",
              workItemId: "rewrite-tools",
              workItemType: "REWRITE_TOPIC_NOTE",
              agentNode: "note",
              status: "SUCCEEDED",
              budget: { maxIterations: 2, maxProviderCalls: 1, timeoutMs: 30000 },
              usage: { iterations: 1, providerCalls: 1 },
              steps: [],
              repairedIssues: [],
              remainingIssues: []
            }
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_EXECUTOR");
    expect(result.latestAttempt).toMatchObject({
      failureSource: "loop",
      failureReason: "LOOP_OUTPUT_SCHEMA_INVALID",
      retryable: false
    });
    expect(result.publishableArtifactWritten).toBe(false);
  });

  it("rejects loop reports that exceed their declared budget", async () => {
    const result = await runWorkItemRuntimeBoundary({
      runId: "run-loop",
      workspaceRoot: "/tmp/workspace",
      workItem,
      runtime: {
        type: "REWRITE_TOPIC_NOTE",
        async buildContext() {
          return { ok: true };
        },
        async runLoop() {
          return {
            output: { workItemId: "rewrite-tools", files: [] },
            report: {
              schemaVersion: "work-item-agent-loop.v1",
              runId: "run-loop",
              workItemId: "rewrite-tools",
              workItemType: "REWRITE_TOPIC_NOTE",
              agentNode: "note",
              status: "SUCCEEDED",
              budget: { maxIterations: 2, maxProviderCalls: 1, timeoutMs: 30000 },
              usage: { iterations: 3, providerCalls: 1 },
              steps: [],
              repairedIssues: [],
              remainingIssues: []
            }
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_EXECUTOR");
    expect(result.latestAttempt).toMatchObject({
      failureSource: "loop",
      failureReason: "LOOP_BUDGET_EXCEEDED",
      retryable: false
    });
  });
});
