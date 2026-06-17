import { describe, expect, test } from "vitest";
import { modeForAssistantMessage, runAssistantTask } from "../../frontend/src/features/assistant/run-session.js";

describe("frontend assistant run session", () => {
  test("modeForAssistantMessage routes write-intent prompts to llm open-agent candidate approval", () => {
    expect(modeForAssistantMessage("准备候选落库")).toBe("llm-open-agent");
    expect(modeForAssistantMessage("请写入知识库草稿")).toBe("llm-open-agent");
    expect(modeForAssistantMessage("总结当前知识库")).toBe("deterministic-open-agent");
  });

  test("runAssistantTask creates a run, polls until approval pause, and maps a safe session view", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    let pollCount = 0;
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });

      if (url === "/v1/workspaces/ws_123/agent-runs") {
        expect(init?.body).toBe(JSON.stringify({
          userMessage: "准备候选补丁",
          mode: "llm-open-agent",
        }));
        expect(init?.body).not.toContain("workspaceRoot");
        return jsonEnvelope(runEnvelope({
          status: "QUEUED",
          outputKind: "none",
          displayText: null,
        }));
      }

      if (url === "/v1/agent-runs/run_123") {
        pollCount += 1;
        return jsonEnvelope(runEnvelope(pollCount === 1
          ? {
              status: "RUNNING",
              outputKind: "none",
              displayText: null,
            }
          : {
              status: "WAITING_APPROVAL",
              outputKind: "candidate-patch",
              displayText: "候选补丁等待审批",
              requiresApproval: true,
              artifactRefs: [".agent-runs/run_123/patch.json"],
              targetWorkspacePaths: ["knowledge-base/topics/frontend.md"],
              source: {
                runtimePrivate: true,
              },
            }));
      }

      if (url === "/v1/agent-runs/run_123/events") {
        return jsonEnvelope([
          {
            eventId: "evt_1",
            runId: "run_123",
            eventType: "RUN_QUEUED",
            status: "QUEUED",
            message: "Run queued",
            createdAt: "2026-06-17T10:00:00Z",
          },
          {
            eventId: "evt_2",
            runId: "run_123",
            eventType: "WAITING_APPROVAL",
            status: "WAITING_APPROVAL",
            message: "Approval requested",
            createdAt: "2026-06-17T10:00:02Z",
            rawProviderPayload: {
              token: "must-not-render",
            },
          },
        ]);
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const session = await runAssistantTask(fetcher, {
      workspaceId: "ws_123",
      userMessage: "准备候选补丁",
      mode: "llm-open-agent",
      maxPolls: 3,
    });

    expect(session).toEqual({
      runId: "run_123",
      status: "WAITING_APPROVAL",
      terminal: true,
      title: "Run 正在等待审批",
      progress: 72,
      displayText: "候选补丁等待审批",
      events: [
        {
          time: "10:00:00",
          label: "Run queued",
        },
        {
          time: "10:00:02",
          label: "Approval requested",
        },
      ],
      approval: {
        status: "PENDING",
        artifactRefs: [".agent-runs/run_123/patch.json"],
        targetWorkspacePaths: ["knowledge-base/topics/frontend.md"],
        wroteWorkspace: false,
      },
    });
    expect(calls.map((call) => call.url)).toEqual([
      "/v1/workspaces/ws_123/agent-runs",
      "/v1/agent-runs/run_123",
      "/v1/agent-runs/run_123",
      "/v1/agent-runs/run_123/events",
    ]);
    expect(JSON.stringify(session)).not.toContain("runtimePrivate");
    expect(JSON.stringify(session)).not.toContain("must-not-render");
  });

  test("runAssistantTask returns a non-terminal polling view when maxPolls is reached", async () => {
    const fetcher = async (url: string) => {
      if (url === "/v1/workspaces/ws_123/agent-runs") {
        return jsonEnvelope(runEnvelope({
          status: "QUEUED",
          outputKind: "none",
          displayText: null,
        }));
      }

      if (url === "/v1/agent-runs/run_123") {
        return jsonEnvelope(runEnvelope({
          status: "RUNNING",
          outputKind: "none",
          displayText: null,
        }));
      }

      if (url === "/v1/agent-runs/run_123/events") {
        return jsonEnvelope([]);
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const session = await runAssistantTask(fetcher, {
      workspaceId: "ws_123",
      userMessage: "继续整理",
      maxPolls: 1,
    });

    expect(session).toMatchObject({
      runId: "run_123",
      status: "RUNNING",
      terminal: false,
      title: "Run 正在执行",
      progress: 55,
    });
  });

  test("runAssistantTask waits between running polls so browser submissions can observe terminal runs", async () => {
    let pollCount = 0;
    const startedAt = Date.now();
    const fetcher = async (url: string) => {
      if (url === "/v1/workspaces/ws_123/agent-runs") {
        return jsonEnvelope(runEnvelope({
          status: "QUEUED",
          outputKind: "none",
          displayText: null,
        }));
      }

      if (url === "/v1/agent-runs/run_123") {
        pollCount += 1;
        return jsonEnvelope(runEnvelope(pollCount < 2
          ? {
              status: "RUNNING",
              outputKind: "none",
              displayText: null,
            }
          : {
              status: "SUCCEEDED",
              outputKind: "answer",
              displayText: "已完成",
            }));
      }

      if (url === "/v1/agent-runs/run_123/events") {
        return jsonEnvelope([]);
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const session = await runAssistantTask(fetcher, {
      workspaceId: "ws_123",
      userMessage: "继续整理",
      maxPolls: 3,
      pollDelayMs: 15,
    });

    expect(session).toMatchObject({
      runId: "run_123",
      status: "SUCCEEDED",
      terminal: true,
      title: "Run 已完成",
      progress: 100,
      displayText: "已完成",
    });
    expect(pollCount).toBe(2);
    expect(Date.now() - startedAt).toBeGreaterThanOrEqual(15);
  });
});

function runEnvelope(overrides: Record<string, unknown>) {
  return {
    runId: "run_123",
    workspaceId: "ws_123",
    status: "QUEUED",
    outputKind: "none",
    displayText: null,
    requiresConfirmation: false,
    requiresApproval: false,
    artifactRefs: [],
    wroteWorkspace: false,
    targetWorkspacePaths: [],
    createdAt: "2026-06-17T10:00:00Z",
    updatedAt: "2026-06-17T10:00:01Z",
    apiKeySecretRef: "secret://must-not-render",
    ...overrides,
  };
}

function jsonEnvelope(data: unknown) {
  return Promise.resolve(
    new Response(
      JSON.stringify({
        schemaVersion: "java-backend-api.v1",
        ok: true,
        data,
      }),
      {
        status: 200,
        headers: {
          "content-type": "application/json",
        },
      },
    ),
  );
}
