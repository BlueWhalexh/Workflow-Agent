import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "workflow-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("LangGraph workflow", () => {
  it("stops at plan approval without auto approve", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-test",
      autoApprove: false
    });

    expect(result.status).toBe("WAITING_PLAN_APPROVAL");
    expect(result.planPath).toBe(".agent-runs/run-test/plan.json");
  });

  it("executes mock note agent with auto approve", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-test",
      autoApprove: true
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-test/report.md");
  });

  it("executes with explicit fake provider runtime config", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-provider-runtime",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-provider-runtime/report.md");
  });
});
