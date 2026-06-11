import path from "node:path";
import { describe, expect, it } from "vitest";
import { createOrganizePlan } from "../../src/domain/planning/organize-planner.js";
import { scanWorkspace } from "../../src/domain/workspace/inventory.js";

const fixtureRoot = path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror");

describe("organize planner", () => {
  it("creates a semi-automatic three-phase plan", async () => {
    const inventory = await scanWorkspace({ workspaceRoot: fixtureRoot });
    const plan = createOrganizePlan({
      runId: "run-test",
      instruction: "整理全部知识库",
      inventory
    });

    expect(plan.mode).toBe("SEMI_AUTOMATIC");
    expect(plan.approval.status).toBe("PENDING");
    expect(plan.phases.map((phase) => phase.id)).toEqual([
      "phase-a-notes",
      "phase-b-indexes",
      "phase-c-global"
    ]);
    expect(plan.workItems.some((item) => item.type === "REWRITE_TOPIC_NOTE")).toBe(true);
    expect(plan.workItems.some((item) => item.type === "CREATE_TOPIC_NOTE")).toBe(true);
    expect(plan.workItems.some((item) => item.targetPaths.includes("knowledge-base/topics/tools/index.md"))).toBe(true);
    expect(plan.workItems.some((item) => item.targetPaths.some((targetPath) => targetPath.includes(".md/index.md")))).toBe(
      false
    );
    expect(plan.workItems.some((item) => item.type === "MAINTAIN_MOC")).toBe(true);
  });
});
