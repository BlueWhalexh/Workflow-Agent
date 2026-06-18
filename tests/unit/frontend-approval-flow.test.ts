import { describe, expect, test } from "vitest";
import {
  decideLatestRunApproval,
  NoPendingApprovalError,
} from "../../frontend/src/features/approvals/approval-flow.js";

describe("frontend approval decision flow", () => {
  test("decideLatestRunApproval chooses the pending approval and posts the decision", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      calls.push({ url, init });

      if (url === "/v1/session/csrf") {
        return csrfEnvelope();
      }

      if (init?.method === "POST") {
        return jsonEnvelope({
          approvalId: "appr_pending",
          runId: "run_123",
          status: "DECIDED",
          decision: "REJECTED",
          artifactRef: ".agent-runs/run_123/candidate.patch",
          targetWorkspacePaths: ["knowledge-base/topics/run_123.md"],
          requestedByUserId: "user_dev",
          decidedByUserId: "user_dev",
          createdAt: "2026-06-17T10:00:00Z",
          decidedAt: "2026-06-17T10:01:00Z",
        });
      }

      return jsonEnvelope([
        {
          approvalId: "appr_old",
          runId: "run_123",
          status: "DECIDED",
          decision: "APPROVED",
          artifactRef: ".agent-runs/run_123/old.patch",
          targetWorkspacePaths: ["knowledge-base/topics/old.md"],
          requestedByUserId: "user_dev",
          decidedByUserId: "user_dev",
          createdAt: "2026-06-17T09:00:00Z",
          decidedAt: "2026-06-17T09:01:00Z",
        },
        {
          approvalId: "appr_pending",
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
    };

    const approval = await decideLatestRunApproval(fetcher, "run_123", "REJECTED");

    expect(approval.approvalId).toBe("appr_pending");
    expect(approval.decision).toBe("REJECTED");
    expect(calls).toHaveLength(3);
    expect(calls[0].url).toBe("/v1/agent-runs/run_123/approvals");
    expect(calls[1]).toEqual({
      url: "/v1/session/csrf",
      init: {
        credentials: "include",
        headers: {
          Accept: "application/json",
        },
      },
    });
    expect(calls[2]).toEqual({
      url: "/v1/agent-runs/run_123/approvals",
      init: {
        method: "POST",
        credentials: "include",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-CSRF-Token": "csrf_123",
        },
        body: JSON.stringify({
          approvalId: "appr_pending",
          decision: "REJECTED",
        }),
      },
    });
  });

  test("decideLatestRunApproval rejects when the run has no pending approvals", async () => {
    const fetcher = async () => jsonEnvelope([]);

    await expect(decideLatestRunApproval(fetcher, "run_123", "APPROVED")).rejects.toBeInstanceOf(
      NoPendingApprovalError,
    );
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
