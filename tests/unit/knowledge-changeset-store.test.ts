import { describe, expect, it } from "vitest";
import {
  assertChangesetApplyBaseIsCurrent,
  InMemoryKnowledgeChangesetRepository,
  KnowledgeChangesetStaleError,
  KnowledgeChangesetValidationError
} from "../../src/domain/knowledge/changeset/store.js";
import type { KnowledgeChangeset } from "../../src/runtime/core/types.js";

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
        content: "---\ntitle: Atlas Search\n---\n"
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

describe("KnowledgeChangeset repository", () => {
  it("rejects changesets without a baseRevision", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    const invalid = changeset({ baseRevision: "" });

    await expect(repository.save(invalid)).rejects.toThrow(KnowledgeChangesetValidationError);
  });

  it("requires baseContentHash for mutation operations that read existing notes", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    const invalid = changeset({
      operations: [
        {
          kind: "update",
          path: "Inbox/2026-06-01-atlas-search-kickoff.md",
          baseContentHash: "",
          content: "---\ntitle: Atlas Search\n---\n"
        }
      ]
    });

    await expect(repository.save(invalid)).rejects.toThrow(KnowledgeChangesetValidationError);
  });

  it("stores changesets and reports stale apply when content hashes no longer match", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    const saved = await repository.save(changeset());

    expect(await repository.get(saved.id)).toEqual(saved);
    expect(() =>
      assertChangesetApplyBaseIsCurrent(saved, {
        currentRevision: "rev_demo_001",
        currentContentHashes: new Map([["Inbox/2026-06-01-atlas-search-kickoff.md", "hash_after"]])
      })
    ).toThrow(KnowledgeChangesetStaleError);
  });
});
