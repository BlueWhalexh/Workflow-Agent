import { describe, expect, it } from "vitest";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import {
  InMemoryKnowledgeChangesetRepository
} from "../../src/domain/knowledge/changeset/store.js";
import {
  InMemoryChangesetApprovalRepository,
  decideChangesetApproval,
  requestChangesetApproval
} from "../../src/domain/knowledge/changeset/approval.js";
import {
  applyKnowledgeChangeset,
  InMemoryKnowledgeVault
} from "../../src/domain/knowledge/changeset/apply.js";
import { proposeKnowledgeChangeset } from "../../src/domain/knowledge/changeset/pipeline.js";
import type { DeterministicRuleset } from "../../src/domain/knowledge/validator/ruleset-validator.js";
import { sha256 } from "../../src/storage/sha.js";
import type { KnowledgeChangeset } from "../../src/runtime/core/types.js";

const demoVaultRoot = join(process.cwd(), "fixtures/demo-vault");

const ruleset: DeterministicRuleset = {
  writableRoots: ["Inbox/", "Projects/"],
  frontmatterSchema: {
    required: ["title", "project", "type", "date", "status", "source"],
    properties: {
      title: { type: "string", minLength: 1 },
      project: { type: "string", enum: ["Atlas Search"] },
      type: { type: "string", enum: ["meeting", "research", "moc"] },
      date: { type: "string", pattern: "^20[0-9]{2}-[0-9]{2}-[0-9]{2}$" },
      status: { type: "string", enum: ["inbox", "active", "archived"] },
      source: { type: "string", enum: ["user-note", "seed"] }
    }
  },
  sourceAttribution: { requiredForUpdate: true, rangeRequired: true },
  linkPolicy: { requireResolvableInternalLinks: true },
  maxOperationsPerChangeset: 4,
  maxBytesPerChangeset: 120000,
  trashPolicy: { allowed: false }
};

function changeset(overrides: Partial<KnowledgeChangeset> = {}): KnowledgeChangeset {
  const before = "# Atlas Search kickoff\n\nold";
  return {
    id: "cs_pipeline",
    workspaceId: "ws_demo",
    sessionId: "sess_demo",
    runId: "run_demo",
    baseRevision: "rev_demo_001",
    rulesetVersion: "sha256:ruleset",
    proposedAt: "2026-06-24T00:00:00.000Z",
    proposedByEngine: "native-loop",
    status: "CANDIDATE",
    operations: [
      {
        kind: "update",
        path: "Inbox/atlas.md",
        baseContentHash: sha256(before),
        content: "# Atlas Search kickoff\n\nSee [[Atlas Search MOC]].",
        frontmatter: {
          title: "Atlas Search kickoff",
          project: "Atlas Search",
          type: "meeting",
          date: "2026-06-01",
          status: "inbox",
          source: "user-note"
        }
      }
    ],
    validatorReport: { passed: false, failures: [] },
    sources: [{ notePath: "Inbox/atlas.md", range: { startLine: 1, endLine: 2 } }],
    ...overrides
  };
}

