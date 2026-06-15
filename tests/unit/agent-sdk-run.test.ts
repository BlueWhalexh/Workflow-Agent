import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  createKnowledgeWorkflowAgent,
  runAgent,
  runOpenAgentGraph,
  runOpenAgentTask,
  runOrganize,
  type AgentSdkOutputKind,
  type AgentSdkRunResult,
  type AgentSdkRunStatus,
  type RunAgentRequest
} from "../../src/index.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "agent-sdk-run-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("agent SDK unified run entry", () => {
  it("exports runAgent and stable public types", () => {
    expect(typeof createKnowledgeWorkflowAgent).toBe("function");
    expect(typeof runAgent).toBe("function");

    const request: RunAgentRequest = {
      workspaceRoot: tempRoot,
      message: "总结当前知识库",
      mode: "deterministic-open-agent"
    };
    const status: AgentSdkRunStatus = "SUCCEEDED";
    const outputKind: AgentSdkOutputKind = "answer";
    const result = null as AgentSdkRunResult | null;

    expect(request.mode).toBe("deterministic-open-agent");
    expect(status).toBe("SUCCEEDED");
    expect(outputKind).toBe("answer");
    expect(result).toBeNull();
  });

  it("returns a normalized read-only answer from deterministic open-agent", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-answer",
      message: "总结当前知识库",
      mode: "deterministic-open-agent"
    });

    expect(result).toMatchObject({
      schemaVersion: "agent-sdk-run.v1",
      runId: "sdk-answer",
      status: "SUCCEEDED",
      outputKind: "answer",
      capabilityId: "agent.openTask"
    });
    expect(result.output?.answer).toContain("Sources:");
    expect(result.artifacts).toMatchObject({
      artifactRoot: ".agent-runs/open-agent",
      artifactPath: ".agent-runs/open-agent/sdk-answer.json",
      rawProviderRefs: [],
      wroteWorkspace: false,
      targetWorkspacePaths: []
    });
    expect(result.diagnostics.providerBacked).toBe(false);
  });

  it("rejects unsafe run ids before creating artifacts", async () => {
    await expect(
      runAgent({
        workspaceRoot: tempRoot,
        runId: "../escape",
        message: "总结当前知识库",
        mode: "deterministic-open-agent"
      })
    ).rejects.toThrow("artifact id must be a safe slug");
    await expect(access(path.join(tempRoot, "..", "escape.json"))).rejects.toThrow();
  });

  it("rejects unsafe task ids in advanced artifact-writing open-agent APIs", async () => {
    await expect(
      runOpenAgentTask({
        workspaceRoot: tempRoot,
        taskId: "../escape",
        objective: "总结当前知识库",
        methodologyId: "lmwiki-v1",
        risk: "READ_ONLY",
        outputPolicy: "ANSWER_ONLY",
        allowedToolNames: ["workspace.scan"],
        blockedToolNames: ["patch.publish"]
      })
    ).rejects.toThrow("artifact id must be a safe slug");
    await expect(
      runOpenAgentGraph({
        workspaceRoot: tempRoot,
        taskId: "../escape",
        message: "总结当前知识库",
        methodologyId: "lmwiki-v1",
        outputPolicy: "ANSWER_ONLY"
      })
    ).rejects.toThrow("artifact id must be a safe slug");
  });

  it("rejects unsafe run ids in advanced fixed workflow API", async () => {
    await expect(
      runOrganize({
        workspaceRoot: tempRoot,
        runId: "../escape",
        instruction: "整理全部知识库",
        autoApprove: true,
        providerRuntime: { provider: "fake", timeoutMs: 30000 }
      })
    ).rejects.toThrow("artifact id must be a safe slug");
  });

  it("returns a normalized draft artifact from deterministic open-agent", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-draft",
      message: "生成 agent loop 改进草稿",
      mode: "deterministic-open-agent"
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.outputKind).toBe("draft");
    expect(result.output?.draftArtifact?.content).toContain("Draft only");
    expect(result.artifacts.wroteWorkspace).toBe(false);
  });

  it("returns a candidate patch proposal without writing the target workspace file", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-candidate",
      message: "准备 agent loop 改进候选落库",
      mode: "llm-open-agent",
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.outputKind).toBe("candidate-patch");
    expect(result.output?.candidatePatch?.publishable).toBe(false);
    expect(result.artifacts.wroteWorkspace).toBe(false);
    expect(result.artifacts.targetWorkspacePaths).toEqual(["knowledge-base/drafts/sdk-candidate.md"]);
    await expect(access(path.join(tempRoot, "knowledge-base/drafts/sdk-candidate.md"))).rejects.toThrow();
  });

  it("returns provider-backed graph diagnostics and raw provider refs", async () => {
    const responses = [
      {
        objective: "总结当前 agent loop 的状态",
        outputPolicy: "ANSWER_ONLY",
        steps: ["scan workspace", "answer"],
        contextHints: ["raw/agent", "knowledge-base"]
      },
      {
        action: "SOLVED",
        summary: "context is enough"
      },
      {
        kind: "ANSWER",
        answer: "Agent loop 已经具备 SDK facade、StateGraph runner 和 artifact evidence。\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
        groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
      }
    ];
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-provider-answer",
      message: "总结当前 agent loop 的状态",
      mode: "llm-open-agent",
      providerRuntime: {
        provider: "mimo-real",
        baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
        model: "mimo-v2.5",
        apiKeyEnvName: "MIMO_API_KEY",
        timeoutMs: 30000
      },
      providerRuntimeDependencies: {
        env: { MIMO_API_KEY: "test-api-key" },
        fetch: async () => {
          const next = responses.shift();
          if (!next) {
            throw new Error("unexpected extra provider call");
          }
          return new Response(
            JSON.stringify({
              id: "chatcmpl-test",
              choices: [{ message: { role: "assistant", content: JSON.stringify(next) } }]
            }),
            { status: 200, headers: { "content-type": "application/json" } }
          );
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.outputKind).toBe("answer");
    expect(result.diagnostics.providerBacked).toBe(true);
    expect(result.diagnostics.providerRuntime).toBe("mimo-real");
    expect(result.artifacts.rawProviderRefs.filter((ref) => ref.endsWith("/request.json"))).toHaveLength(3);
    expect(result.artifacts.rawProviderRefs.filter((ref) => ref.endsWith("/response.json"))).toHaveLength(3);
    expect(result.artifacts.wroteWorkspace).toBe(false);
    for (const artifactRef of result.artifacts.rawProviderRefs) {
      const artifact = await readFile(path.join(tempRoot, artifactRef), "utf8");
      if (artifactRef.endsWith("/request.json")) {
        expect(artifact).toContain("[REDACTED]");
      }
      expect(artifact).not.toContain("test-api-key");
    }
  });

  it("returns confirmation when the command implies workspace write without fixed scope", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-confirm",
      message: "把这个整理一下落库"
    });

    expect(result.status).toBe("NEEDS_CONFIRMATION");
    expect(result.outputKind).toBe("confirmation");
    expect(result.output?.confirmation?.required).toBe(true);
    expect(result.artifacts.wroteWorkspace).toBe(false);
  });

  it("returns a fixed workflow route preview without executing", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-preview",
      message: "整理全部知识库",
      mode: "fixed-workflow",
      execute: false
    });

    expect(result.status).toBe("WAITING_APPROVAL");
    expect(result.outputKind).toBe("route-preview");
    expect(result.route.lane).toBe("FIXED_WORKFLOW");
    expect(result.artifacts.wroteWorkspace).toBe(false);
  });

  it("executes fixed workflow through the unified SDK entry", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-fixed",
      message: "整理全部知识库",
      mode: "fixed-workflow",
      execute: true,
      autoApprove: true,
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.outputKind).toBe("workflow-report");
    expect(result.output?.workflow?.reportPath).toBe(".agent-runs/sdk-fixed/report.md");
    expect(result.artifacts.artifactRoot).toBe(".agent-runs/sdk-fixed");
    expect(result.artifacts.reportPath).toBe(".agent-runs/sdk-fixed/report.md");
    expect(result.artifacts.wroteWorkspace).toBe(true);
  });

  it("does not report a workspace write when fixed workflow resume skips published work", async () => {
    const first = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-fixed-skip",
      message: "整理全部知识库",
      mode: "fixed-workflow",
      execute: true,
      autoApprove: true,
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });
    const second = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-fixed-skip",
      message: "整理全部知识库",
      mode: "fixed-workflow",
      execute: true,
      autoApprove: true,
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });

    expect(first.artifacts.wroteWorkspace).toBe(true);
    expect(second.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(second.artifacts.wroteWorkspace).toBe(false);
  });

  it("fails fixed-workflow mode when the message does not match a fixed workflow", async () => {
    const result = await runAgent({
      workspaceRoot: tempRoot,
      runId: "sdk-fixed-route-fail",
      message: "总结当前知识库",
      mode: "fixed-workflow"
    });

    expect(result.status).toBe("FAILED_ROUTE");
    expect(result.outputKind).toBe("none");
    expect(result.artifacts.wroteWorkspace).toBe(false);
  });
});
