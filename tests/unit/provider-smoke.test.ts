import { execFile, spawn } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, describe, expect, it } from "vitest";
import { createDeepSeekRealNoteProvider } from "../../src/domain/llm-provider/deepseek-real-provider.js";
import { createMimoRealNoteProvider } from "../../src/domain/llm-provider/mimo-real-provider.js";
import type { WorkItem } from "../../src/domain/planning/work-item.js";
import { inspectRealProviderSmoke, runRealProviderSmoke } from "../../src/runtime/provider/real-smoke.js";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

const execFileAsync = promisify(execFile);
const tempRoots: string[] = [];

async function execFileWithInput(args: string[], input: string): Promise<{ stdout: string; stderr: string }> {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, args, {
      cwd: process.cwd(),
      env: {
        ...process.env,
        MIMO_API_KEY: "",
        MIMO_BASE_URL: "",
        MIMO_MODEL: ""
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

afterEach(async () => {
  await Promise.all(tempRoots.splice(0).map((tempRoot) => rm(tempRoot, { recursive: true, force: true })));
});

const workItem: WorkItem = {
  id: "rewrite-tools",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/tools/Skill vs CLI Tool 决策.md"],
  targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  baseShas: {
    "raw/tools/Skill vs CLI Tool 决策.md": "source-sha"
  },
  risk: "LOW",
  requiresApproval: false,
  reason: "provider smoke",
  attempts: [],
  publishPolicy: "AUTO_PUBLISH"
};

describe("real provider smoke harness", () => {
  it("skips DeepSeek smoke when required env is missing", () => {
    const result = inspectRealProviderSmoke({
      provider: "deepseek-real-smoke",
      env: {}
    });

    expect(result).toEqual({
      provider: "deepseek-real-smoke",
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv: ["DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"]
    });
  });

  it("reports Claude Code smoke as blocked until SDK wiring exists", () => {
    const result = inspectRealProviderSmoke({
      provider: "claude-code-real-smoke",
      env: {}
    });

    expect(result).toEqual({
      provider: "claude-code-real-smoke",
      status: "BLOCKED",
      realExternalCall: false,
      reason: "CLAUDE_CODE_SDK_NOT_WIRED",
      requiredEnv: []
    });
  });

  it("skips MiMo smoke when required env is missing", () => {
    const result = inspectRealProviderSmoke({
      provider: "mimo-real-smoke",
      env: {}
    });

    expect(result).toEqual({
      provider: "mimo-real-smoke",
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv: ["MIMO_API_KEY", "MIMO_BASE_URL", "MIMO_MODEL"]
    });
  });

  it("CLI skip path does not perform a real external call", async () => {
    const result = await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/provider-smoke.ts",
      "--provider",
      "deepseek-real-smoke"
    ]);

    const payload = JSON.parse(result.stdout) as { status: string; realExternalCall: boolean };
    expect(payload.status).toBe("SKIPPED");
    expect(payload.realExternalCall).toBe(false);
  });

  it("CLI MiMo skip path does not perform a real external call", async () => {
    const result = await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/provider-smoke.ts",
      "--provider",
      "mimo-real-smoke"
    ]);

    const payload = JSON.parse(result.stdout) as { status: string; realExternalCall: boolean };
    expect(payload.status).toBe("SKIPPED");
    expect(payload.realExternalCall).toBe(false);
  });

  it("CLI MiMo smoke can read API key from stdin without printing it", async () => {
    const result = await execFileWithInput(
      [
        "--import",
        "tsx",
        "src/cli/provider-smoke.ts",
        "--provider",
        "mimo-real-smoke",
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

  it("maps DeepSeek real adapter requests and responses with injected fetch", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const provider = createDeepSeekRealNoteProvider({
      apiKey: "test-api-key",
      baseUrl: "https://api.deepseek.com",
      model: "deepseek-v4-pro",
      fetch: async (url, init) => {
        requests.push({ url: String(url), init: init ?? {} });
        return new Response(
          JSON.stringify({
            model: "deepseek-v4-pro",
            choices: [
              {
                finish_reason: "stop",
                message: {
                  role: "assistant",
                  content: "# Note\n\n## 摘要\n\nok\n\n## 来源追踪\n\n- raw/tools/Skill vs CLI Tool 决策.md\n\n## 关键决策\n\n- ok\n"
                }
              }
            ],
            usage: {
              prompt_tokens: 10,
              completion_tokens: 5,
              total_tokens: 15
            }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
    });

    const result = await provider.generateNote({
      runId: "run-real-adapter",
      workItem,
      sourceContent: "# source\n"
    });

    expect(requests[0].url).toBe("https://api.deepseek.com/chat/completions");
    expect(requests[0].init.method).toBe("POST");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
    expect(JSON.parse(requests[0].init.body as string).model).toBe("deepseek-v4-pro");
    expect(result.provider).toBe("deepseek");
    expect(result.model).toBe("deepseek-v4-pro");
    expect(result.finishReason).toBe("stop");
    expect(result.usage).toEqual({ inputTokens: 10, outputTokens: 5, totalTokens: 15, reasoningTokens: undefined });
    expect(result.content).toContain("## 摘要");
  });

  it("runs DeepSeek real smoke through adapter only when explicitly enabled", async () => {
    const result = await runRealProviderSmoke({
      provider: "deepseek-real-smoke",
      executeReal: true,
      env: {
        DEEPSEEK_API_KEY: "test-api-key",
        DEEPSEEK_BASE_URL: "https://api.deepseek.com",
        DEEPSEEK_MODEL: "deepseek-v4-pro"
      },
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "deepseek-v4-pro",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Smoke\n" } }]
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    expect(result.status).toBe("PASSED");
    expect(result.realExternalCall).toBe(true);
  });

  it("maps MiMo real adapter requests and responses with injected fetch", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const provider = createMimoRealNoteProvider({
      apiKey: "test-api-key",
      baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
      model: "mimo-test-model",
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
                  content: "# Note\n\n## 摘要\n\nok\n\n## 来源追踪\n\n- raw/tools/Skill vs CLI Tool 决策.md\n\n## 关键决策\n\n- ok\n"
                }
              }
            ],
            usage: {
              prompt_tokens: 12,
              completion_tokens: 6,
              total_tokens: 18
            }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      }
    });

    const result = await provider.generateNote({
      runId: "run-mimo-real-adapter",
      workItem,
      sourceContent: "# source\n"
    });

    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
    expect(requests[0].init.method).toBe("POST");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
    expect(JSON.parse(requests[0].init.body as string).model).toBe("mimo-test-model");
    expect(result.provider).toBe("mimo-api");
    expect(result.model).toBe("mimo-test-model");
    expect(result.finishReason).toBe("stop");
    expect(result.usage).toEqual({ inputTokens: 12, outputTokens: 6, totalTokens: 18, reasoningTokens: undefined });
    expect(result.content).toContain("## 摘要");
  });

  it("runs MiMo real smoke through adapter only when explicitly enabled", async () => {
    const result = await runRealProviderSmoke({
      provider: "mimo-real-smoke",
      executeReal: true,
      env: {
        MIMO_API_KEY: "test-api-key",
        MIMO_BASE_URL: "https://token-plan-cn.xiaomimimo.com/v1",
        MIMO_MODEL: "mimo-test-model"
      },
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Smoke\n" } }]
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    expect(result.status).toBe("PASSED");
    expect(result.realExternalCall).toBe(true);
  });

  it("returns FAILED when explicit DeepSeek real smoke request fails", async () => {
    const result = await runRealProviderSmoke({
      provider: "deepseek-real-smoke",
      executeReal: true,
      env: {
        DEEPSEEK_API_KEY: "test-api-key",
        DEEPSEEK_BASE_URL: "https://api.deepseek.com",
        DEEPSEEK_MODEL: "deepseek-v4-pro"
      },
      fetch: async () => new Response("provider unavailable", { status: 503 })
    });

    expect(result.status).toBe("FAILED");
    expect(result.realExternalCall).toBe(true);
    expect(result.reason).toBe("REAL_EXTERNAL_CALL_FAILED");
    expect(result.httpStatus).toBe(503);
    expect(result.errorMessage).toContain("provider unavailable");
    expect(result.errorMessage).not.toContain("test-api-key");
  });

  it("captures only redacted DeepSeek raw envelopes", async () => {
    const envelopes: unknown[] = [];
    const provider = createDeepSeekRealNoteProvider({
      apiKey: "test-api-key",
      baseUrl: "https://api.deepseek.com",
      model: "deepseek-v4-pro",
      onRawEnvelope: async (envelope) => {
        envelopes.push(envelope);
      },
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "deepseek-v4-pro",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Smoke\n" } }],
            usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    await provider.generateNote({
      runId: "run-real-adapter",
      workItem,
      sourceContent: "# source\n"
    });

    expect(JSON.stringify(envelopes)).not.toContain("test-api-key");
    expect(envelopes[0]).toMatchObject({
      request: {
        headers: {
          Authorization: "[REDACTED]"
        },
        body: {
          model: "deepseek-v4-pro"
        }
      },
      response: {
        body: {
          usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
        }
      }
    });
  });

  it("captures only redacted MiMo raw envelopes", async () => {
    const envelopes: unknown[] = [];
    const provider = createMimoRealNoteProvider({
      apiKey: "test-api-key",
      baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
      model: "mimo-test-model",
      onRawEnvelope: async (envelope) => {
        envelopes.push(envelope);
      },
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Smoke\n" } }],
            usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    await provider.generateNote({
      runId: "run-mimo-real-adapter",
      workItem,
      sourceContent: "# source\n"
    });

    expect(JSON.stringify(envelopes)).not.toContain("test-api-key");
    expect(envelopes[0]).toMatchObject({
      request: {
        headers: {
          Authorization: "[REDACTED]"
        },
        body: {
          model: "mimo-test-model"
        }
      },
      response: {
        body: {
          usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
        }
      }
    });
  });

  it("writes redacted raw envelope artifacts and raw_ref trace when store is provided", async () => {
    const tempRoot = await mkdtemp(path.join(os.tmpdir(), "provider-smoke-"));
    tempRoots.push(tempRoot);
    const store = new AgentRunsStore(tempRoot, "run-provider-smoke");

    const result = await runRealProviderSmoke({
      provider: "deepseek-real-smoke",
      executeReal: true,
      env: {
        DEEPSEEK_API_KEY: "test-api-key",
        DEEPSEEK_BASE_URL: "https://api.deepseek.com",
        DEEPSEEK_MODEL: "deepseek-v4-pro"
      },
      store,
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "deepseek-v4-pro",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Smoke\n" } }],
            usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    expect(result.status).toBe("PASSED");
    const request = await readFile(store.artifactPath("raw-provider/deepseek-real-smoke/request.json"), "utf8");
    const response = await readFile(store.artifactPath("raw-provider/deepseek-real-smoke/response.json"), "utf8");
    const trace = await readFile(store.artifactPath("traces/deepseek-real-smoke.jsonl"), "utf8");
    expect(request).not.toContain("test-api-key");
    expect(request).toContain("[REDACTED]");
    expect(response).toContain("prompt_tokens");
    expect(trace).toContain("llm.provider.raw_ref");
  });

  it("writes redacted MiMo raw envelope artifacts and raw_ref trace when store is provided", async () => {
    const tempRoot = await mkdtemp(path.join(os.tmpdir(), "provider-smoke-"));
    tempRoots.push(tempRoot);
    const store = new AgentRunsStore(tempRoot, "run-provider-smoke");

    const result = await runRealProviderSmoke({
      provider: "mimo-real-smoke",
      executeReal: true,
      env: {
        MIMO_API_KEY: "test-api-key",
        MIMO_BASE_URL: "https://token-plan-cn.xiaomimimo.com/v1",
        MIMO_MODEL: "mimo-test-model"
      },
      store,
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Smoke\n" } }],
            usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    expect(result.status).toBe("PASSED");
    const request = await readFile(store.artifactPath("raw-provider/mimo-real-smoke/request.json"), "utf8");
    const response = await readFile(store.artifactPath("raw-provider/mimo-real-smoke/response.json"), "utf8");
    const trace = await readFile(store.artifactPath("traces/mimo-real-smoke.jsonl"), "utf8");
    expect(request).not.toContain("test-api-key");
    expect(request).toContain("[REDACTED]");
    expect(response).toContain("prompt_tokens");
    expect(trace).toContain("llm.provider.raw_ref");
  });
});
