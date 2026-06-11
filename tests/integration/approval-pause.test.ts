import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "approval-pause-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("plan approval pause contract", () => {
  it("returns without executing Phase A when plan approval is pending", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-approval",
      autoApprove: false
    });

    expect(result.status).toBe("WAITING_PLAN_APPROVAL");
    expect(result.pendingApproval).toEqual({
      type: "PLAN",
      artifactPath: ".agent-runs/run-approval/approvals/plan-approval.json"
    });

    const approval = JSON.parse(
      await readFile(path.join(tempRoot, ".agent-runs/run-approval/approvals/plan-approval.json"), "utf8")
    );
    expect(approval.status).toBe("PENDING");

    await expect(
      readFile(
        path.join(tempRoot, ".agent-runs/run-approval/patches/rewrite-tools-skill-vs-cli-tool-决策.patch.json"),
        "utf8"
      )
    ).rejects.toMatchObject({ code: "ENOENT" });
  });
});
