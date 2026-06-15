import { describe, expect, it } from "vitest";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { runMockNoteAgent } from "../../src/agents/mock-note-agent.js";
import { runMockTopicIndexAgent } from "../../src/agents/mock-topic-index-agent.js";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

describe("mock note agent", () => {
  it("emits a valid organized topic note patch bundle", async () => {
    const bundle = await runMockNoteAgent({
      runId: "run-test",
      workItem: {
        id: "rewrite-tools",
        type: "REWRITE_TOPIC_NOTE",
        phase: "phase-a-notes",
        status: "PLANNED",
        sourcePaths: ["raw/tools/Skill vs CLI Tool 决策.md"],
        targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
        baseShas: {
          "raw/tools/Skill vs CLI Tool 决策.md": "abc",
          "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md": "def"
        },
        risk: "LOW",
        requiresApproval: false,
        reason: "existing page is bootstrap raw mirror",
        attempts: [],
        publishPolicy: "AUTO_PUBLISH"
      },
      sourceContent: "# Skill vs CLI Tool 决策\n\n## 决策\n\nSkill 适合流程，CLI 适合确定性动作。"
    });

    expect(bundle.files[0].content).toContain("<!-- agent-meta");
    expect(bundle.files[0].content).toContain("## 摘要");
    expect(bundle.files[0].content).toContain("## 来源追踪");
    expect(bundle.eval.rawMirrorConverted).toBe(true);
  });
});

describe("mock topic index agent", () => {
  it("emits loop evidence for deterministic topic index patches", async () => {
    const tempRoot = await mkdtemp(path.join(os.tmpdir(), "topic-index-agent-"));
    const store = new AgentRunsStore(tempRoot, "run-test");
    try {
      const bundle = await runMockTopicIndexAgent({
        runId: "run-test",
        workItem: {
          id: "maintain-tools-index",
          type: "MAINTAIN_TOPIC_INDEX",
          phase: "phase-b-indexes",
          status: "PLANNED",
          sourcePaths: [],
          targetPaths: ["knowledge-base/topics/tools/index.md"],
          baseShas: {},
          risk: "LOW",
          requiresApproval: false,
          reason: "maintain topic index",
          attempts: []
        },
        sourceContent: "",
        store
      });

      expect(bundle.files[0].path).toBe("knowledge-base/topics/tools/index.md");
      const loop = JSON.parse(
        await readFile(path.join(tempRoot, ".agent-runs/run-test/agent-loop/maintain-tools-index.json"), "utf8")
      ) as { schemaVersion: string; agentNode: string; outputRef: { path: string } };
      expect(loop.schemaVersion).toBe("work-item-agent-loop.v1");
      expect(loop.agentNode).toBe("topic-index");
      expect(loop.outputRef.path).toBe("patches/maintain-tools-index.patch.json");
    } finally {
      await rm(tempRoot, { recursive: true, force: true });
    }
  });
});
