import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOpenAgentTask } from "../../src/index.js";

let tempRoot: string;

async function fileExists(filePath: string): Promise<boolean> {
  return access(filePath)
    .then(() => true)
    .catch(() => false);
}

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "open-agent-runtime-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("open agent runtime", () => {
  it("produces a read-only answer artifact with workspace context", async () => {
    const result = await runOpenAgentTask({
      workspaceRoot: tempRoot,
      taskId: "task-answer",
      objective: "根据知识库总结 AI 相关知识",
      risk: "READ_ONLY",
      outputPolicy: "ANSWER_ONLY",
      allowedToolNames: ["workspace.scan", "artifact.readEval"],
      blockedToolNames: ["patch.publish"]
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.answer).toContain("根据知识库总结 AI 相关知识");
    expect(result.answer).toContain("lmwiki-v1");
    expect(result.draftArtifact).toBeUndefined();
    expect(result.artifactPath).toBe(".agent-runs/open-agent/task-answer.json");
    expect(result.report.steps.map((step) => step.name)).toEqual([
      "PLAN",
      "GATHER_CONTEXT",
      "PRODUCE_OUTPUT",
      "SELF_CHECK"
    ]);
    expect(result.report.context.methodology).toEqual({ id: "lmwiki-v1", version: "1" });
    expect(result.report.context.rawFiles.length).toBeGreaterThan(0);
    expect(result.report.context.knowledgePages.length).toBeGreaterThan(0);
    expect(result.report.groundingRefs.length).toBeGreaterThan(0);
    expect(result.report.toolCalls.map((call) => call.name)).toEqual([
      "methodology.read",
      "workspace.scan",
      "open-agent.output",
      "open-agent.selfCheck"
    ]);
    expect(result.answer).toContain("Sources:");

    const artifactPath = path.join(tempRoot, result.artifactPath);
    expect(await fileExists(artifactPath)).toBe(true);
    const artifact = JSON.parse(await readFile(artifactPath, "utf8")) as typeof result.report;
    expect(artifact.schemaVersion).toBe("open-agent-runtime.v1");
    expect(artifact.outputRef.kind).toBe("answer");
  });

  it("produces a draft artifact without writing knowledge-base files", async () => {
    const targetKnowledgePath = path.join(tempRoot, "knowledge-base/topics/generated-question-list.md");
    const result = await runOpenAgentTask({
      workspaceRoot: tempRoot,
      taskId: "task-draft",
      objective: "生成一份 AI 八股文问题清单",
      risk: "DRAFT_ONLY",
      outputPolicy: "DRAFT_ARTIFACT",
      allowedToolNames: ["workspace.scan", "artifact.readEval"],
      blockedToolNames: ["patch.publish"]
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.answer).toBeUndefined();
    expect(result.draftArtifact?.title).toContain("AI 八股文问题清单");
    expect(result.draftArtifact?.content).toContain("Draft only");
    expect(result.report.outputRef.kind).toBe("draft");
    expect(result.report.toolPolicy.allowedToolNames).not.toContain("patch.publish");
    expect(await fileExists(targetKnowledgePath)).toBe(false);
  });

  it("proposes a candidate patch without writing knowledge-base files", async () => {
    const targetKnowledgePath = path.join(tempRoot, "knowledge-base/drafts/task-candidate-patch.md");
    const result = await runOpenAgentTask({
      workspaceRoot: tempRoot,
      taskId: "task-candidate-patch",
      objective: "生成一份 AI 八股文问题清单并准备候选落库",
      risk: "DRAFT_ONLY",
      outputPolicy: "CANDIDATE_PATCH",
      allowedToolNames: ["workspace.scan", "artifact.readEval", "patch.validate"],
      blockedToolNames: ["patch.publish"]
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.answer).toBeUndefined();
    expect(result.draftArtifact).toBeUndefined();
    expect(result.candidatePatch).toMatchObject({
      kind: "CANDIDATE_PATCH_PROPOSAL",
      publishable: false,
      targetPaths: ["knowledge-base/drafts/task-candidate-patch.md"],
      handoff: {
        type: "FIXED_WORKFLOW",
        capabilityId: "workflow.organizeWorkspace",
        confirmationRequired: true,
        executeRequired: true
      }
    });
    expect(result.candidatePatch?.files[0].content).toContain("Candidate patch only");
    expect(result.report.outputRef.kind).toBe("candidate-patch");
    expect(result.report.candidatePatch?.publishable).toBe(false);
    expect(await fileExists(targetKnowledgePath)).toBe(false);

    const artifact = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as typeof result.report;
    expect(artifact.candidatePatch?.targetPaths).toEqual(["knowledge-base/drafts/task-candidate-patch.md"]);
  });

  it("fails policy when workspace publish tool is allowed", async () => {
    const result = await runOpenAgentTask({
      workspaceRoot: tempRoot,
      taskId: "task-policy-failure",
      objective: "生成并发布一份清单",
      risk: "DRAFT_ONLY",
      outputPolicy: "DRAFT_ARTIFACT",
      allowedToolNames: ["workspace.scan", "patch.publish"],
      blockedToolNames: []
    });

    expect(result.status).toBe("FAILED_POLICY");
    expect(result.answer).toBeUndefined();
    expect(result.draftArtifact).toBeUndefined();
    expect(result.report.steps.at(-1)?.status).toBe("FAILED");
    expect(result.report.toolPolicy.allowedToolNames).toContain("patch.publish");
  });
});
