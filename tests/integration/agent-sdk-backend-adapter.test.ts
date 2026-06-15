import { access, cp, mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { runBackendAgent, type BackendAgentResponse } from "../../src/index.js";

async function pathExists(absolutePath: string): Promise<boolean> {
  return access(absolutePath).then(
    () => true,
    () => false
  );
}

async function readTreeText(root: string): Promise<string> {
  const entries = await readdir(root, { withFileTypes: true }).catch(() => []);
  const chunks: string[] = [];
  for (const entry of entries) {
    const absolutePath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      chunks.push(await readTreeText(absolutePath));
    } else if (entry.isFile()) {
      chunks.push(await readFile(absolutePath, "utf8"));
    }
  }
  return chunks.join("\n");
}

describe("agent SDK backend adapter smoke", () => {
  it("maps SDK run envelopes into backend-facing responses without runtime result parsing", async () => {
    const workspaceRoot = await mkdtemp(path.join(os.tmpdir(), "agent-sdk-backend-adapter-"));
    try {
      await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), workspaceRoot, {
        recursive: true
      });

      const answer = await runBackendAgent({
        workspaceRoot,
        runId: "adapter-answer",
        userMessage: "总结当前知识库",
        mode: "deterministic-open-agent"
      });
      const providerResponses = [
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
          answer: "Provider-backed answer.\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
          groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
        }
      ];
      const providerAnswer = await runBackendAgent({
        workspaceRoot,
        runId: "adapter-provider-answer",
        userMessage: "总结当前 agent loop 的状态",
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
            const next = providerResponses.shift();
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
      const candidate = await runBackendAgent({
        workspaceRoot,
        runId: "adapter-candidate",
        userMessage: "准备 agent loop 改进候选落库",
        mode: "llm-open-agent",
        providerRuntime: { provider: "fake", timeoutMs: 30000 }
      });
      const confirmation = await runBackendAgent({
        workspaceRoot,
        runId: "adapter-confirm",
        userMessage: "把这个整理一下落库"
      });
      const preview = await runBackendAgent({
        workspaceRoot,
        runId: "adapter-preview",
        userMessage: "整理全部知识库",
        mode: "fixed-workflow",
        execute: false
      });
      const fixed = await runBackendAgent({
        workspaceRoot,
        runId: "adapter-fixed",
        userMessage: "整理全部知识库",
        mode: "fixed-workflow",
        execute: true,
        autoApprove: true,
        providerRuntime: { provider: "fake", timeoutMs: 30000 }
      });

      const responses: BackendAgentResponse[] = [answer, providerAnswer, candidate, confirmation, preview, fixed];
      for (const response of responses) {
        expect(response.schemaVersion).toBe("agent-backend-response.v1");
        expect(response.source.schemaVersion).toBe("agent-sdk-run.v1");
      }

      expect(answer).toMatchObject({
        runId: "adapter-answer",
        status: "SUCCEEDED",
        outputKind: "answer",
        requiresConfirmation: false,
        requiresApproval: false,
        wroteWorkspace: false,
        targetWorkspacePaths: []
      });
      expect(answer.displayText).toContain("Sources:");

      expect(providerAnswer.outputKind).toBe("answer");
      expect(providerAnswer.displayText).toContain("Provider-backed answer");
      expect(providerAnswer.artifactRefs.some((ref) => ref.endsWith("/request.json"))).toBe(true);
      expect(providerAnswer.artifactRefs.some((ref) => ref.endsWith("/response.json"))).toBe(true);

      expect(candidate.outputKind).toBe("candidate-patch");
      expect(candidate.requiresApproval).toBe(true);
      expect(candidate.requiresConfirmation).toBe(false);
      expect(candidate.displayText).toContain("fixed workflow confirmation");
      expect(candidate.wroteWorkspace).toBe(false);
      expect(candidate.targetWorkspacePaths).toEqual(["knowledge-base/drafts/adapter-candidate.md"]);
      expect(await pathExists(path.join(workspaceRoot, "knowledge-base/drafts/adapter-candidate.md"))).toBe(false);

      expect(confirmation.outputKind).toBe("confirmation");
      expect(confirmation.requiresConfirmation).toBe(true);
      expect(confirmation.requiresApproval).toBe(false);
      expect(confirmation.displayText).toContain("请确认要写入的对象");

      expect(preview.outputKind).toBe("route-preview");
      expect(preview.requiresApproval).toBe(true);
      expect(preview.wroteWorkspace).toBe(false);
      expect(preview.displayText).toBeNull();

      expect(fixed.outputKind).toBe("workflow-report");
      expect(fixed.requiresApproval).toBe(false);
      expect(fixed.wroteWorkspace).toBe(true);
      expect(fixed.displayText).toBeNull();
      expect(fixed.artifactRefs).toContain(".agent-runs/adapter-fixed/report.md");

      const artifactText = await readTreeText(path.join(workspaceRoot, ".agent-runs"));
      expect(artifactText).toContain("[REDACTED]");
      expect(artifactText).not.toContain("test-api-key");
    } finally {
      await rm(workspaceRoot, { recursive: true, force: true });
    }
  });

  it("keeps the adapter source independent from runtime internals", async () => {
    const source = await readFile(path.join(process.cwd(), "src/sdk/backend-adapter.ts"), "utf8");

    expect(source).not.toContain("../runtime/");
    expect(source).not.toContain("openAgentGraph");
    expect(source).not.toContain("openAgent");
    expect(source).not.toContain("workflow.");
  });
});
