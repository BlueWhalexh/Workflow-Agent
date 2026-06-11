import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createMemoryCheckpointStore } from "../../src/runtime/langgraph/checkpoint-store.js";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "checkpoint-resume-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("runtime checkpoint store", () => {
  it("uses the same MemorySaver across calls for one run thread", async () => {
    const checkpointStore = createMemoryCheckpointStore();

    const waiting = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-checkpoint",
      autoApprove: false,
      checkpointStore
    });

    expect(waiting.status).toBe("WAITING_PLAN_APPROVAL");

    const approved = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-checkpoint",
      autoApprove: true,
      checkpointStore
    });

    expect(approved.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(approved.reportPath).toBe(".agent-runs/run-checkpoint/report.md");
  });
});