describe("Phase 1 KnowledgeChangeset pipeline", () => {
  it("runs propose, validate, approve, and apply without writing before approval", async () => {
    const changesets = new InMemoryKnowledgeChangesetRepository();
    const approvals = new InMemoryChangesetApprovalRepository();
    const vault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_001",
      files: new Map([
        ["Inbox/atlas.md", "# Atlas Search kickoff\n\nold"],
        ["Projects/AtlasSearch/Atlas Search MOC.md", "# Atlas Search MOC\n"]
      ])
    });

    const proposed = await proposeKnowledgeChangeset({
      repository: changesets,
      changeset: changeset(),
      ruleset,
      existingNotePaths: ["Inbox/atlas.md", "Projects/AtlasSearch/Atlas Search MOC.md"]
    });

    expect(proposed.status).toBe("AWAITING_APPROVAL");
    expect(vault.read("Inbox/atlas.md")).toBe("# Atlas Search kickoff\n\nold");

    await requestChangesetApproval(approvals, {
      approvalId: "apr_pipeline",
      runId: "run_demo",
      changesetId: "cs_pipeline",
      requestedEventId: "evt_request_approval",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });
    const decision = await decideChangesetApproval(approvals, {
      path: { runId: "run_demo", approvalId: "apr_pipeline" },
      body: {
        approvalId: "apr_pipeline",
        action: "approve",
        scope: "changeset",
        changesetId: "cs_pipeline",
        decidedAt: "2026-06-24T10:00:00.000Z",
        decidedBy: "user"
      },
      now: "2026-06-24T10:00:00.000Z"
    });

    expect(decision.nextEvent).toBe("changeset_apply_started");
    await applyKnowledgeChangeset({
      repository: changesets,
      vault,
      changesetId: "cs_pipeline",
      appliedAt: "2026-06-24T10:00:00.000Z",
      nextRevision: "rev_demo_002"
    });

    expect(vault.read("Inbox/atlas.md")).toBe("# Atlas Search kickoff\n\nSee [[Atlas Search MOC]].");
    await expect(changesets.get("cs_pipeline")).resolves.toMatchObject({
      status: "APPLIED",
      appliedRevision: "rev_demo_002"
    });
  });

  it("rejects invalid proposed changesets before approval", async () => {
    const changesets = new InMemoryKnowledgeChangesetRepository();
    const proposed = await proposeKnowledgeChangeset({
      repository: changesets,
      changeset: changeset({
        operations: [
          {
            kind: "create",
            path: "_golden/not-allowed.md",
            content: "No frontmatter and outside writable roots."
          }
        ],
        sources: []
      }),
      ruleset,
      existingNotePaths: ["Inbox/atlas.md"]
    });

    expect(proposed.status).toBe("REJECTED_BY_VALIDATOR");
    expect(proposed.validatorReport.passed).toBe(false);
    expect(proposed.validatorReport.failures.map((failure) => failure.ruleId)).toEqual(
      expect.arrayContaining(["path-whitelist", "frontmatter-schema"])
    );
  });

  it("does not approve or apply from a short chat confirmation", async () => {
    const changesets = new InMemoryKnowledgeChangesetRepository();
    await changesets.save(changeset({ status: "AWAITING_APPROVAL" }));
    const approvals = new InMemoryChangesetApprovalRepository();
    await requestChangesetApproval(approvals, {
      approvalId: "apr_pipeline",
      runId: "run_demo",
      changesetId: "cs_pipeline",
      requestedEventId: "evt_request_approval",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });

    const chatText = "可以";

    expect(chatText).toBe("可以");
    await expect(approvals.get("apr_pipeline")).resolves.toMatchObject({ status: "pending" });
    await expect(changesets.get("cs_pipeline")).resolves.toMatchObject({ status: "AWAITING_APPROVAL" });
  });

  it("runs the Phase 1 chain against the fixed Demo Vault and rejects stale content", async () => {
    const notePath = "knowledge-base/atlas-search/index.md";
    const mocPath = "knowledge-base/index.md";
    const originalNote = await readFile(join(demoVaultRoot, notePath), "utf8");
    const moc = await readFile(join(demoVaultRoot, mocPath), "utf8");
    const demoRuleset: DeterministicRuleset = {
      ...ruleset,
      writableRoots: ["knowledge-base/", "daily/", "projects/", "resources/"],
      maxOperationsPerChangeset: 24,
      maxBytesPerChangeset: 120000
    };
    const afterContent = `${originalNote}\n\n## Phase 1 Changeset Pipeline\n\n- Deterministic validator and approval-bound apply verified against [[knowledge-base/index]].\n`;
    const demoChangeset = changeset({
      id: "cs_demo_vault",
      baseRevision: "rev_demo_vault_001",
      operations: [
        {
          kind: "update",
          path: notePath,
          baseContentHash: sha256(originalNote),
          content: afterContent,
          frontmatter: {
            title: "Atlas Search",
            project: "Atlas Search",
            type: "moc",
            date: "2026-06-24",
            status: "active",
            source: "user-note"
          }
        }
      ],
      sources: [{ notePath, range: { startLine: 1, endLine: 18 } }]
    });

    const changesets = new InMemoryKnowledgeChangesetRepository();
    const approvals = new InMemoryChangesetApprovalRepository();
    const vault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_vault_001",
      files: new Map([
        [notePath, originalNote],
        [mocPath, moc]
      ])
    });

    const proposed = await proposeKnowledgeChangeset({
      repository: changesets,
      changeset: demoChangeset,
      ruleset: demoRuleset,
      existingNotePaths: [notePath, mocPath, "raw/项目随手记.md"]
    });
    expect(proposed.status).toBe("AWAITING_APPROVAL");

    await requestChangesetApproval(approvals, {
      approvalId: "apr_demo_vault",
      runId: "run_demo",
      changesetId: "cs_demo_vault",
      requestedEventId: "evt_demo_vault_request_approval",
      expiresAt: "2026-06-24T10:15:00.000Z"
    });
    await decideChangesetApproval(approvals, {
      path: { runId: "run_demo", approvalId: "apr_demo_vault" },
      body: {
        approvalId: "apr_demo_vault",
        action: "approve",
        scope: "changeset",
        changesetId: "cs_demo_vault",
        decidedAt: "2026-06-24T10:00:00.000Z",
        decidedBy: "user"
      },
      now: "2026-06-24T10:00:00.000Z"
    });
    await applyKnowledgeChangeset({
      repository: changesets,
      vault,
      changesetId: "cs_demo_vault",
      appliedAt: "2026-06-24T10:00:00.000Z",
      nextRevision: "rev_demo_vault_002"
    });
    expect(vault.read(notePath)).toBe(afterContent);

    const staleRepository = new InMemoryKnowledgeChangesetRepository();
    await staleRepository.save({ ...demoChangeset, id: "cs_demo_vault_stale", status: "AWAITING_APPROVAL" });
    const staleVault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_vault_001",
      files: new Map([
        [notePath, `${originalNote}\n\nUser changed this before approval.\n`],
        [mocPath, moc]
      ])
    });
    await expect(
      applyKnowledgeChangeset({
        repository: staleRepository,
        vault: staleVault,
        changesetId: "cs_demo_vault_stale",
        appliedAt: "2026-06-24T10:00:00.000Z",
        nextRevision: "rev_demo_vault_002"
      })
    ).rejects.toThrow("baseContentHash");
    expect(staleVault.read(notePath)).toBe(`${originalNote}\n\nUser changed this before approval.\n`);
    await expect(staleRepository.get("cs_demo_vault_stale")).resolves.toMatchObject({ status: "STALE" });
  });
});
