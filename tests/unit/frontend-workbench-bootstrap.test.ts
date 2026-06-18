import { describe, expect, test } from "vitest";
import {
  applyApprovalDecisionToWorkbench,
  applyAssistantRunProgressToWorkbench,
  activeWorkspaceIdFromWorkbench,
  applyArtifactContentToWorkbench,
  applyAssistantRunSessionToWorkbench,
  loadWorkbenchBootstrapView,
} from "../../frontend/src/app/bootstrap.js";
import { workbenchFixture } from "../../frontend/src/app/fixtures.js";
import type { RunApprovalView } from "../../frontend/src/features/approvals/approval-api.js";
import type { ArtifactContentView } from "../../frontend/src/features/artifacts/artifact-api.js";
import type { AssistantRunSessionView } from "../../frontend/src/features/assistant/run-session.js";

describe("frontend workbench bootstrap", () => {
  test("loadWorkbenchBootstrapView maps backend workspace bootstrap into the workbench view model", async () => {
    const calls: string[] = [];
    const fetcher = async (url: string) => {
      calls.push(url);

      if (url === "/v1/ops/integration-contract") {
        return integrationContractEnvelope();
      }

      if (url === "/v1/me") {
        return jsonEnvelope({
          userId: "user_dev",
          teamId: "team_dev",
          displayName: "Dev User",
          apiKeySecretRef: "must_not_escape",
        });
      }

      if (url === "/v1/workspaces") {
        return jsonEnvelope([
          {
            workspaceId: "ws_backend",
            name: "后端联通知识库",
            defaultBranch: "main",
            status: "ACTIVE",
            serverStorageRef: "/Users/didi/private/workspace",
          },
          {
            workspaceId: "ws_second",
            name: "第二工作区",
            defaultBranch: "develop",
            status: "ACTIVE",
          },
        ]);
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const bootstrap = await loadWorkbenchBootstrapView(fetcher);

    expect(bootstrap.status).toBe("connected");
    expect(bootstrap.statusLabel).toBe("后端已连接");
    expect(bootstrap.data.workspaceName).toBe("后端联通知识库");
    expect(bootstrap.data.breadcrumb[0]).toBe("后端联通知识库");
    expect(bootstrap.data.treeItems.slice(0, 2)).toEqual([
      {
        id: "ws_backend",
        icon: "▾",
        label: "后端联通知识库",
        count: "ACTIVE",
        active: true,
      },
      {
        id: "ws_second",
        icon: "•",
        label: "第二工作区",
        count: "ACTIVE",
        depth: true,
      },
    ]);
    expect(JSON.stringify(bootstrap)).not.toContain("apiKeySecretRef");
    expect(JSON.stringify(bootstrap)).not.toContain("serverStorageRef");
    expect(JSON.stringify(bootstrap)).not.toContain("/Users/didi/private/workspace");
    expect(calls).toEqual(["/v1/ops/integration-contract", "/v1/me", "/v1/workspaces"]);
  });

  test("loadWorkbenchBootstrapView falls back to the fixture when the backend is unavailable", async () => {
    const fetcher = async () =>
      new Response("service unavailable", {
        status: 503,
        statusText: "Service Unavailable",
      });

    const bootstrap = await loadWorkbenchBootstrapView(fetcher);

    expect(bootstrap.status).toBe("fixture-fallback");
    expect(bootstrap.statusLabel).toBe("离线预览");
    expect(bootstrap.data.workspaceName).toBe("Agent Loop Core");
  });

  test("loadWorkbenchBootstrapView does not connect when the backend integration contract is incomplete", async () => {
    const calls: string[] = [];
    const fetcher = async (url: string) => {
      calls.push(url);

      if (url === "/v1/ops/integration-contract") {
        return integrationContractEnvelope({
          frontendRequiredEndpoints: [{ method: "GET", path: "/v1/me" }],
          capabilities: {
            asyncAgentRuns: true,
            sseRunEvents: false,
            approvalBoundary: true,
            artifactRegistry: true,
          },
        });
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const bootstrap = await loadWorkbenchBootstrapView(fetcher);

    expect(bootstrap.status).toBe("contract-mismatch");
    expect(bootstrap.statusLabel).toBe("后端契约不完整");
    expect(bootstrap.data.workspaceName).toBe("Agent Loop Core");
    expect(calls).toEqual(["/v1/ops/integration-contract"]);
  });

  test("loadWorkbenchBootstrapView stays connected when the backend has no visible workspaces", async () => {
    const fetcher = async (url: string) => {
      if (url === "/v1/ops/integration-contract") {
        return integrationContractEnvelope();
      }

      if (url === "/v1/me") {
        return jsonEnvelope({
          userId: "user_dev",
          teamId: "team_dev",
          displayName: "Dev User",
        });
      }

      if (url === "/v1/workspaces") {
        return jsonEnvelope([]);
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    const bootstrap = await loadWorkbenchBootstrapView(fetcher);

    expect(bootstrap.status).toBe("connected");
    expect(bootstrap.statusLabel).toBe("后端已连接");
    expect(bootstrap.data.workspaceName).toBe("暂无工作区");
    expect(bootstrap.data.treeItems).toEqual([]);
    expect(bootstrap.data.breadcrumb[0]).toBe("暂无工作区");
  });

  test("applyAssistantRunSessionToWorkbench maps a live run session into the assistant panel safely", () => {
    const unsafeSession: AssistantRunSessionView & { source: unknown } = {
      runId: "run_live",
      status: "SUCCEEDED",
      terminal: true,
      title: "Run 已完成",
      progress: 100,
      displayText: "已整理当前知识页",
      events: [{ time: "10:12:13", label: "Worker response recorded" }],
      approval: {
        status: "NONE",
        artifactRefs: [".agent-runs/run_live/report.md"],
        targetWorkspacePaths: ["knowledge-base/topics/live.md"],
        wroteWorkspace: true,
      },
      source: {
        apiKeySecretRef: "secret://must-not-render",
        rawProviderPayload: "must-not-render",
      },
    };

    const nextWorkbench = applyAssistantRunSessionToWorkbench(
      {
        ...workbenchFixture,
        treeItems: [
          { id: "ws_123", icon: "▾", label: "主工作区", count: "ACTIVE", active: true },
          { id: "ws_456", icon: "•", label: "备用工作区", count: "ACTIVE", depth: true },
        ],
      },
      unsafeSession,
      "整理当前页",
    );

    expect(activeWorkspaceIdFromWorkbench(nextWorkbench)).toBe("ws_123");
    expect(nextWorkbench.assistant.run).toEqual({
      title: "Run 已完成",
      id: "run_live",
      progress: 100,
      events: [{ time: "10:12:13", label: "Worker response recorded" }],
    });
    expect(nextWorkbench.assistant.messages.slice(-2)).toEqual([
      { author: "你", kind: "user", text: "整理当前页" },
      { author: "助手", kind: "ai", text: "已整理当前知识页" },
    ]);
    expect(nextWorkbench.assistant.approval).toEqual({
      title: "运行结果",
      summary: "已整理当前知识页",
      artifact: ".agent-runs/run_live/report.md",
      target: "knowledge-base/topics/live.md",
      wroteWorkspace: true,
    });
    expect(JSON.stringify(nextWorkbench)).not.toContain("apiKeySecretRef");
    expect(JSON.stringify(nextWorkbench)).not.toContain("must-not-render");
  });

  test("applyAssistantRunProgressToWorkbench refreshes run status without appending chat messages", () => {
    const nextWorkbench = applyAssistantRunProgressToWorkbench(workbenchFixture, {
      runId: "run_stream",
      status: "RUNNING",
      terminal: false,
      title: "Run 正在执行",
      progress: 55,
      displayText: null,
      events: [{ time: "10:00:01", label: "Worker attempt running" }],
      approval: {
        status: "NONE",
        artifactRefs: [],
        targetWorkspacePaths: [],
        wroteWorkspace: false,
      },
    });

    expect(nextWorkbench.assistant.run).toEqual({
      title: "Run 正在执行",
      id: "run_stream",
      progress: 55,
      events: [{ time: "10:00:01", label: "Worker attempt running" }],
    });
    expect(nextWorkbench.assistant.messages).toEqual(workbenchFixture.assistant.messages);
    expect(nextWorkbench.assistant.approval).toEqual(workbenchFixture.assistant.approval);
  });

  test("applyArtifactContentToWorkbench maps a read artifact into a safe approval preview", () => {
    const unsafeArtifact: ArtifactContentView & {
      source: unknown;
      apiKeySecretRef: string;
      rawProviderPayload: string;
    } = {
      artifactId: "artifact_123",
      runId: "run_live",
      artifactRef: ".agent-runs/run_live/report.md",
      kind: "REPORT",
      redactionStatus: "CLEAN",
      contentType: "text/markdown",
      createdAt: "2026-06-17T10:12:13Z",
      content: "## 候选补丁\n\n建议更新知识页。\n\nserver: /Users/didi/private/workspace/report.md",
      source: {
        serverStorageRef: "/Users/didi/private/workspace",
      },
      apiKeySecretRef: "secret://must-not-render",
      rawProviderPayload: "must-not-render",
    };

    const nextWorkbench = applyArtifactContentToWorkbench(workbenchFixture, unsafeArtifact);

    expect(nextWorkbench.assistant.approval.artifact).toBe(".agent-runs/run_live/report.md");
    expect(nextWorkbench.assistant.approval.artifactPreview).toEqual({
      title: "REPORT · CLEAN",
      contentType: "text/markdown",
      content: "## 候选补丁\n\n建议更新知识页。\n\nserver: [redacted-path]",
    });
    expect(JSON.stringify(nextWorkbench)).not.toContain("apiKeySecretRef");
    expect(JSON.stringify(nextWorkbench)).not.toContain("rawProviderPayload");
    expect(JSON.stringify(nextWorkbench)).not.toContain("must-not-render");
    expect(JSON.stringify(nextWorkbench)).not.toContain("/Users/didi/private/workspace");
  });

  test("applyApprovalDecisionToWorkbench maps approval decision as metadata without implying workspace writes", () => {
    const approval: RunApprovalView & {
      rawProviderPayload: string;
      serverStorageRef: string;
    } = {
      approvalId: "appr_1",
      runId: "run_live",
      status: "DECIDED",
      decision: "APPROVED",
      artifactRef: ".agent-runs/run_live/candidate.patch",
      targetWorkspacePaths: ["knowledge-base/topics/live.md"],
      requestedByUserId: "user_dev",
      decidedByUserId: "user_dev",
      createdAt: "2026-06-17T10:12:13Z",
      decidedAt: "2026-06-17T10:13:00Z",
      rawProviderPayload: "must-not-render",
      serverStorageRef: "/Users/didi/private/workspace",
    };

    const nextWorkbench = applyApprovalDecisionToWorkbench(
      {
        ...workbenchFixture,
        assistant: {
          ...workbenchFixture.assistant,
          approval: {
            ...workbenchFixture.assistant.approval,
            wroteWorkspace: false,
          },
        },
      },
      approval,
    );

    expect(nextWorkbench.assistant.approval).toMatchObject({
      title: "审批已批准",
      summary: "审批已批准；这是审批元数据更新，尚未执行候选补丁写入。",
      artifact: ".agent-runs/run_live/candidate.patch",
      target: "knowledge-base/topics/live.md",
      wroteWorkspace: false,
      approvalId: "appr_1",
      status: "DECIDED",
      decision: "APPROVED",
    });
    expect(nextWorkbench.assistant.messages.slice(-1)).toEqual([
      {
        author: "安全检查",
        kind: "ai",
        text: "审批已批准；这是审批元数据更新，尚未执行候选补丁写入。",
      },
    ]);
    expect(JSON.stringify(nextWorkbench)).not.toContain("must-not-render");
    expect(JSON.stringify(nextWorkbench)).not.toContain("/Users/didi/private/workspace");
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

function integrationContractEnvelope(overrides: Record<string, unknown> = {}) {
  return jsonEnvelope({
    schemaVersion: "java-backend-integration-contract.v1",
    publicEnvelopeSchema: "java-backend-api.v1",
    frontendRequiredEndpoints: [
      { method: "GET", path: "/v1/me" },
      { method: "GET", path: "/v1/workspaces" },
      { method: "POST", path: "/v1/workspaces/{workspaceId}/agent-runs" },
      { method: "GET", path: "/v1/agent-runs/{runId}/events/stream" },
      { method: "GET", path: "/v1/agent-runs/{runId}/artifacts" },
      { method: "POST", path: "/v1/agent-runs/{runId}/approvals" },
      { method: "GET", path: "/v1/ops/integration-contract" },
    ],
    capabilities: {
      asyncAgentRuns: true,
      sseRunEvents: true,
      approvalBoundary: true,
      artifactRegistry: true,
    },
    ...overrides,
  });
}
