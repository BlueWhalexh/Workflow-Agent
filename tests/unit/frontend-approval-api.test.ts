import { describe, expect, test } from "vitest";
import {
  decideRunApproval,
  listRunApprovals,
} from "../../frontend/src/features/approvals/approval-api.js";

describe("frontend approval API adapter", () => {
  test("listRunApprovals maps backend approval records without private fields", async () => {
    const fetcher = async (url: string) => {
      expect(url).toBe("/v1/agent-runs/run_123/approvals");
      return jsonEnvelope([
        {
          approvalId: "appr_1",
          runId: "run_123",
          status: "PENDING",
          decision: null,
          artifactRef: ".agent-runs/run_123/candidate.patch",
          targetWorkspacePaths: ["knowledge-base/topics/run_123.md"],
          requestedByUserId: "user_dev",
          decidedByUserId: null,
          createdAt: "2026-06-17T10:00:00Z",
          decidedAt: null,
          apiKeySecretRef: "secret://must-not-render",
          rawProviderPayload: {
            token: "must-not-render",
          },
        },
      ]);
    };

    const approvals = await listRunApprovals(fetcher, "run_123");

    expect(approvals).toEqual([
      {
        approvalId: "appr_1",
        runId: "run_123",
        status: "PENDING",
        decision: null,
        artifactRef: ".agent-runs/run_123/candidate.patch",
        targetWorkspacePaths: ["knowledge-base/topics/run_123.md"],
        requestedByUserId: "user_dev",
        decidedByUserId: null,
        createdAt: "2026-06-17T10:00:00Z",
        decidedAt: null,
      },
    ]);
    expect(JSON.stringify(approvals)).not.toContain("secret://must-not-render");
    expect(JSON.stringify(approvals)).not.toContain("rawProviderPayload");
    expect(JSON.stringify(approvals)).not.toContain("must-not-render");
  });

  test("decideRunApproval posts a decision and maps the public approval record", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return jsonEnvelope({
        approvalId: "appr_1",
        runId: "run_123",
        status: "DECIDED",
        decision: "APPROVED",
        artifactRef: ".agent-runs/run_123/candidate.patch",
        targetWorkspacePaths: ["knowledge-base/topics/run_123.md"],
        requestedByUserId: "user_dev",
        decidedByUserId: "user_dev",
        createdAt: "2026-06-17T10:00:00Z",
        decidedAt: "2026-06-17T10:01:00Z",
        serverStorageRef: "/Users/didi/private/workspace",
      });
    };

    const approval = await decideRunApproval(fetcher, "run_123", {
      approvalId: "appr_1",
      decision: "APPROVED",
    });

    expect(approval).toEqual({
      approvalId: "appr_1",
      runId: "run_123",
      status: "DECIDED",
      decision: "APPROVED",
      artifactRef: ".agent-runs/run_123/candidate.patch",
      targetWorkspacePaths: ["knowledge-base/topics/run_123.md"],
      requestedByUserId: "user_dev",
      decidedByUserId: "user_dev",
      createdAt: "2026-06-17T10:00:00Z",
      decidedAt: "2026-06-17T10:01:00Z",
    });
    expect(calls).toEqual([
      {
        url: "/v1/agent-runs/run_123/approvals",
        init: {
          method: "POST",
          credentials: "include",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            approvalId: "appr_1",
            decision: "APPROVED",
          }),
        },
      },
    ]);
    expect(JSON.stringify(approval)).not.toContain("/Users/didi/private/workspace");
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
