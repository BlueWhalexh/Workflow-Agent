import { describe, expect, test } from "vitest";
import {
  loadAssistantRunSession,
  modeForAssistantMessage,
  runAssistantTask,
} from "../../frontend/src/features/assistant/run-session.js";

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

      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }

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
      artifacts: [],
    });
    expect(calls.map((call) => call.url)).toEqual([
      "/v1/session/csrf",
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
      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }

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
      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }

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

  test("runAssistantTask reads terminal run artifacts into a public preview", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });

      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }

      if (url === "/v1/workspaces/ws_123/agent-runs") {
        return jsonEnvelope(runEnvelope({
          status: "QUEUED",
          outputKind: "none",
          displayText: null,
        }));
      }

      if (url === "/v1/agent-runs/run_123") {
        return jsonEnvelope(runEnvelope({
          status: "SUCCEEDED",
          outputKind: "answer",
          displayText: "已生成知识库摘要",
          artifactRefs: [".agent-runs/run_123/report.md"],
        }));
      }

      if (url === "/v1/agent-runs/run_123/events") {
        return jsonEnvelope([]);
      }

      if (url === "/v1/agent-runs/run_123/artifacts") {
        return jsonEnvelope([
          {
            artifactId: "art_1",
            runId: "run_123",
            artifactRef: ".agent-runs/run_123/report.md",
            kind: "report",
            redactionStatus: "redacted",
            contentType: "text/markdown",
            createdAt: "2026-06-17T10:00:03Z",
            serverStorageRef: "secret://must-not-render",
          },
        ]);
      }

      if (url === "/v1/artifacts/art_1") {
        return jsonEnvelope({
          artifactId: "art_1",
          runId: "run_123",
          artifactRef: ".agent-runs/run_123/report.md",
          kind: "report",
          redactionStatus: "redacted",
          contentType: "text/markdown",
          createdAt: "2026-06-17T10:00:03Z",
          content: "# 运行摘要\n\n已完成本地 runtime smoke。",
          rawProviderPayload: {
            token: "must-not-render",
          },
        });
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const session = await runAssistantTask(fetcher, {
      workspaceId: "ws_123",
      userMessage: "总结当前知识库",
      maxPolls: 1,
    });

    expect(session.artifacts).toEqual([
      {
        artifactId: "art_1",
        artifactRef: ".agent-runs/run_123/report.md",
        kind: "report",
        contentType: "text/markdown",
        content: "# 运行摘要\n\n已完成本地 runtime smoke。",
      },
    ]);
    expect(calls.map((call) => call.url)).toEqual([
      "/v1/session/csrf",
      "/v1/workspaces/ws_123/agent-runs",
      "/v1/agent-runs/run_123",
      "/v1/agent-runs/run_123/events",
      "/v1/agent-runs/run_123/artifacts",
      "/v1/artifacts/art_1",
    ]);
    expect(JSON.stringify(session)).not.toContain("serverStorageRef");
    expect(JSON.stringify(session)).not.toContain("must-not-render");
  });

  test("loadAssistantRunSession reopens an existing completed run with events and artifact preview", async () => {
    const calls: string[] = [];
    const fetcher = async (url: string) => {
      calls.push(url);

      if (url === "/v1/agent-runs/run_recent") {
        return jsonEnvelope(runEnvelope({
          runId: "run_recent",
          status: "SUCCEEDED",
          outputKind: "answer",
          displayText: "已生成最近摘要",
          artifactRefs: [".agent-runs/run_recent/report.md"],
          source: {
            runtimePrivate: true,
          },
        }));
      }

      if (url === "/v1/agent-runs/run_recent/events") {
        return jsonEnvelope([
          {
            eventId: "evt_1",
            runId: "run_recent",
            eventType: "RUN_QUEUED",
            status: "QUEUED",
            message: "Run queued",
            createdAt: "2026-06-17T10:00:00Z",
          },
          {
            eventId: "evt_2",
            runId: "run_recent",
            eventType: "COMPLETED",
            status: "SUCCEEDED",
            message: "Worker response recorded",
            createdAt: "2026-06-17T10:00:03Z",
          },
        ]);
      }

      if (url === "/v1/agent-runs/run_recent/artifacts") {
        return jsonEnvelope([
          {
            artifactId: "art_recent",
            runId: "run_recent",
            artifactRef: ".agent-runs/run_recent/report.md",
            kind: "report",
            redactionStatus: "clean",
            contentType: "text/markdown",
            createdAt: "2026-06-17T10:00:03Z",
            serverStorageRef: "/Users/didi/private/workspace",
          },
        ]);
      }

      if (url === "/v1/artifacts/art_recent") {
        return jsonEnvelope({
          artifactId: "art_recent",
          runId: "run_recent",
          artifactRef: ".agent-runs/run_recent/report.md",
          kind: "report",
          redactionStatus: "clean",
          contentType: "text/markdown",
          createdAt: "2026-06-17T10:00:03Z",
          content: "# 最近运行\n\n刷新后仍可打开。",
          rawProviderPayload: {
            token: "must-not-render",
          },
        });
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const session = await loadAssistantRunSession(fetcher, "run_recent");

    expect(session).toEqual({
      runId: "run_recent",
      status: "SUCCEEDED",
      terminal: true,
      title: "Run 已完成",
      progress: 100,
      displayText: "已生成最近摘要",
      events: [
        { time: "10:00:00", label: "Run queued" },
        { time: "10:00:03", label: "Worker response recorded" },
      ],
      approval: {
        status: "NONE",
        artifactRefs: [".agent-runs/run_recent/report.md"],
        targetWorkspacePaths: [],
        wroteWorkspace: false,
      },
      artifacts: [
        {
          artifactId: "art_recent",
          artifactRef: ".agent-runs/run_recent/report.md",
          kind: "report",
          contentType: "text/markdown",
          content: "# 最近运行\n\n刷新后仍可打开。",
        },
      ],
    });
    expect(calls).toEqual([
      "/v1/agent-runs/run_recent",
      "/v1/agent-runs/run_recent/events",
      "/v1/agent-runs/run_recent/artifacts",
      "/v1/artifacts/art_recent",
    ]);
    expect(JSON.stringify(session)).not.toContain("runtimePrivate");
    expect(JSON.stringify(session)).not.toContain("/Users/didi/private/workspace");
    expect(JSON.stringify(session)).not.toContain("must-not-render");
  });

  test("runAssistantTask streams lifecycle events into interim session updates", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const updates: unknown[] = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });

      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }

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
      "/v1/session/csrf",
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

function csrfEnvelope() {
  return jsonEnvelope({
    token: "csrf_123",
    headerName: "X-CSRF-Token",
  });
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
