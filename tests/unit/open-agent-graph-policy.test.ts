import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOpenAgentGraph } from "../../src/index.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "open-agent-graph-policy-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("open agent graph policy gate", () => {
  it("returns FAILED_POLICY for unknown methodology without calling provider", async () => {
    let providerCalls = 0;

    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-policy-unknown-methodology",
      message: "根据知识库总结 AI agent 架构",
      methodologyId: "unknown-methodology",
      openAgentProvider: {
        async plan() {
          providerCalls += 1;
          return {
            objective: "should not be called",
            outputPolicy: "ANSWER_ONLY",
            steps: [],
            contextHints: []
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_POLICY");
    expect(result.providerCalls).toBe(0);
    expect(providerCalls).toBe(0);
    expect(result.steps.find((step) => step.name === "POLICY_GATE")?.summary).toContain("Unknown knowledge methodology");
    expect(result.steps.at(-1)?.name).toBe("ARTIFACT");
  });

  it("returns FAILED_POLICY when patch.publish is allowed", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-policy-publish",
      message: "生成并发布一份问题清单",
      allowedToolNames: ["workspace.scan", "patch.publish"],
      blockedToolNames: []
    });

    expect(result.status).toBe("FAILED_POLICY");
    expect(result.providerCalls).toBe(0);
    expect(result.steps.find((step) => step.name === "POLICY_GATE")?.summary).toContain("patch.publish");
    expect(result.steps.at(-1)?.name).toBe("ARTIFACT");
  });

  it("returns FAILED_BUDGET when maxIterations is zero", async () => {
    const result = await runOpenAgentGraph({
      workspaceRoot: tempRoot,
      taskId: "graph-policy-budget",
      message: "根据知识库总结 AI agent 架构",
      loopBudget: {
        maxIterations: 0,
        maxToolCalls: 8,
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("FAILED_BUDGET");
    expect(result.providerCalls).toBe(0);
    expect(result.steps.find((step) => step.name === "POLICY_GATE")?.status).toBe("FAILED");
    expect(result.steps.at(-1)?.name).toBe("ARTIFACT");
  });
});
