import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOpenAgentGraph } from "../../src/index.js";

let tempRoot: string;

async function pathExists(absolutePath: string): Promise<boolean> {
  return access(absolutePath).then(
    () => true,
    () => false
  );
}

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "open-agent-graph-nodes-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("open agent graph nodes", () => {
  it("runs open agent orchestration through a StateGraph wrapper", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-stategraph-wrapper",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY"
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.steps[0]).toMatchObject({
      name: "RUNNER",
      status: "SUCCEEDED",
      summary: "Open agent graph invoked through LangGraph StateGraph."
    });
  });

  it("fails validation when provider returns an invalid plan", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-invalid-plan",
      message: "根据知识库总结 AI agent 架构",
      openAgentProvider: {
        async plan() {
          return "not-json";
        }
      }
    });

    expect(result.status).toBe("FAILED_VALIDATION");
    expect(result.providerCalls).toBe(1);
    expect(result.steps.map((step) => step.name)).toEqual(["RUNNER", "POLICY_GATE", "PLAN", "ARTIFACT"]);
    expect(result.toolCalls).toEqual([]);
    expect(result.loopIterations).toBe(0);
  });

  it("records methodology, workspace scan, grounding refs, and safe tool loop observations", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-answer",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库总结 AI agent 架构",
            outputPolicy: "ANSWER_ONLY",
            steps: ["scan workspace", "read context", "answer"],
            contextHints: ["raw/agent", "knowledge-base"]
          };
        },
        async nextAction() {
          return {
            action: "SOLVED",
            summary: "Enough context gathered."
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.toolCalls.map((call) => call.name)).toContain("methodology.read");
    expect(result.toolCalls.map((call) => call.name)).toContain("workspace.scan");
    expect(result.groundingRefs.length).toBeGreaterThan(0);
    expect((result as unknown as { contextDigest?: Array<{ path: string; excerpt: string }> }).contextDigest?.[0]).toMatchObject({
      path: expect.stringContaining("raw/"),
      excerpt: expect.any(String)
    });
    expect((result as unknown as { contextDigest: Array<{ excerpt: string }> }).contextDigest[0].excerpt.length).toBeLessThanOrEqual(
      800
    );
    expect(result.loopIterations).toBe(1);
    expect(result.traceEvents[0]).toMatchObject({
      iteration: 1,
      action: "SOLVED"
    });
    expect(JSON.stringify(result.traceEvents)).not.toContain("thought");
    expect(result.answer).toContain("Sources:");
  });

  it("stops at maxIterations when provider keeps asking for more context", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-budget-loop",
      message: "根据知识库总结 AI agent 架构",
      loopBudget: {
        maxIterations: 2,
        maxToolCalls: 8,
        timeoutMs: 30000
      },
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库总结 AI agent 架构",
            outputPolicy: "ANSWER_ONLY",
            steps: ["scan", "expand"],
            contextHints: []
          };
        },
        async nextAction() {
          return {
            action: "READ_CONTEXT",
            toolName: "workspace.scan",
            summary: "Need more context."
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_BUDGET");
    expect(result.loopIterations).toBe(2);
    expect(result.answer).toBeUndefined();
    expect(result.synthesis).toBeUndefined();
    expect(result.steps.map((step) => step.name)).toEqual([
      "RUNNER",
      "POLICY_GATE",
      "PLAN",
      "CONTEXT_GATHER",
      "TOOL_LOOP",
      "ARTIFACT"
    ]);
  });

  it("stops at maxToolCalls before reading more context", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-tool-call-budget",
      message: "根据知识库总结 AI agent 架构",
      loopBudget: {
        maxIterations: 3,
        maxToolCalls: 1,
        timeoutMs: 30000
      },
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库总结 AI agent 架构",
            outputPolicy: "ANSWER_ONLY",
            steps: ["scan", "expand"],
            contextHints: []
          };
        },
        async nextAction() {
          return {
            action: "READ_CONTEXT",
            toolName: "workspace.scan",
            summary: "Need more context."
          };
        },
        async synthesize() {
          return {
            kind: "ANSWER",
            answer: "Should not synthesize\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_BUDGET");
    expect(result.loopIterations).toBe(2);
    expect(result.answer).toBeUndefined();
    expect(result.synthesis).toBeUndefined();
    expect(result.traceEvents.filter((event) => event.action === "READ_CONTEXT")).toHaveLength(1);
    expect(result.steps.map((step) => step.name)).toEqual([
      "RUNNER",
      "POLICY_GATE",
      "PLAN",
      "CONTEXT_GATHER",
      "TOOL_LOOP",
      "ARTIFACT"
    ]);
  });

  it("writes a FAILED_PROVIDER report when real provider env is missing", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-missing-provider-env",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      providerRuntime: {
        provider: "mimo-real",
        timeoutMs: 30000,
        baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
        model: "mimo-test-model",
        apiKeyEnvName: "MIMO_API_KEY"
      },
      providerRuntimeDependencies: {
        env: {}
      }
    });

    expect(result.status).toBe("FAILED_PROVIDER");
    expect(result.providerCalls).toBe(0);
    expect(result.steps.map((step) => step.name)).toEqual(["RUNNER", "ARTIFACT"]);
    expect(result.steps[0]).toMatchObject({
      name: "RUNNER",
      status: "FAILED",
      summary: "MISSING_MIMO_API_KEY"
    });
    const report = JSON.parse(await readFile(path.join(tempRoot, result.artifactPath), "utf8")) as typeof result;
    expect(report.status).toBe("FAILED_PROVIDER");
    expect(JSON.stringify(report)).not.toContain("MIMO_API_KEY=");
  });

  it("uses provider-backed synthesis for answer output when provider supports synthesize", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-provider-synthesis-answer",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库总结 AI agent 架构",
            outputPolicy: "ANSWER_ONLY",
            steps: ["scan workspace", "read context", "answer"],
            contextHints: ["raw/agent", "knowledge-base"]
          };
        },
        async nextAction() {
          return {
            action: "SOLVED",
            summary: "Enough context gathered."
          };
        },
        async synthesize() {
          return {
            kind: "ANSWER",
            answer: "Provider answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.steps.map((step) => step.name)).toEqual([
      "RUNNER",
      "POLICY_GATE",
      "PLAN",
      "CONTEXT_GATHER",
      "TOOL_LOOP",
      "SYNTHESIZE",
      "SELF_CHECK",
      "ARTIFACT"
    ]);
    expect(result.answer).toContain("Provider answer");
    expect(result.providerCalls).toBe(3);
    expect(result.synthesis).toMatchObject({
      providerBacked: true,
      outputKind: "ANSWER"
    });
  });

  it("materializes exact source refs when provider answer only uses numbered citations", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-provider-numbered-citations",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      openAgentProvider: {
        async plan() {
          return {
            objective: "根据知识库总结 AI agent 架构",
            outputPolicy: "ANSWER_ONLY",
            steps: ["scan workspace", "read context", "answer"],
            contextHints: ["raw/agent", "knowledge-base"]
          };
        },
        async nextAction() {
          return {
            action: "SOLVED",
            summary: "Enough context gathered."
          };
        },
        async synthesize() {
          return {
            kind: "ANSWER",
            answer: "Provider answer cites the agent loop failure analysis [1].",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.answer).toContain("Provider answer cites the agent loop failure analysis [1].");
    expect(result.answer).toContain("Sources:");
    expect(result.answer).toContain("- raw/agent/Agent Loop 失败复盘.md");
    expect(result.steps.map((step) => step.name)).toContain("SELF_CHECK");
  });

  it("materializes markdown source refs when provider draft only uses numbered citations", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-provider-draft-numbered-citations",
      message: "生成 agent loop 改进草稿",
      outputPolicy: "DRAFT_ARTIFACT",
      openAgentProvider: {
        async plan() {
          return {
            objective: "生成 agent loop 改进草稿",
            outputPolicy: "DRAFT_ARTIFACT",
            steps: ["scan workspace", "read context", "draft"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return {
            action: "SOLVED",
            summary: "Enough context gathered."
          };
        },
        async synthesize() {
          return {
            kind: "DRAFT_ARTIFACT",
            title: "Agent Loop Draft",
            content: "Draft only. Provider draft cites the agent loop failure analysis [1].",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.draftArtifact?.content).toContain("Draft only");
    expect(result.draftArtifact?.content).toContain("## Sources");
    expect(result.draftArtifact?.content).toContain("- raw/agent/Agent Loop 失败复盘.md");
  });

  it("materializes markdown source refs when provider candidate patch only uses numbered citations without writing workspace", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-provider-candidate-numbered-citations",
      message: "准备 agent loop 改进候选落库",
      outputPolicy: "CANDIDATE_PATCH",
      openAgentProvider: {
        async plan() {
          return {
            objective: "准备 agent loop 改进候选落库",
            outputPolicy: "CANDIDATE_PATCH",
            steps: ["scan workspace", "read context", "candidate"],
            contextHints: ["raw/agent"]
          };
        },
        async nextAction() {
          return {
            action: "SOLVED",
            summary: "Enough context gathered."
          };
        },
        async synthesize() {
          return {
            kind: "CANDIDATE_PATCH",
            title: "Agent Loop Candidate",
            content: "Provider candidate cites the agent loop failure analysis [1].",
            targetPath: "knowledge-base/drafts/provider-candidate.md",
            groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
          };
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.candidatePatch?.publishable).toBe(false);
    expect(result.candidatePatch?.files[0].content).toContain("## Sources");
    expect(result.candidatePatch?.files[0].content).toContain("- raw/agent/Agent Loop 失败复盘.md");
    expect(result.candidatePatch?.files[0].contentSha).toMatch(/^[a-f0-9]{64}$/);
    expect(await pathExists(path.join(tempRoot, "knowledge-base/drafts/graph-provider-candidate-numbered-citations.md"))).toBe(
      false
    );
  });

  it("returns NEEDS_CONFIRMATION when provider requests an unclear workspace write", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-needs-confirmation",
      message: "把这个整理一下落库",
      openAgentProvider: {
        async plan() {
          return {
            objective: "把这个整理一下落库",
            outputPolicy: "CANDIDATE_PATCH",
            steps: ["confirm write scope"],
            contextHints: []
          };
        },
        async nextAction() {
          return {
            action: "REQUEST_WRITE_CONFIRMATION",
            summary: "Target path is unclear."
          };
        }
      }
    });

    expect(result.status).toBe("NEEDS_CONFIRMATION");
    expect(result.confirmation?.required).toBe(true);
    expect(result.confirmation?.handoff.capabilityId).toBe("workflow.organizeWorkspace");
    expect(result.candidatePatch).toBeUndefined();
    expect(result.synthesis).toBeUndefined();
    expect(result.steps.map((step) => step.name)).toContain("HANDOFF");
    expect(result.steps.map((step) => step.name)).toContain("ARTIFACT");
  });

  it("produces a non-publishable candidate patch under knowledge-base only", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-candidate",
      message: "生成一份 AI 八股文问题清单并准备候选落库",
      outputPolicy: "CANDIDATE_PATCH"
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.candidatePatch).toMatchObject({
      kind: "CANDIDATE_PATCH_PROPOSAL",
      publishable: false,
      targetPaths: ["knowledge-base/drafts/graph-candidate.md"]
    });
    expect(result.candidatePatch?.files[0].path.startsWith("knowledge-base/")).toBe(true);
  });

  it("uses providerRuntime to drive plan and action through MiMo-compatible provider", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-provider-backed",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      providerRuntime: {
        provider: "mimo-real",
        timeoutMs: 30000,
        baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
        model: "mimo-test-model",
        apiKeyEnvName: "MIMO_API_KEY"
      },
      providerRuntimeDependencies: {
        env: {
          MIMO_API_KEY: "test-api-key"
        },
        fetch: async (url, init) => {
          requests.push({ url: String(url), init: init ?? {} });
          const content =
            requests.length === 1
              ? JSON.stringify({
                  objective: "根据知识库总结 AI agent 架构",
                  outputPolicy: "ANSWER_ONLY",
                  steps: ["scan workspace", "answer"],
                  contextHints: ["raw/agent", "knowledge-base"]
                })
              : requests.length === 2
                ? JSON.stringify({
                    action: "SOLVED",
                    summary: "Provider decided enough context is available."
                  })
                : JSON.stringify({
                    kind: "ANSWER",
                    answer: "Provider graph answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
                    groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
                  });
          return new Response(
            JSON.stringify({
              model: "mimo-test-model",
              choices: [{ finish_reason: "stop", message: { role: "assistant", content } }]
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.providerCalls).toBe(3);
    expect(requests).toHaveLength(3);
    expect(result.answer).toContain("Provider graph answer");
    expect(result.traceEvents[0].summary).toBe("Provider decided enough context is available.");
    expect(result.rawProviderRefs).toHaveLength(3);
    expect(result.rawProviderRefs.map((ref) => ref.providerCallId)).toEqual([
      "open-agent-plan-1",
      "open-agent-next-action-2",
      "open-agent-synthesize-3"
    ]);
    expect(result.rawProviderRefs[0].requestPath).toContain(".agent-runs/open-agent/raw-provider/graph-provider-backed");
    const requestArtifact = await readFile(path.join(tempRoot, result.rawProviderRefs[0].requestPath), "utf8");
    expect(requestArtifact).toContain("[REDACTED]");
    expect(requestArtifact).not.toContain("test-api-key");
    const synthesizeRequest = await readFile(path.join(tempRoot, result.rawProviderRefs[2].requestPath), "utf8");
    expect(synthesizeRequest).toContain("[REDACTED]");
    expect(synthesizeRequest).not.toContain("test-api-key");
  });
});
