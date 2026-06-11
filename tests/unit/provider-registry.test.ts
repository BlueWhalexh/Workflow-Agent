import { describe, expect, it } from "vitest";
import { selectNoteProvider } from "../../src/runtime/provider/provider-registry.js";
import type { WorkItem } from "../../src/domain/planning/work-item.js";

const workItem: WorkItem = {
  id: "rewrite-tools",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/tools/Skill vs CLI Tool 决策.md"],
  targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  baseShas: {
    "raw/tools/Skill vs CLI Tool 决策.md": "source-sha"
  },
  risk: "LOW",
  requiresApproval: false,
  reason: "fixture",
  attempts: [],
  publishPolicy: "AUTO_PUBLISH"
};

describe("provider registry", () => {
  it("defaults to the fake note provider", async () => {
    const provider = selectNoteProvider();

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("fake");
    expect(result.model).toBe("fake-note-model");
  });

  it("selects the fake note provider from runtime config", async () => {
    const provider = selectNoteProvider({ provider: "fake", timeoutMs: 30000 });

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("fake");
  });
});
