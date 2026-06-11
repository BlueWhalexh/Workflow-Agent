import { describe, expect, it } from "vitest";
import { runMockNoteAgent } from "../../src/agents/mock-note-agent.js";

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
