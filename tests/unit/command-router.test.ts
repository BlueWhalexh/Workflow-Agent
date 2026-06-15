import { access, cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createKnowledgeWorkflowAgent, handleCommand } from "../../src/index.js";

let tempRoot: string;

async function fileExists(filePath: string): Promise<boolean> {
  return access(filePath)
    .then(() => true)
    .catch(() => false);
}

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "command-router-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("hybrid command router", () => {
  it("exports handleCommand through the public SDK", async () => {
    expect(typeof handleCommand).toBe("function");
    expect(typeof createKnowledgeWorkflowAgent().handleCommand).toBe("function");
  });

  it("routes open read requests to a read-only agent task envelope", async () => {
    const result = await handleCommand({
      workspaceRoot: tempRoot,
      message: "根据知识库总结 AI 相关知识"
    });

    expect(result.route).toMatchObject({
      lane: "OPEN_AGENT_TASK",
      capabilityId: "agent.openTask",
      risk: "READ_ONLY"
    });
    expect(result.agentTask?.outputPolicy).toBe("ANSWER_ONLY");
    expect(result.agentTask?.allowedToolNames).toContain("workspace.scan");
    expect(result.agentTask?.allowedToolNames).toContain("artifact.readEval");
    expect(result.agentTask?.allowedToolNames).not.toContain("patch.publish");
    expect(result.agentTask?.blockedToolNames).toContain("patch.publish");
    expect(result.openAgent?.status).toBe("SUCCEEDED");
    expect(result.openAgent?.answer).toContain("根据知识库总结 AI 相关知识");
    expect(result.openAgent?.artifactPath).toMatch(/\.agent-runs\/open-agent\/.+\.json/);
  });

  it("routes open draft requests to a draft-only agent task envelope", async () => {
    const result = await handleCommand({
      workspaceRoot: tempRoot,
      message: "生成一份 AI 八股文问题清单"
    });

    expect(result.route).toMatchObject({
      lane: "OPEN_AGENT_TASK",
      capabilityId: "agent.draftArtifact",
      risk: "DRAFT_ONLY"
    });
    expect(result.agentTask?.outputPolicy).toBe("DRAFT_ARTIFACT");
    expect(result.agentTask?.allowedToolNames).not.toContain("patch.publish");
    expect(result.openAgent?.status).toBe("SUCCEEDED");
    expect(result.openAgent?.draftArtifact?.content).toContain("Draft only");
  });

  it("requires confirmation for ambiguous workspace write requests", async () => {
    const result = await handleCommand({
      workspaceRoot: tempRoot,
      message: "把这个落库"
    });

    expect(result.route).toMatchObject({
      lane: "CONFIRMATION_REQUIRED",
      capabilityId: "confirmation.workspaceWrite",
      risk: "WORKSPACE_WRITE"
    });
    expect(result.confirmation?.required).toBe(true);
    expect(result.confirmation?.questions).toContain("请确认要写入的对象、范围和目标 methodology。");
    expect(result.confirmation?.handoff).toMatchObject({
      type: "FIXED_WORKFLOW",
      capabilityId: "workflow.organizeWorkspace",
      executeRequired: true,
      confirmationRequired: true,
      methodologyId: "lmwiki-v1",
      instruction: "把这个落库"
    });
    expect(result.openAgent).toBeUndefined();
    expect(await fileExists(path.join(tempRoot, ".agent-runs/open-agent"))).toBe(false);
  });

  it("routes high-confidence organize commands without executing by default", async () => {
    const result = await handleCommand({
      workspaceRoot: tempRoot,
      message: "整理全部知识库",
      runId: "run-router-plan"
    });

    expect(result.route).toMatchObject({
      lane: "FIXED_WORKFLOW",
      capabilityId: "workflow.organizeWorkspace",
      risk: "WORKSPACE_WRITE"
    });
    expect(result.workflow).toBeUndefined();
  });

  it("executes fixed workflow only when requested", async () => {
    const result = await handleCommand({
      workspaceRoot: tempRoot,
      message: "整理全部知识库",
      runId: "run-router-execute",
      execute: true,
      autoApprove: true,
      providerRuntime: { provider: "fake", timeoutMs: 30000 }
    });

    expect(result.route.lane).toBe("FIXED_WORKFLOW");
    expect(result.workflow).toMatchObject({
      runId: "run-router-execute",
      status: "SUCCEEDED_WITH_WARNINGS",
      methodologyId: "lmwiki-v1"
    });
  });

  it("rejects unknown methodology ids before returning a route", async () => {
    await expect(
      handleCommand({
        workspaceRoot: tempRoot,
        message: "根据知识库总结 AI 相关知识",
        methodologyId: "unknown"
      })
    ).rejects.toThrow("Unknown knowledge methodology: unknown");
  });
});
