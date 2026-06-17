import { describe, expect, test } from "vitest";
import { sanitizeForPublicUi } from "../../frontend/src/shared/safety/public-fields.js";

describe("frontend public safety filter", () => {
  test("sanitizeForPublicUi removes sensitive fields recursively before rendering", () => {
    const sanitized = sanitizeForPublicUi({
      workspaceId: "ws_123",
      name: "Agent Loop Core",
      apiKeySecretRef: "secret_ref_should_not_render",
      serverStorageRef: "/Users/didi/workspaces/ws_123",
      nested: {
        authorization: "Bearer secret_token",
        cookie: "session=secret",
        source: {
          runtimePrivate: true,
        },
        providerPayload: {
          token: "raw-provider-secret",
        },
      },
      artifact: {
        artifactId: "artifact_123",
        targetWorkspacePaths: ["knowledge-base/topics/frontend.md"],
        wroteWorkspace: false,
      },
    });

    expect(sanitized).toEqual({
      workspaceId: "ws_123",
      name: "Agent Loop Core",
      nested: {},
      artifact: {
        artifactId: "artifact_123",
        targetWorkspacePaths: ["knowledge-base/topics/frontend.md"],
        wroteWorkspace: false,
      },
    });
  });

  test("sanitizeForPublicUi redacts server absolute paths inside string values", () => {
    const sanitized = sanitizeForPublicUi({
      displayText: "report at /private/tmp/agent-run/report.md and /Users/didi/workspace/raw.md",
      publicPath: "knowledge-base/topics/frontend.md",
    });

    expect(sanitized).toEqual({
      displayText: "report at [redacted-path] and [redacted-path]",
      publicPath: "knowledge-base/topics/frontend.md",
    });
  });

  test("sanitizeForPublicUi preserves approved approval and run view fields", () => {
    const sanitized = sanitizeForPublicUi({
      runId: "run_123",
      status: "WAITING_APPROVAL",
      displayText: "等待审批",
      approvalStatus: "PENDING",
      targetWorkspacePaths: ["knowledge-base/topics/frontend.md"],
      wroteWorkspace: false,
      retryable: false,
    });

    expect(sanitized).toEqual({
      runId: "run_123",
      status: "WAITING_APPROVAL",
      displayText: "等待审批",
      approvalStatus: "PENDING",
      targetWorkspacePaths: ["knowledge-base/topics/frontend.md"],
      wroteWorkspace: false,
      retryable: false,
    });
  });
});
