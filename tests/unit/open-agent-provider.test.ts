import { describe, expect, it } from "vitest";
import * as openAgentProviderModule from "../../src/runtime/open-agent/open-agent-provider.js";
import {
  createOpenAiCompatibleOpenAgentProvider,
  OpenAgentProviderValidationError
} from "../../src/runtime/open-agent/open-agent-provider.js";

const parseOpenAgentSynthesisOutput = () =>
  (openAgentProviderModule as unknown as { parseOpenAgentSynthesisOutput: (value: unknown) => unknown })
    .parseOpenAgentSynthesisOutput;

describe("OpenAI-compatible open agent provider", () => {
  it("parses strict JSON answer synthesis output", () => {
    expect(
      parseOpenAgentSynthesisOutput()({
        kind: "ANSWER",
        answer: "Answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
        groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
      })
    ).toEqual({
      kind: "ANSWER",
      answer: "Answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
      groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
    });
  });

  it("parses fenced JSON draft synthesis output", () => {
    expect(
      parseOpenAgentSynthesisOutput()(
        "```json\n{\"kind\":\"DRAFT_ARTIFACT\",\"title\":\"AI Agent Questions\",\"content\":\"Draft only. Provider-generated draft content.\\n\\nSources:\\n- raw/agent/Agent Loop 失败复盘.md\",\"groundingRefs\":[\"raw/agent/Agent Loop 失败复盘.md\"]}\n```"
      )
    ).toEqual({
      kind: "DRAFT_ARTIFACT",
      title: "AI Agent Questions",
      content: "Draft only. Provider-generated draft content.\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
      groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
    });
  });

  it("maps synthesis request to chat completions and parses answer output", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const envelopes: unknown[] = [];
    const provider = createOpenAiCompatibleOpenAgentProvider({
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
                  content: JSON.stringify({
                    kind: "ANSWER",
                    answer: "Provider answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
                    groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
                  })
                }
              }
            ],
            usage: { prompt_tokens: 5, completion_tokens: 6, total_tokens: 11 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      },
      onRawEnvelope: (envelope) => {
        envelopes.push(envelope);
      }
    });

    const synthesis = await provider.synthesize!({
      objective: "总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      methodologyId: "agent-loop",
      groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"],
      contextDigest: [{ path: "raw/agent/Agent Loop 失败复盘.md", excerpt: "Fixed workflow and open agent layering." }]
    });

    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
    expect(requests[0].init.method).toBe("POST");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
    expect(synthesis).toEqual({
      kind: "ANSWER",
      answer: "Provider answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
      groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
    });
    expect(JSON.stringify(envelopes)).not.toContain("test-api-key");
    expect(envelopes[0]).toMatchObject({
      providerCallId: "open-agent-synthesize-1",
      envelope: {
        request: {
          headers: {
            Authorization: "[REDACTED]"
          }
        }
      }
    });
  });

  it("maps plan request to chat completions and parses strict JSON", async () => {
    const requests: Array<{ url: string; init: RequestInit }> = [];
    const envelopes: unknown[] = [];
    const provider = createOpenAiCompatibleOpenAgentProvider({
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
                  content: JSON.stringify({
                    objective: "总结 AI agent 架构",
                    outputPolicy: "ANSWER_ONLY",
                    steps: ["scan", "answer"],
                    contextHints: ["raw/agent"]
                  })
                }
              }
            ],
            usage: { prompt_tokens: 3, completion_tokens: 4, total_tokens: 7 }
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        );
      },
      onRawEnvelope: (envelope) => {
        envelopes.push(envelope);
      }
    });

    const plan = await provider.plan({ objective: "总结 AI agent 架构", outputPolicy: "ANSWER_ONLY" });

    expect(requests[0].url).toBe("https://token-plan-cn.xiaomimimo.com/v1/chat/completions");
    expect(requests[0].init.method).toBe("POST");
    expect((requests[0].init.headers as Record<string, string>).Authorization).toBe("Bearer test-api-key");
    expect(JSON.parse(requests[0].init.body as string).model).toBe("mimo-test-model");
    expect(plan).toEqual({
      objective: "总结 AI agent 架构",
      outputPolicy: "ANSWER_ONLY",
      steps: ["scan", "answer"],
      contextHints: ["raw/agent"]
    });
    expect(JSON.stringify(envelopes)).not.toContain("test-api-key");
    expect(envelopes[0]).toMatchObject({
      providerCallId: "open-agent-plan-1",
      envelope: {
        request: {
          headers: {
            Authorization: "[REDACTED]"
          }
        }
      }
    });
  });

  it("parses fenced JSON next actions", async () => {
    const provider = createOpenAiCompatibleOpenAgentProvider({
      apiKey: "test-api-key",
      baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
      model: "mimo-test-model",
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [
              {
                finish_reason: "stop",
                message: {
                  role: "assistant",
                  content: "```json\n{\"action\":\"SOLVED\",\"summary\":\"Enough context gathered.\"}\n```"
                }
              }
            ]
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    const action = await provider.nextAction!({
      iteration: 1,
      plan: {
        objective: "总结 AI agent 架构",
        outputPolicy: "ANSWER_ONLY",
        steps: ["scan"],
        contextHints: []
      },
      groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
    });

    expect(action).toEqual({
      action: "SOLVED",
      summary: "Enough context gathered."
    });
  });

  it("normalizes string contextHints from provider plans", async () => {
    const provider = createOpenAiCompatibleOpenAgentProvider({
      apiKey: "test-api-key",
      baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
      model: "mimo-test-model",
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [
              {
                finish_reason: "stop",
                message: {
                  role: "assistant",
                  content: JSON.stringify({
                    objective: "总结 AI agent 架构",
                    outputPolicy: "ANSWER_ONLY",
                    steps: ["scan", "answer"],
                    contextHints: "raw/agent knowledge-base"
                  })
                }
              }
            ]
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    const plan = await provider.plan({ objective: "总结 AI agent 架构", outputPolicy: "ANSWER_ONLY" });

    expect(typeof plan).toBe("object");
    if (typeof plan === "string") {
      throw new Error("Expected parsed plan object.");
    }
    expect(plan.contextHints).toEqual(["raw/agent knowledge-base"]);
  });

  it("throws validation error for invalid JSON", async () => {
    const provider = createOpenAiCompatibleOpenAgentProvider({
      apiKey: "test-api-key",
      baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
      model: "mimo-test-model",
      fetch: async () =>
        new Response(
          JSON.stringify({
            model: "mimo-test-model",
            choices: [{ finish_reason: "stop", message: { role: "assistant", content: "not-json" } }]
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
    });

    await expect(provider.plan({ objective: "总结 AI agent 架构", outputPolicy: "ANSWER_ONLY" })).rejects.toBeInstanceOf(
      OpenAgentProviderValidationError
    );
  });
});
