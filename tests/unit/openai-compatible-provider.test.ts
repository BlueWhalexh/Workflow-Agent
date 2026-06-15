import { describe, expect, it } from "vitest";
import { createOpenAiCompatibleNoteProvider } from "../../src/domain/llm-provider/openai-compatible-note-provider.js";
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
  reason: "shared adapter",
  attempts: [],
  publishPolicy: "AUTO_PUBLISH"
};

describe("OpenAI-compatible note provider", () => {
  it("maps chat completions request and response without leaking raw API key", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const envelopes: unknown[] = [];
    const provider = createOpenAiCompatibleNoteProvider({
      apiKey: "test-api-key",
      baseUrl: "https://example.test/v1/",
      model: "provider-test-model",
      traceProvider: "openai-compatible",
      providerCallSuffix: "provider-real",
      providerDisplayName: "Provider",
      fetch: async (url, init) => {
        requests.push({ url: String(url), init: init ?? {} });
        return new Response(
          JSON.stringify({
            model: "provider-test-model",
            choices: [
              {
                finish_reason: "stop",
                message: {
                  role: "assistant",
                  content: "# Note\n"
                }
              }
            ],
            usage: {
              prompt_tokens: 3,
              completion_tokens: 2,
              total_tokens: 5,
              completion_tokens_details: {
                reasoning_tokens: 1
              }
            }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      },
      onRawEnvelope: async (envelope) => {
        envelopes.push(envelope);
      }
    });

    const result = await provider.generateNote({
      runId: "run-provider",
      workItem,
      sourceContent: "# source\n"
    });

    expect(requests[0].url).toBe("https://example.test/v1/chat/completions");
    expect(requests[0].init.method).toBe("POST");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
    expect(result).toMatchObject({
      providerCallId: "rewrite-tools:provider-real",
      provider: "openai-compatible",
      model: "provider-test-model",
      finishReason: "stop",
      usage: {
        inputTokens: 3,
        outputTokens: 2,
        totalTokens: 5,
        reasoningTokens: 1
      },
      content: "# Note\n"
    });
    expect(JSON.stringify(envelopes)).not.toContain("test-api-key");
    expect(envelopes[0]).toMatchObject({
      request: {
        headers: {
          Authorization: "[REDACTED]"
        }
      }
    });
  });
});
