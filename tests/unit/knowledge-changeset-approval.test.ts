import { describe, expect, it } from "vitest";
import {
  ChangesetApprovalConflictError,
  ChangesetApprovalValidationError,
  InMemoryChangesetApprovalRepository,
  decideChangesetApproval,
  requestChangesetApproval
} from "../../src/domain/knowledge/changeset/approval.js";

describe("structured KnowledgeChangeset approval", () => {
  it("consumes a matching pending changeset approval and returns the next apply event", async () => {
    const repository = new InMemoryChangesetApprovalRepository();
    await requestChangesetApproval(repository, {
      approvalId: "apr_demo",
      runId: "run_demo",
      changesetId: "cs_demo",
      requestedEventId: "evt_request_approval",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });

    const decision = await decideChangesetApproval(repository, {
      path: { runId: "run_demo", approvalId: "apr_demo" },
      body: {
        approvalId: "apr_demo",
        action: "approve",
        scope: "changeset",
        changesetId: "cs_demo",
        decidedAt: "2026-06-24T10:00:00.000Z",
        decidedBy: "user"
      },
      now: "2026-06-24T10:00:00.000Z"
    });

    expect(decision).toEqual({
      approvalId: "apr_demo",
      action: "approve",
      scope: "changeset",
      changesetId: "cs_demo",
      expiresAt: "2026-06-24T10:15:00.000Z",
      nextEvent: "changeset_apply_started",
      appliedAt: "2026-06-24T10:00:00.000Z"
    });
    await expect(repository.get("apr_demo")).resolves.toMatchObject({
      status: "consumed",
      decision: "approve",
      decidedAt: "2026-06-24T10:00:00.000Z"
    });
  });

  it("rejects path/body mismatches, non-changeset scope, and free-form chat fields", async () => {
    const repository = new InMemoryChangesetApprovalRepository();
    await requestChangesetApproval(repository, {
      approvalId: "apr_demo",
      runId: "run_demo",
      changesetId: "cs_demo",
      requestedEventId: "evt_request_approval",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_demo", approvalId: "apr_demo" },
        body: {
          approvalId: "apr_other",
          action: "approve",
          scope: "changeset",
          changesetId: "cs_demo",
          decidedAt: "2026-06-24T10:00:00.000Z",
          decidedBy: "user"
        },
        now: "2026-06-24T10:00:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalValidationError);

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_demo", approvalId: "apr_demo" },
        body: {
          approvalId: "apr_demo",
          action: "approve",
          scope: "message",
          changesetId: "cs_demo",
          decidedAt: "2026-06-24T10:00:00.000Z",
          decidedBy: "user"
        },
        now: "2026-06-24T10:00:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalValidationError);

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_demo", approvalId: "apr_demo" },
        body: {
          approvalId: "apr_demo",
          action: "approve",
          scope: "changeset",
          changesetId: "cs_demo",
          decidedAt: "2026-06-24T10:00:00.000Z",
          decidedBy: "user",
          message: "可以"
        },
        now: "2026-06-24T10:00:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalValidationError);
  });

  it("rejects mismatched run or changeset bindings, expired approvals, and repeated decisions", async () => {
    const repository = new InMemoryChangesetApprovalRepository();
    await requestChangesetApproval(repository, {
      approvalId: "apr_demo",
      runId: "run_demo",
      changesetId: "cs_demo",
      requestedEventId: "evt_request_approval",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_other", approvalId: "apr_demo" },
        body: {
          approvalId: "apr_demo",
          action: "approve",
          scope: "changeset",
          changesetId: "cs_demo",
          decidedAt: "2026-06-24T10:00:00.000Z",
          decidedBy: "user"
        },
        now: "2026-06-24T10:00:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalValidationError);

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_demo", approvalId: "apr_demo" },
        body: {
          approvalId: "apr_demo",
          action: "approve",
          scope: "changeset",
          changesetId: "cs_other",
          decidedAt: "2026-06-24T10:00:00.000Z",
          decidedBy: "user"
        },
        now: "2026-06-24T10:00:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalValidationError);

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_demo", approvalId: "apr_demo" },
        body: {
          approvalId: "apr_demo",
          action: "reject",
          scope: "changeset",
          changesetId: "cs_demo",
          decidedAt: "2026-06-24T10:16:00.000Z",
          decidedBy: "user"
        },
        now: "2026-06-24T10:16:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalConflictError);

    await requestChangesetApproval(repository, {
      approvalId: "apr_repeat",
      runId: "run_demo",
      changesetId: "cs_demo",
      requestedEventId: "evt_request_approval_repeat",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });

    await decideChangesetApproval(repository, {
      path: { runId: "run_demo", approvalId: "apr_repeat" },
      body: {
        approvalId: "apr_repeat",
        action: "reject",
        scope: "changeset",
        changesetId: "cs_demo",
        decidedAt: "2026-06-24T10:01:00.000Z",
        decidedBy: "user"
      },
      now: "2026-06-24T10:01:00.000Z"
    });

    await expect(
      decideChangesetApproval(repository, {
        path: { runId: "run_demo", approvalId: "apr_repeat" },
        body: {
          approvalId: "apr_repeat",
          action: "reject",
          scope: "changeset",
          changesetId: "cs_demo",
          decidedAt: "2026-06-24T10:02:00.000Z",
          decidedBy: "user"
        },
        now: "2026-06-24T10:02:00.000Z"
      })
    ).rejects.toThrow(ChangesetApprovalConflictError);
  });
});
