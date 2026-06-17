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

  test("runAssistantTask streams lifecycle events into interim session updates", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const updates: unknown[] = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });

      if (url === "/v1/workspaces/ws_123/agent-runs") {
        return jsonEnvelope(runEnvelope({
          status: "QUEUED",
          outputKind: "none",
          displayText: null,
        }));
      }

      if (url === "/v1/agent-runs/run_123/events/stream") {
        return sseResponse([
          sseEvent({
            id: "evt_1",
            event: "RUNNING",
            data: {
              eventId: "evt_1",
              runId: "run_123",
              eventType: "RUNNING",
              status: "RUNNING",
              message: "Worker attempt running",
              createdAt: "2026-06-17T10:00:01Z",
            },
          }),
          sseEvent({
            id: "evt_2",
            event: "COMPLETED",
            data: {
              eventId: "evt_2",
              runId: "run_123",
              eventType: "COMPLETED",
              status: "SUCCEEDED",
              message: "Worker response recorded",
              createdAt: "2026-06-17T10:00:02Z",
            },
          }),
        ]);
      }

      if (url === "/v1/agent-runs/run_123") {
        return jsonEnvelope(runEnvelope({
          status: "SUCCEEDED",
          outputKind: "answer",
          displayText: "已完成",
        }));
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const session = await runAssistantTask(fetcher, {
      workspaceId: "ws_123",
      userMessage: "总结当前知识库",
      streamEvents: true,
      maxPolls: 1,
      onUpdate: (update) => {
        updates.push(update);
      },
    });

    expect(updates).toMatchObject([
      {
        runId: "run_123",
        status: "RUNNING",
        title: "Run 正在执行",
        progress: 55,
        events: [{ time: "10:00:01", label: "Worker attempt running" }],
      },
      {
        runId: "run_123",
        status: "SUCCEEDED",
        title: "Run 已完成",
        progress: 100,
        events: [
          { time: "10:00:01", label: "Worker attempt running" },
          { time: "10:00:02", label: "Worker response recorded" },
        ],
      },
    ]);
    expect(session).toMatchObject({
      runId: "run_123",
      status: "SUCCEEDED",
      displayText: "已完成",
      events: [
        { time: "10:00:01", label: "Worker attempt running" },
        { time: "10:00:02", label: "Worker response recorded" },
      ],
    });
    expect(calls.map((call) => call.url)).toEqual([
      "/v1/workspaces/ws_123/agent-runs",
      "/v1/agent-runs/run_123/events/stream",
      "/v1/agent-runs/run_123",
    ]);
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

function sseResponse(chunks: string[]) {
  return Promise.resolve(
    new Response(
      new ReadableStream({
        start(controller) {
          const encoder = new TextEncoder();
          for (const chunk of chunks) {
            controller.enqueue(encoder.encode(chunk));
          }
          controller.close();
        },
      }),
      {
        status: 200,
        headers: {
          "content-type": "text/event-stream",
        },
      },
    ),
  );
}

function sseEvent(input: { id: string; event: string; data: unknown }) {
  return `id:${input.id}\nevent:${input.event}\ndata:${JSON.stringify(input.data)}\n\n`;
}
