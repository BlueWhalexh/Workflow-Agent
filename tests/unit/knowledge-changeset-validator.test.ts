import { describe, expect, it } from "vitest";
import {
  validateKnowledgeChangeset,
  type DeterministicRuleset
} from "../../src/domain/knowledge/validator/ruleset-validator.js";
import type { ChangesetOperation, KnowledgeChangeset } from "../../src/runtime/core/types.js";

const ruleset: DeterministicRuleset = {
  writableRoots: ["Inbox/", "Projects/"],
  frontmatterSchema: {
    required: ["title", "project", "type", "date", "status", "source"],
    properties: {
      title: { type: "string", minLength: 1 },
      project: { type: "string", enum: ["Atlas Search", "Beacon Ops"] },
      type: { type: "string", enum: ["meeting", "research", "moc"] },
      date: { type: "string", pattern: "^20[0-9]{2}-[0-9]{2}-[0-9]{2}$" },
      status: { type: "string", enum: ["inbox", "active", "archived"] },
      source: { type: "string", enum: ["user-note", "seed"] }
    }
  },
  sourceAttribution: { requiredForUpdate: true, rangeRequired: true },
  linkPolicy: { requireResolvableInternalLinks: true },
  maxOperationsPerChangeset: 4,
  maxBytesPerChangeset: 200,
  trashPolicy: { allowed: false }
};

function changeset(overrides: Partial<KnowledgeChangeset> = {}): KnowledgeChangeset {
  return {
    id: "cs_demo",
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
        path: "Inbox/2026-06-01-atlas-search-kickoff.md",
        baseContentHash: "hash_before",
        content: "See [[Atlas Search MOC]].",
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
    validatorReport: { passed: true, failures: [] },
    sources: [
      {
        notePath: "Inbox/2026-06-01-atlas-search-kickoff.md",
        range: { startLine: 1, endLine: 8 }
      }
    ],
    ...overrides
  };
}

describe("deterministic KnowledgeChangeset validator", () => {
  it("accepts a changeset that satisfies path, frontmatter, link, source, budget, and rename rules", () => {
    const report = validateKnowledgeChangeset(changeset(), {
      ruleset,
      existingNotePaths: [
        "Inbox/2026-06-01-atlas-search-kickoff.md",
        "Projects/AtlasSearch/Atlas Search MOC.md"
      ]
    });

    expect(report).toEqual({ passed: true, failures: [] });
  });

  it("reports deterministic failures for every Phase 1 ruleset category", () => {
    const invalidOperations: ChangesetOperation[] = [
      {
        kind: "create",
        path: "_golden/generated.md",
        content: "Unresolved link to [[Missing MOC]].",
        frontmatter: {
          title: "",
          project: "Unknown Project",
          type: "note",
          date: "not-a-date",
          status: "inbox",
          source: "user-note"
        }
      },
      {
        kind: "update",
        path: "Inbox/2026-06-01-atlas-search-kickoff.md",
        baseContentHash: "hash_before",
        content: "x".repeat(210),
        frontmatter: {
          title: "Atlas Search kickoff",
          project: "Atlas Search",
          type: "meeting",
          date: "2026-06-01",
          status: "inbox",
          source: "user-note"
        }
      },
      {
        kind: "trash",
        path: "Inbox/2026-06-02-beacon-ops-incident-review.md",
        baseContentHash: "hash_trash",
        reason: "duplicate"
      },
      {
        kind: "rename",
        path: "Inbox/2026-06-03-cobalt-notes-frontmatter-study.md",
        baseContentHash: "hash_rename",
        targetPath: "Projects/AtlasSearch/Atlas Search MOC.md"
      },
      {
        kind: "create",
        path: "Inbox/overflow.md",
        content: "overflow",
        frontmatter: {
          title: "Overflow",
          project: "Atlas Search",
          type: "research",
          date: "2026-06-04",
          status: "inbox",
          source: "seed"
        }
      }
    ];

    const report = validateKnowledgeChangeset(changeset({ operations: invalidOperations, sources: [] }), {
      ruleset,
      existingNotePaths: [
        "Inbox/2026-06-01-atlas-search-kickoff.md",
        "Inbox/2026-06-02-beacon-ops-incident-review.md",
        "Inbox/2026-06-03-cobalt-notes-frontmatter-study.md",
        "Projects/AtlasSearch/Atlas Search MOC.md"
      ]
    });

    expect(report.passed).toBe(false);
    expect(report.failures.map((failure) => failure.ruleId)).toEqual(
      expect.arrayContaining([
        "path-whitelist",
        "frontmatter-schema",
        "link-integrity",
        "source-attribution",
        "budget-limit",
        "trash-policy",
        "rename-conflict"
      ])
    );
  });
});
