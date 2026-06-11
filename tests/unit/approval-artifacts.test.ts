import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { writePlanApproval } from "../../src/runtime/langgraph/nodes/approval-node.js";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "approval-artifacts-"));
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("approval artifacts", () => {
  it("writes pending and approved plan approval artifacts", async () => {
    const store = new AgentRunsStore(tempRoot, "run-approval");

    await writePlanApproval({ store, status: "PENDING" });
    await expect(store.readJson("approvals/plan-approval.json")).resolves.toMatchObject({
      type: "PLAN_APPROVAL",
      status: "PENDING"
    });

    await writePlanApproval({ store, status: "APPROVED", approvedAt: "2026-06-11T00:00:00.000Z" });
    await expect(store.readJson("approvals/plan-approval.json")).resolves.toMatchObject({
      type: "PLAN_APPROVAL",
      status: "APPROVED",
      approvedAt: "2026-06-11T00:00:00.000Z"
    });
  });
});
