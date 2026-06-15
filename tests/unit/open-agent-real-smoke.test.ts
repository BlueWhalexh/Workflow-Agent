import { spawn } from "node:child_process";
import { access, cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { inspectOpenAgentRealSmoke, runOpenAgentRealSmoke } from "../../src/index.js";

let tempRoot: string;

async function fileExists(filePath: string): Promise<boolean> {
  return access(filePath)
    .then(() => true)
    .catch(() => false);
}

async function execFileWithInput(args: string[], input: string): Promise<{ stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, args, {
      cwd: process.cwd(),
      env: {
        ...process.env,
        MIMO_API_KEY: "",
        MIMO_BASE_URL: "",
        MIMO_MODEL: "",
        MY_WORKFLOW_AGENT_DISABLE_KEYCHAIN: "1"
      }
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk: Buffer) => {
      stdout += chunk.toString("utf8");
    });
    child.stderr.on("data", (chunk: Buffer) => {
      stderr += chunk.toString("utf8");
    });
    child.on("error", reject);
    child.on("close", (code) => {
      if (code === 0) {
        resolve({ stdout, stderr });
      } else {
        reject(new Error(`Command failed with code ${code}: ${stderr}`));
      }
    });
    child.stdin.end(input);
  });
}

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "open-agent-real-smoke-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("open agent real smoke", () => {
  it("skips MiMo open-agent smoke when required env is missing", () => {
    const result = inspectOpenAgentRealSmoke({
      provider: "mimo-open-agent-smoke",
      env: {}
    });

    expect(result).toEqual({
      provider: "mimo-open-agent-smoke",
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv: ["MIMO_API_KEY", "MIMO_BASE_URL", "MIMO_MODEL"]
    });
  });

  it("CLI reads MiMo API key from stdin without printing it and skips without execute guard", async () => {
    const result = await execFileWithInput(
      [
        "--import",
        "tsx",
        "src/cli/open-agent-smoke.ts",
        "--provider",
        "mimo-open-agent-smoke",
        "--workspace-root",
        tempRoot,
        "--api-key-stdin",
        "--base-url",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "--model",
        "mimo-test-model"
      ],
      "test-api-key\n"
    );

    const payload = JSON.parse(result.stdout) as { status: string; realExternalCall: boolean; reason: string };
    expect(payload.status).toBe("SKIPPED");
    expect(payload.realExternalCall).toBe(false);
    expect(payload.reason).toBe("EXECUTE_REAL_NOT_SET");
    expect(result.stdout).not.toContain("test-api-key");
    expect(result.stderr).not.toContain("test-api-key");
  });

  it("runs MiMo open-agent smoke with injected fetch only when explicitly enabled", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const result = await runOpenAgentRealSmoke({
      provider: "mimo-open-agent-smoke",
      workspaceRoot: tempRoot,
      executeReal: true,
      env: {
        MIMO_API_KEY: "test-api-key",
        MIMO_BASE_URL: "https://token-plan-cn.xiaomimimo.com/v1",
        MIMO_MODEL: "mimo-test-model"
      },
      fetch: async (url, init) => {
        requests.push({ url: String(url), init: init ?? {} });
        return new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [
              {
                finish_reason: "stop",
                message: {
                  role: "assistant",
                  content: "# Smoke\n\n## 摘要\n\nreal smoke ok\n\n## 来源追踪\n\n- raw/smoke.md\n"
                }
              }
            ],
            usage: { prompt_tokens: 2, completion_tokens: 2, total_tokens: 4 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
    });

    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
    expect(result.status).toBe("PASSED");
    expect(result.realExternalCall).toBe(true);
    expect(result.openAgent?.status).toBe("SUCCEEDED");
    expect(result.openAgent?.candidatePatch?.publishable).toBe(false);
    expect(result.artifactPath).toBe(".agent-runs/open-agent/mimo-open-agent-smoke.json");
    expect(await fileExists(path.join(tempRoot, result.artifactPath!))).toBe(true);
    expect(JSON.stringify(result)).not.toContain("test-api-key");
  });

  it("runs MiMo llm-graph smoke with injected fetch only when explicitly enabled", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const result = await runOpenAgentRealSmoke({
      provider: "mimo-open-agent-smoke",
      workspaceRoot: tempRoot,
      mode: "llm-graph",
      outputPolicy: "ANSWER_ONLY",
      executeReal: true,
      env: {
        MIMO_API_KEY: "test-api-key",
        MIMO_BASE_URL: "https://token-plan-cn.xiaomimimo.com/v1",
        MIMO_MODEL: "mimo-test-model"
      },
      fetch: async (url, init) => {
        requests.push({ url: String(url), init: init ?? {} });
        const content =
          requests.length === 1
            ? JSON.stringify({
                objective: "MiMo real smoke produced provider output for open graph eval.",
                outputPolicy: "ANSWER_ONLY",
                steps: ["scan workspace", "answer"],
                contextHints: ["raw", "knowledge-base"]
              })
            : requests.length === 2
              ? JSON.stringify({
                  action: "SOLVED",
                  summary: "Provider-backed graph smoke solved."
                })
              : JSON.stringify({
                  kind: "ANSWER",
                  answer: "Provider smoke answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
                  groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
                });
        return new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [
              {
                finish_reason: "stop",
                message: {
                  role: "assistant",
                  content
                }
              }
            ],
            usage: { prompt_tokens: 2, completion_tokens: 2, total_tokens: 4 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
    });

    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
    expect(requests).toHaveLength(3);
    expect(result.status).toBe("PASSED");
    expect(result.realExternalCall).toBe(true);
    expect(result.openAgentGraph?.status).toBe("SUCCEEDED");
    expect(result.openAgentGraph?.providerCalls).toBe(3);
    expect(result.openAgentGraph?.answer).toContain("Provider smoke answer");
    expect(result.openAgentGraph?.synthesis).toMatchObject({
      providerBacked: true,
      providerCallId: "open-agent-synthesize-3",
      outputKind: "ANSWER"
    });
    expect(result.openAgentGraph?.rawProviderRefs).toHaveLength(3);
    expect(result.openAgentGraph?.rawProviderRefs.map((ref) => ref.providerCallId)).toEqual([
      "open-agent-plan-1",
      "open-agent-next-action-2",
      "open-agent-synthesize-3"
    ]);
    expect(result.artifactPath).toBe(".agent-runs/open-agent/mimo-open-agent-smoke.json");
    expect(result.rawProviderRequestPath).toBe(
      ".agent-runs/open-agent/raw-provider/mimo-open-agent-smoke/open-agent-plan-1/request.json"
    );
    expect(result.rawProviderResponsePath).toBe(
      ".agent-runs/open-agent/raw-provider/mimo-open-agent-smoke/open-agent-plan-1/response.json"
    );
    const requestArtifact = await readFile(path.join(tempRoot, result.rawProviderRequestPath!), "utf8");
    const responseArtifact = await readFile(path.join(tempRoot, result.rawProviderResponsePath!), "utf8");
    expect(requestArtifact).toContain("[REDACTED]");
    expect(requestArtifact).not.toContain("test-api-key");
    expect(responseArtifact).not.toContain("test-api-key");
    expect(JSON.stringify(result)).not.toContain("test-api-key");
  });
});
