import { describe, expect, it } from "vitest";
import { runMockNoteAgent } from "../../src/agents/mock-note-agent.js";
import { createFakeNoteProvider } from "../../src/domain/llm-provider/fake-note-provider.js";
import type { LlmNoteProvider } from "../../src/domain/llm-provider/provider.js";
import type { WorkItem } from "../../src/domain/planning/work-item.js";

const workItem: WorkItem = {
  id: "rewrite-tools",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/tools/Skill vs CLI Tool 决策.md"],
  targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  baseShas: {
    "raw/tools/Skill vs CLI Tool 决策.md": "source-sha",
    "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md": "base-sha"
  },
  risk: "LOW",
  requiresApproval: false,
  reason: "fixture",
  attempts: [],
  publishPolicy: "AUTO_PUBLISH"
};

describe("llm provider adapter", () => {
  it("fake note provider returns a deterministic note draft with usage", async () => {
    const provider = createFakeNoteProvider();

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# Skill vs CLI Tool 决策\n"
    });

    expect(result.provider).toBe("fake");
    expect(result.model).toBe("fake-note-model");
    expect(result.finishReason).toBe("stop");
    expect(result.usage).toEqual({ inputTokens: 1, outputTokens: 1, totalTokens: 2 });
    expect(result.content).toContain("state: AGENT_ORGANIZED");
    expect(result.content).toContain("lastRunId: run-provider");
  });

  it("note agent uses an injected provider before building the patch bundle", async () => {
    const provider: LlmNoteProvider = {
      async generateNote() {
        return {
          providerCallId: "custom-call",
          provider: "fake",
          model: "custom-model",
          finishReason: "stop",
          usage: { inputTokens: 2, outputTokens: 3, totalTokens: 5 },
          content: "# Custom\n\n<!-- agent-meta\nstate: AGENT_ORGANIZED\ncontentSha: pending\n-->\n"
        };
      }
    };

    const bundle = await runMockNoteAgent({
      runId: "run-provider",
      workItem,
      sourceContent: "# source",
      provider
    });

    expect(bundle.files[0].content).toContain("# Custom");
    expect(bundle.files[0].content).not.toContain("contentSha: pending");
  });
});
