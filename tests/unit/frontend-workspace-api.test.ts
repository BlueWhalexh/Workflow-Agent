import { describe, expect, test } from "vitest";
import { loadWorkspaceBootstrap } from "../../frontend/src/features/workspace/workspace-api.js";

describe("frontend workspace API adapter", () => {
  test("loadWorkspaceBootstrap maps /v1/me and /v1/workspaces into a public workspace view", async () => {
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
            workspaceId: "ws_123",
            name: "Team Knowledge",
            defaultBranch: "main",
            status: "ACTIVE",
            serverStorageRef: "/Users/didi/private/workspace",
          },
        ]);
      }

      throw new Error(`Unexpected URL ${url}`);
    };

    await expect(loadWorkspaceBootstrap(fetcher)).resolves.toEqual({
      principal: {
        userId: "user_dev",
        teamId: "team_dev",
        displayName: "Dev User",
      },
      workspaces: [
        {
          id: "ws_123",
          name: "Team Knowledge",
          defaultBranch: "main",
          status: "ACTIVE",
        },
      ],
      selectedWorkspaceId: "ws_123",
    });
    expect(calls).toEqual(["/v1/me", "/v1/workspaces"]);
  });

  test("loadWorkspaceBootstrap leaves selectedWorkspaceId undefined when no workspace is visible", async () => {
    const fetcher = async (url: string) => {
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

    await expect(loadWorkspaceBootstrap(fetcher)).resolves.toEqual({
      principal: {
        userId: "user_dev",
        teamId: "team_dev",
        displayName: "Dev User",
      },
      workspaces: [],
      selectedWorkspaceId: undefined,
    });
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
