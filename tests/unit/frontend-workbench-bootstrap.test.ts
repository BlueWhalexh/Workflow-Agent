import { describe, expect, test } from "vitest";
import { loadWorkbenchBootstrapView } from "../../frontend/src/app/bootstrap.js";

describe("frontend workbench bootstrap", () => {
  test("loadWorkbenchBootstrapView maps backend workspace bootstrap into the workbench view model", async () => {
    const calls: string[] = [];
    const fetcher = async (url: string) => {
      calls.push(url);

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
    expect(calls).toEqual(["/v1/me", "/v1/workspaces"]);
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
