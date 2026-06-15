import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import {
  createKnowledgeWorkflowAgent,
  runOpenAgentGraph,
  runOpenAgentRealSmoke,
  runOpenAgentTask,
  runOrganize,
  type RunOrganizeRequest,
  type RunOrganizeResult
} from "../../src/index.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "sdk-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("public SDK surface", () => {
  it("exports stable organize SDK functions and types", () => {
    expect(typeof createKnowledgeWorkflowAgent).toBe("function");
    expect(typeof runOpenAgentGraph).toBe("function");
    expect(typeof runOpenAgentRealSmoke).toBe("function");
    expect(typeof runOpenAgentTask).toBe("function");
    expect(typeof runOrganize).toBe("function");
    const request: RunOrganizeRequest = {
      workspaceRoot: "/tmp/workspace",
      instruction: "整理全部知识库",
      autoApprove: false,
      methodologyId: "lmwiki-v1"
    };
    expect(request.autoApprove).toBe(false);
    const result = null as RunOrganizeResult | null;
    expect(result).toBeNull();
  });

  it("runs organize workflow through the SDK facade", async () => {
    const result = await runOrganize({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-sdk",
      autoApprove: true,
      methodologyId: "lmwiki-v1",
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });

    expect(result).toMatchObject({
      runId: "run-sdk",
      status: "SUCCEEDED_WITH_WARNINGS",
      artifactRoot: ".agent-runs/run-sdk",
      methodologyId: "lmwiki-v1"
    });
    expect(result.reportPath).toBe(".agent-runs/run-sdk/report.md");
  });

  it("inspects run artifacts without executing workflow", async () => {
    const agent = createKnowledgeWorkflowAgent();
    await agent.runOrganize({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-sdk-inspect",
      autoApprove: true,
      methodologyId: "lmwiki-v1",
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });

    const inspected = await agent.inspectRun({ workspaceRoot: tempRoot, runId: "run-sdk-inspect" });

    expect(inspected.runId).toBe("run-sdk-inspect");
    expect(inspected.artifactRoot).toBe(".agent-runs/run-sdk-inspect");
    expect(inspected.methodology).toEqual({ id: "lmwiki-v1", version: "1" });
    expect(inspected.workItemStatuses["quality-review"]).toBe("SUCCEEDED");
    expect(inspected.eval?.agentLoop?.missingArtifacts).toEqual([]);
    expect(inspected.decisions.map((decision) => decision.action)).toContain("SKIP");
  });

  it("routes commands through the SDK agent facade", async () => {
    const agent = createKnowledgeWorkflowAgent({ defaultMethodologyId: "unknown" });

    await expect(
      agent.handleCommand({
        workspaceRoot: tempRoot,
        message: "根据知识库总结 AI 相关知识"
      })
    ).rejects.toThrow("Unknown knowledge methodology: unknown");
  });

  it("runs open agent tasks through the SDK agent facade", async () => {
    const agent = createKnowledgeWorkflowAgent();
    expect(typeof agent.runOpenAgentTask).toBe("function");

    const result = await agent.runOpenAgentTask({
      workspaceRoot: tempRoot,
      taskId: "task-sdk-open-agent",
      objective: "根据知识库总结 AI 相关知识",
      risk: "READ_ONLY",
      outputPolicy: "ANSWER_ONLY",
      allowedToolNames: ["workspace.scan"],
      blockedToolNames: ["patch.publish"]
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.artifactPath).toBe(".agent-runs/open-agent/task-sdk-open-agent.json");
  });

  it("runs open agent graph through the SDK agent facade", async () => {
    const agent = createKnowledgeWorkflowAgent();
    expect(typeof agent.runOpenAgentGraph).toBe("function");

    const result = await agent.runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "task-sdk-open-agent-graph",
      message: "根据知识库总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY"
    });

    expect(result.status).toBe("SUCCEEDED");
    expect(result.answer).toContain("Sources:");
    expect(result.artifactPath).toBe(".agent-runs/open-agent/task-sdk-open-agent-graph.json");
  });
});
