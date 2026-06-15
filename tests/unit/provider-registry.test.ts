import { describe, expect, it } from "vitest";
import { selectNoteProvider } from "../../src/runtime/provider/provider-registry.js";
import { ProviderRuntimeError } from "../../src/domain/llm-provider/provider-error.js";
import type { WorkItem } from "../../src/domain/planning/work-item.js";

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
  reason: "fixture",
  attempts: [],
  publishPolicy: "AUTO_PUBLISH"
};

describe("provider registry", () => {
  it("defaults to the fake note provider", async () => {
    const provider = selectNoteProvider();

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("fake");
    expect(result.model).toBe("fake-note-model");
  });

  it("selects the fake note provider from runtime config", async () => {
    const provider = selectNoteProvider({ provider: "fake", timeoutMs: 30000 });

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("fake");
  });

  it("selects fixture providers from runtime config", async () => {
    const deepSeek = selectNoteProvider({ provider: "deepseek-fixture", timeoutMs: 30000 });
    const claudeCode = selectNoteProvider({ provider: "claude-code-fixture", timeoutMs: 30000 });
    const mimoVllm = selectNoteProvider({ provider: "mimo-vllm-fixture", timeoutMs: 30000 });

    await expect(
      deepSeek.generateNote({
        runId: "run-provider",
        workItem,
        sourceContent: "# source\n"
      })
    ).resolves.toMatchObject({ provider: "deepseek" });
    await expect(
      claudeCode.generateNote({
        runId: "run-provider",
        workItem,
        sourceContent: "# source\n"
      })
    ).resolves.toMatchObject({ provider: "claude-code" });
    await expect(
      mimoVllm.generateNote({
        runId: "run-provider",
        workItem,
        sourceContent: "# source\n"
      })
    ).resolves.toMatchObject({ provider: "mimo-vllm" });
  });

  it("selects opt-in DeepSeek real provider without storing API key in config", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const provider = selectNoteProvider(
      {
        provider: "deepseek-real",
        timeoutMs: 30000,
        model: "deepseek-v4-pro",
        baseUrl: "https://api.deepseek.com",
        apiKeyEnvName: "TEST_DEEPSEEK_API_KEY"
      },
      {
        env: { TEST_DEEPSEEK_API_KEY: "test-api-key" },
        fetch: async (url, init) => {
          requests.push({ url: String(url), init: init ?? {} });
          return new Response(
            JSON.stringify({
              model: "deepseek-v4-pro",
              choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Note\n" } }]
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }
    );

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("deepseek");
    expect(requests[0].url).toBe("https://api.deepseek.com/chat/completions");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
  });

  it("selects opt-in MiMo real provider without storing API key in config", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const provider = selectNoteProvider(
      {
        provider: "mimo-real",
        timeoutMs: 30000,
        model: "mimo-test-model",
        baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
        apiKeyEnvName: "TEST_MIMO_API_KEY"
      },
      {
        env: { TEST_MIMO_API_KEY: "test-api-key" },
        fetch: async (url, init) => {
          requests.push({ url: String(url), init: init ?? {} });
          return new Response(
            JSON.stringify({
              model: "mimo-test-model",
              choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Note\n" } }]
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }
    );

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(result.provider).toBe("mimo-api");
    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
  });

  it("uses the smoke-verified MiMo API model by default", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const provider = selectNoteProvider(
      {
        provider: "mimo-real",
        timeoutMs: 30000,
        apiKeyEnvName: "TEST_MIMO_API_KEY"
      },
      {
        env: { TEST_MIMO_API_KEY: "test-api-key" },
        fetch: async (url, init) => {
          requests.push({ url: String(url), init: init ?? {} });
          return new Response(
            JSON.stringify({
              model: "mimo-v2.5",
              choices: [{ finish_reason: "stop", message: { role: "assistant", content: "# Note\n" } }]
            }),
            { status: 200, headers: { "Content-Type": "application/json" } }
          );
        }
      }
    );

    await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(JSON.parse(requests[0].init.body as string).model).toBe("mimo-v2.5");
  });

  it("rejects opt-in DeepSeek real provider when API key env is missing", () => {
    expect(() =>
      selectNoteProvider(
        {
          provider: "deepseek-real",
          timeoutMs: 30000,
          apiKeyEnvName: "TEST_DEEPSEEK_API_KEY"
        },
        {
          env: {}
        }
      )
    ).toThrow(ProviderRuntimeError);
  });

  it("rejects opt-in MiMo real provider when API key env is missing", () => {
    expect(() =>
      selectNoteProvider(
        {
          provider: "mimo-real",
          timeoutMs: 30000,
          apiKeyEnvName: "TEST_MIMO_API_KEY"
        },
        {
          env: {}
        }
      )
    ).toThrow(ProviderRuntimeError);
  });
});
