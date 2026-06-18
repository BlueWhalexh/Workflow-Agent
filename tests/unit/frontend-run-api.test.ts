import { describe, expect, test } from "vitest";
import {
  cancelAgentRun,
  createAgentRun,
  getAgentRun,
  listWorkspaceRuns,
  listRunEvents,
} from "../../frontend/src/features/runs/run-api.js";

describe("frontend run API adapter", () => {
  test("createAgentRun posts a safe backend request and maps the public run view", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }
      return jsonEnvelope({
        runId: "run_123",
        workspaceId: "ws_123",
        status: "WAITING_APPROVAL",
        outputKind: "candidate-patch",
        displayText: "Candidate patch needs approval.",
        requiresConfirmation: false,
        requiresApproval: true,
        artifactRefs: [".agent-runs/run_123/artifact.json"],
        wroteWorkspace: false,
        targetWorkspacePaths: ["knowledge-base/drafts/run_123.md"],
        createdAt: "2026-06-17T10:00:00Z",
        updatedAt: "2026-06-17T10:00:01Z",
        apiKeySecretRef: "secret://must-not-render",
        source: {
          runtimePrivate: true,
        },
      });
    };

    const run = await createAgentRun(fetcher, "ws_123", {
      userMessage: "准备候选补丁",
      mode: "llm-open-agent",
      providerRuntimeRef: "credential.mimo",
    });

    expect(run).toEqual({
      runId: "run_123",
      workspaceId: "ws_123",
      status: "WAITING_APPROVAL",
      outputKind: "candidate-patch",
      displayText: "Candidate patch needs approval.",
      requiresConfirmation: false,
      requiresApproval: true,
      artifactRefs: [".agent-runs/run_123/artifact.json"],
      wroteWorkspace: false,
      targetWorkspacePaths: ["knowledge-base/drafts/run_123.md"],
      errorCode: undefined,
      createdAt: "2026-06-17T10:00:00Z",
      updatedAt: "2026-06-17T10:00:01Z",
    });
    expect(calls).toHaveLength(2);
    expect(calls[0]).toEqual({
      url: "/v1/session/csrf",
      init: {
        credentials: "include",
        headers: {
          Accept: "application/json",
        },
      },
    });
    expect(calls[1]).toEqual({
      url: "/v1/workspaces/ws_123/agent-runs",
      init: {
        method: "POST",
        credentials: "include",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-CSRF-Token": "csrf_123",
        },
        body: JSON.stringify({
          userMessage: "准备候选补丁",
          mode: "llm-open-agent",
          providerRuntimeRef: "credential.mimo",
        }),
      },
    });
    expect(calls[1].init?.body).not.toContain("workspaceRoot");
    expect(JSON.stringify(run)).not.toContain("secret://must-not-render");
    expect(JSON.stringify(run)).not.toContain("runtimePrivate");
  });

  test("getAgentRun and cancelAgentRun use backend run endpoints", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }
      return jsonEnvelope({
        runId: "run_123",
        workspaceId: "ws_123",
        status: init?.method === "POST" ? "CANCELLED" : "RUNNING",
        outputKind: "none",
        displayText: null,
        requiresConfirmation: false,
        requiresApproval: false,
        artifactRefs: [],
        wroteWorkspace: false,
        targetWorkspacePaths: [],
        createdAt: "2026-06-17T10:00:00Z",
        updatedAt: "2026-06-17T10:00:01Z",
      });
    };

    await expect(getAgentRun(fetcher, "run_123")).resolves.toMatchObject({
      runId: "run_123",
      status: "RUNNING",
    });
    await expect(cancelAgentRun(fetcher, "run_123")).resolves.toMatchObject({
      runId: "run_123",
      status: "CANCELLED",
    });
    expect(calls).toEqual([
      {
        url: "/v1/agent-runs/run_123",
        init: {
          credentials: "include",
          headers: {
            Accept: "application/json",
          },
        },
      },
      {
        url: "/v1/session/csrf",
        init: {
          credentials: "include",
          headers: {
            Accept: "application/json",
          },
        },
      },
      {
        url: "/v1/agent-runs/run_123/cancel",
        init: {
          method: "POST",
          credentials: "include",
          headers: {
            Accept: "application/json",
            "X-CSRF-Token": "csrf_123",
          },
        },
      },
    ]);
  });

  test("listWorkspaceRuns loads recent public run envelopes for a workspace", async () => {
    const fetcher = async (url: string) => {
      expect(url).toBe("/v1/workspaces/ws_123/agent-runs");
      return jsonEnvelope([
        {
          runId: "run_new",
          workspaceId: "ws_123",
          status: "SUCCEEDED",
          outputKind: "answer",
          displayText: "已生成摘要",
          requiresConfirmation: false,
          requiresApproval: false,
          artifactRefs: [".agent-runs/run_new/report.md"],
          wroteWorkspace: false,
          targetWorkspacePaths: [],
          createdAt: "2026-06-17T10:00:00Z",
          updatedAt: "2026-06-17T10:00:03Z",
          source: {
            runtimePrivate: true,
          },
        },
        {
          runId: "run_old",
          workspaceId: "ws_123",
          status: "WAITING_APPROVAL",
          outputKind: "candidate-patch",
          displayText: "候选补丁等待审批",
          requiresConfirmation: false,
          requiresApproval: true,
          artifactRefs: [".agent-runs/run_old/patch.json"],
          wroteWorkspace: false,
          targetWorkspacePaths: ["knowledge-base/topics/run-old.md"],
          createdAt: "2026-06-17T09:59:00Z",
          updatedAt: "2026-06-17T09:59:03Z",
          rawProviderPayload: {
            token: "must-not-render",
          },
        },
      ]);
    };

    const runs = await listWorkspaceRuns(fetcher, "ws_123");

    expect(runs.map((run) => run.runId)).toEqual(["run_new", "run_old"]);
    expect(runs[0]).toEqual({
      runId: "run_new",
      workspaceId: "ws_123",
      status: "SUCCEEDED",
      outputKind: "answer",
      displayText: "已生成摘要",
      requiresConfirmation: false,
      requiresApproval: false,
      artifactRefs: [".agent-runs/run_new/report.md"],
      wroteWorkspace: false,
      targetWorkspacePaths: [],
      errorCode: undefined,
      createdAt: "2026-06-17T10:00:00Z",
      updatedAt: "2026-06-17T10:00:03Z",
    });
    expect(JSON.stringify(runs)).not.toContain("runtimePrivate");
    expect(JSON.stringify(runs)).not.toContain("must-not-render");
  });

  test("listRunEvents maps lifecycle events without runtime-private fields", async () => {
    const fetcher = async (url: string) => {
      expect(url).toBe("/v1/agent-runs/run_123/events");
      return jsonEnvelope([
        {
          eventId: "evt_1",
          runId: "run_123",
          eventType: "RUN_QUEUED",
          status: "QUEUED",
          message: "Run queued",
          createdAt: "2026-06-17T10:00:00Z",
          rawProvider: {
            token: "must-not-render",
          },
        },
        {
          eventId: "evt_2",
          runId: "run_123",
          eventType: "COMPLETED",
          status: "SUCCEEDED",
          message: "Run completed",
          createdAt: "2026-06-17T10:00:01Z",
          runtime: {
            workspaceRoot: "/Users/didi/private",
          },
        },
      ]);
    };

    const events = await listRunEvents(fetcher, "run_123");

    expect(events).toEqual([
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
        eventType: "COMPLETED",
        status: "SUCCEEDED",
        message: "Run completed",
        createdAt: "2026-06-17T10:00:01Z",
      },
    ]);
    expect(JSON.stringify(events)).not.toContain("must-not-render");
    expect(JSON.stringify(events)).not.toContain("/Users/didi/private");
  });
});

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
