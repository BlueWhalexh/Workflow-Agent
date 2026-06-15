import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { handleCommand, runOpenAgentGraph } from "../../src/index.js";

let tempRoot: string;

async function fileExists(filePath: string): Promise<boolean> {
  return access(filePath)
    .then(() => true)
    .catch(() => false);
}

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "open-agent-graph-integration-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("open agent graph integration", () => {
  it("uses provider draft synthesis without writing a knowledge-base draft", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "provider-draft",
      message: "根据知识库生成一份 AI agent 八股文问题清单草稿",
      outputPolicy: "DRAFT_ARTIFACT",
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库生成一份 AI agent 八股文问题清单草稿",
            outputPolicy: "DRAFT_ARTIFACT",
            steps: ["scan workspace", "draft"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return { action: "SOLVED", summary: "Enough context gathered." };
        },
        async synthesize() {
          return {
            kind: "DRAFT_ARTIFACT",
            title: "AI Agent Questions",
            content: "Draft only. Provider draft content.\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.draftArtifact?.content).toContain("Provider draft");
    expect(result.draftArtifact?.content).toContain("Draft only");
    expect(await fileExists(path.join(tempRoot, "knowledge-base/drafts/provider-draft.md"))).toBe(false);
  });

  it("rejects provider draft synthesis without Draft only marker", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "provider-draft-missing-marker",
      message: "根据知识库生成一份 AI agent 八股文问题清单草稿",
      outputPolicy: "DRAFT_ARTIFACT",
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库生成一份 AI agent 八股文问题清单草稿",
            outputPolicy: "DRAFT_ARTIFACT",
            steps: ["scan workspace", "draft"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return { action: "SOLVED", summary: "Enough context gathered." };
        },
        async synthesize() {
          return {
            kind: "DRAFT_ARTIFACT",
            title: "AI Agent Questions",
            content: "Provider draft content.\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_VALIDATION");
  });

  it("uses provider candidate content while runtime controls target, sha, handoff, and no-write", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "provider-candidate",
      message: "准备一份可以落库的候选 AI agent 问题清单，但不要发布",
      outputPolicy: "CANDIDATE_PATCH",
      openAgentProvider: {
        async plan() {
          return {
            objective: "准备一份可以落库的候选 AI agent 问题清单，但不要发布",
            outputPolicy: "CANDIDATE_PATCH",
            steps: ["scan workspace", "candidate"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return { action: "SOLVED", summary: "Enough context gathered." };
        },
        async synthesize() {
          return {
            kind: "CANDIDATE_PATCH",
            title: "AI Agent Questions",
            content: "Provider candidate content.\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
            targetPath: "knowledge-base/drafts/provider-suggested.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.candidatePatch?.publishable).toBe(false);
    expect(result.candidatePatch?.targetPaths).toEqual(["knowledge-base/drafts/provider-candidate.md"]);
    expect(result.candidatePatch?.files[0].content).toContain("Provider candidate");
    expect(result.candidatePatch?.files[0].contentSha).toMatch(/^[a-f0-9]{64}$/);
    expect(result.candidatePatch?.handoff.capabilityId).toBe("workflow.organizeWorkspace");
    expect(await fileExists(path.join(tempRoot, result.candidatePatch!.targetPaths[0]))).toBe(false);

    const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as typeof result & {
      runner?: { kind?: string; version?: number };
    };
    expect(report.runner).toEqual({ kind: "LANGGRAPH_STATEGRAPH", version: 1 });
    expect(report.synthesis).toMatchObject({
      providerBacked: true,
      outputKind: "CANDIDATE_PATCH"
    });
    expect(report.candidatePatch?.publishable).toBe(false);
  });

  it("rejects provider candidate synthesis with target outside knowledge-base", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "provider-candidate-unsafe",
      message: "准备一份可以落库的候选 AI agent 问题清单，但不要发布",
      outputPolicy: "CANDIDATE_PATCH",
      openAgentProvider: {
        async plan() {
          return {
            objective: "准备一份可以落库的候选 AI agent 问题清单，但不要发布",
            outputPolicy: "CANDIDATE_PATCH",
            steps: ["scan workspace", "candidate"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return { action: "SOLVED", summary: "Enough context gathered." };
        },
        async synthesize() {
          return {
            kind: "CANDIDATE_PATCH",
            title: "AI Agent Questions",
            content: "Provider candidate content.\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
            targetPath: "raw/unsafe.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_POLICY");
    expect(await fileExists(path.join(tempRoot, "raw/unsafe.md"))).toBe(false);
  });

  it("rejects provider synthesis refs outside gathered context", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "provider-answer-unsafe-ref",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库总结 AI agent 架构",
            outputPolicy: "ANSWER_ONLY",
            steps: ["scan workspace", "answer"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return { action: "SOLVED", summary: "Enough context gathered." };
        },
        async synthesize() {
          return {
            kind: "ANSWER",
            answer: "Provider answer\n\nSources:\n- raw/unknown.md",
            groundingRefs: ["raw/unknown.md"]
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_VALIDATION");
  });

  it("writes report and trace artifacts without writing candidate target", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-artifact",
      message: "生成一份 AI 八股文问题清单并准备候选落库",
      outputPolicy: "CANDIDATE_PATCH"
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.artifactPath).toBe(".agent-runs/open-agent/graph-artifact.json");
    expect(result.tracePath).toBe(".agent-runs/open-agent/traces/graph-artifact.jsonl");
    expect(await fileExists(path.join(tempRoot, result.artifactPath))).toBe(true);
    expect(await fileExists(path.join(tempRoot, result.tracePath))).toBe(true);
    expect(await fileExists(path.join(tempRoot, "knowledge-base/drafts/graph-artifact.md"))).toBe(false);

    const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as typeof result;
    expect(report.candidatePatch?.publishable).toBe(false);
    expect(report.realExternalCall).toBe(false);
  });

  it("records LangGraph runner metadata in the open agent graph report", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-runner-metadata",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY"
    });

    expect(result.status).toBe("SUCCEEDED");
    const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as {
      runner?: { kind?: string; version?: number };
    };
    expect(report.runner).toEqual({
      kind: "LANGGRAPH_STATEGRAPH",
      version: 1
    });
  });

  it("handleCommand can opt into llm graph while default remains deterministic", async () => {
    const deterministic = await handleCommand({
      workspaceRoot: tempRoot,
      runId: "router-deterministic",
      message: "根据知识库总结 AI agent 架构"
    });
    const graph = await handleCommand({
      workspaceRoot: tempRoot,
      runId: "router-graph",
      message: "根据知识库总结 AI agent 架构",
      openAgentMode: "llm-graph"
    });

    expect(deterministic.openAgent).toBeDefined();
    expect(deterministic.openAgentGraph).toBeUndefined();
    expect(graph.openAgentGraph?.status).toBe("SUCCEEDED");
    expect(graph.openAgent).toBeUndefined();
  });
});
