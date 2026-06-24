import { describe, expect, it } from "vitest";
import {
  applyKnowledgeChangeset,
  calculateStaleRejectionRate,
  InMemoryKnowledgeVault
} from "../../src/domain/knowledge/changeset/apply.js";
import {
  InMemoryKnowledgeChangesetRepository,
  KnowledgeChangesetStaleError
} from "../../src/domain/knowledge/changeset/store.js";
import { sha256 } from "../../src/storage/sha.js";
import type { KnowledgeChangeset } from "../../src/runtime/core/types.js";

function changeset(overrides: Partial<KnowledgeChangeset> = {}): KnowledgeChangeset {
  return {
    id: "cs_apply",
    workspaceId: "ws_demo",
    sessionId: "sess_demo",
    runId: "run_demo",
    baseRevision: "rev_demo_001",
    rulesetVersion: "sha256:ruleset",
    proposedAt: "2026-06-24T00:00:00.000Z",
    proposedByEngine: "native-loop",
    status: "AWAITING_APPROVAL",
    operations: [
      {
        kind: "update",
        path: "Inbox/source.md",
        baseContentHash: sha256("# Source\n\nold"),
        content: "# Source\n\nnew"
      },
      {
        kind: "create",
        path: "Projects/AtlasSearch/new.md",
        content: "# New\n"
      },
      {
        kind: "rename",
        path: "Inbox/rename-me.md",
        baseContentHash: sha256("# Rename me\n"),
        targetPath: "Projects/AtlasSearch/renamed.md"
      }
    ],
    validatorReport: { passed: true, failures: [] },
    sources: [{ notePath: "Inbox/source.md", range: { startLine: 1, endLine: 2 } }],
    ...overrides
  };
}

describe("revision-aware KnowledgeChangeset apply pipeline", () => {
  it("applies an approved changeset atomically and records the applied revision", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    await repository.save(changeset());
    const vault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_001",
      files: new Map([
        ["Inbox/source.md", "# Source\n\nold"],
        ["Inbox/rename-me.md", "# Rename me\n"]
      ])
    });

    const result = await applyKnowledgeChangeset({
      repository,
      vault,
      changesetId: "cs_apply",
      appliedAt: "2026-06-24T10:00:00.000Z",
      nextRevision: "rev_demo_002"
    });

    expect(result).toEqual({
      changesetId: "cs_apply",
      appliedAt: "2026-06-24T10:00:00.000Z",
      appliedRevision: "rev_demo_002",
      changedPaths: [
        "Inbox/source.md",
        "Projects/AtlasSearch/new.md",
        "Inbox/rename-me.md",
        "Projects/AtlasSearch/renamed.md"
      ]
    });
    expect(vault.read("Inbox/source.md")).toBe("# Source\n\nnew");
    expect(vault.read("Projects/AtlasSearch/new.md")).toBe("# New\n");
    expect(vault.read("Inbox/rename-me.md")).toBeNull();
    expect(vault.read("Projects/AtlasSearch/renamed.md")).toBe("# Rename me\n");
    await expect(repository.get("cs_apply")).resolves.toMatchObject({
      status: "APPLIED",
      appliedAt: "2026-06-24T10:00:00.000Z",
      appliedRevision: "rev_demo_002"
    });
  });

  it("marks stale and leaves the vault unchanged when workspace revision changed", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    await repository.save(changeset());
    const vault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_002",
      files: new Map([
        ["Inbox/source.md", "# Source\n\nold"],
        ["Inbox/rename-me.md", "# Rename me\n"]
      ])
    });

    await expect(
      applyKnowledgeChangeset({
        repository,
        vault,
        changesetId: "cs_apply",
        appliedAt: "2026-06-24T10:00:00.000Z",
        nextRevision: "rev_demo_003"
      })
    ).rejects.toThrow(KnowledgeChangesetStaleError);

    expect(vault.read("Inbox/source.md")).toBe("# Source\n\nold");
    expect(vault.read("Projects/AtlasSearch/new.md")).toBeNull();
    await expect(repository.get("cs_apply")).resolves.toMatchObject({ status: "STALE" });
  });

  it("rejects the whole batch when any baseContentHash no longer matches", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    await repository.save(changeset());
    const vault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_001",
      files: new Map([
        ["Inbox/source.md", "# Source\n\nchanged by user"],
        ["Inbox/rename-me.md", "# Rename me\n"]
      ])
    });

    await expect(
      applyKnowledgeChangeset({
        repository,
        vault,
        changesetId: "cs_apply",
        appliedAt: "2026-06-24T10:00:00.000Z",
        nextRevision: "rev_demo_002"
      })
    ).rejects.toThrow(KnowledgeChangesetStaleError);

    expect(vault.read("Inbox/source.md")).toBe("# Source\n\nchanged by user");
    expect(vault.read("Projects/AtlasSearch/new.md")).toBeNull();
    expect(vault.read("Inbox/rename-me.md")).toBe("# Rename me\n");
    await expect(repository.get("cs_apply")).resolves.toMatchObject({ status: "STALE" });
  });

  it("rejects a concurrent apply when another changeset commits the same base revision first", async () => {
    const repository = new InMemoryKnowledgeChangesetRepository();
    await repository.save(changeset({
      id: "cs_first",
      operations: [
        {
          kind: "update",
          path: "Inbox/source.md",
          baseContentHash: sha256("# Source\n\nold"),
          content: "# Source\n\nfirst"
        }
      ]
    }));
    await repository.save(changeset({
      id: "cs_second",
      operations: [
        {
          kind: "create",
          path: "Projects/AtlasSearch/second.md",
          content: "# Second\n"
        }
      ]
    }));
    const vault = new InMemoryKnowledgeVault({
      workspaceId: "ws_demo",
      revision: "rev_demo_001",
      files: new Map([["Inbox/source.md", "# Source\n\nold"]])
    });

    const results = await Promise.allSettled([
      applyKnowledgeChangeset({
        repository,
        vault,
        changesetId: "cs_first",
        appliedAt: "2026-06-24T10:00:00.000Z",
        nextRevision: "rev_demo_002"
      }),
      applyKnowledgeChangeset({
        repository,
        vault,
        changesetId: "cs_second",
        appliedAt: "2026-06-24T10:00:01.000Z",
        nextRevision: "rev_demo_002"
      })
    ]);

    expect(results).toEqual([
      expect.objectContaining({ status: "fulfilled" }),
      expect.objectContaining({ status: "rejected", reason: expect.any(KnowledgeChangesetStaleError) })
    ]);
    expect(vault.read("Inbox/source.md")).toBe("# Source\n\nfirst");
    expect(vault.read("Projects/AtlasSearch/second.md")).toBeNull();
    await expect(repository.get("cs_first")).resolves.toMatchObject({ status: "APPLIED" });
    await expect(repository.get("cs_second")).resolves.toMatchObject({ status: "STALE" });
  });

  it("reports stale rejection rate for eval output", () => {
    expect(calculateStaleRejectionRate(["applied", "stale", "stale", "applied"])).toBe(0.5);
    expect(calculateStaleRejectionRate([])).toBe(0);
  });
});
