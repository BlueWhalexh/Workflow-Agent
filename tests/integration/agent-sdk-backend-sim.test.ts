import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { createKnowledgeWorkflowAgent } from "../../src/index.js";

async function pathExists(absolutePath: string): Promise<boolean> {
  return access(absolutePath).then(
    () => true,
    () => false
  );
}

describe("agent SDK backend simulation", () => {
  it("runs user messages through SDK, runtime, artifact store, and response envelope", async () => {
    const workspaceRoot = await mkdtemp(path.join(os.tmpdir(), "agent-sdk-backend-sim-"));
    try {
      await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), workspaceRoot, {
        recursive: true
      });
      const agent = createKnowledgeWorkflowAgent();

      const answer = await agent.runAgent({
        workspaceRoot,
        runId: "sim-answer",
        message: "总结当前知识库",
        mode: "deterministic-open-agent"
      });
      const draft = await agent.runAgent({
        workspaceRoot,
        runId: "sim-draft",
        message: "生成 agent loop 改进草稿",
        mode: "deterministic-open-agent"
      });
      const candidate = await agent.runAgent({
        workspaceRoot,
        runId: "sim-candidate",
        message: "准备 agent loop 改进候选落库",
        mode: "llm-open-agent",
        providerRuntime: { provider: "fake", timeoutMs: 30000 }
      });
      const confirmation = await agent.runAgent({
        workspaceRoot,
        runId: "sim-confirm",
        message: "把这个整理一下落库"
      });
      const preview = await agent.runAgent({
        workspaceRoot,
        runId: "sim-preview",
        message: "整理全部知识库",
        mode: "fixed-workflow",
        execute: false
      });
      const fixed = await agent.runAgent({
        workspaceRoot,
        runId: "sim-fixed",
        message: "整理全部知识库",
        mode: "fixed-workflow",
        execute: true,
        autoApprove: true,
        providerRuntime: { provider: "fake", timeoutMs: 30000 }
      });

      expect(answer.outputKind).toBe("answer");
      expect(draft.outputKind).toBe("draft");
      expect(candidate.outputKind).toBe("candidate-patch");
      expect(confirmation.outputKind).toBe("confirmation");
      expect(preview.outputKind).toBe("route-preview");
      expect(fixed.outputKind).toBe("workflow-report");

      const answerArtifact = path.join(workspaceRoot, answer.artifacts.artifactPath ?? "");
      const candidateArtifact = path.join(workspaceRoot, candidate.artifacts.artifactPath ?? "");
      expect(await pathExists(answerArtifact)).toBe(true);
      expect(await pathExists(candidateArtifact)).toBe(true);
      expect(await readFile(answerArtifact, "utf8")).toContain("open-agent-runtime.v1");
      expect(await readFile(candidateArtifact, "utf8")).toContain("open-agent-graph.v1");

      expect(answer.artifacts.wroteWorkspace).toBe(false);
      expect(draft.artifacts.wroteWorkspace).toBe(false);
      expect(candidate.artifacts.wroteWorkspace).toBe(false);
      expect(confirmation.artifacts.wroteWorkspace).toBe(false);
      expect(preview.artifacts.wroteWorkspace).toBe(false);
      expect(fixed.artifacts.wroteWorkspace).toBe(true);
      expect(fixed.artifacts.reportPath).toBe(".agent-runs/sim-fixed/report.md");
      expect(candidate.artifacts.targetWorkspacePaths).toEqual(["knowledge-base/drafts/sim-candidate.md"]);
      expect(await pathExists(path.join(workspaceRoot, "knowledge-base/drafts/sim-candidate.md"))).toBe(false);
    } finally {
      await rm(workspaceRoot, { recursive: true, force: true });
    }
  });
});
