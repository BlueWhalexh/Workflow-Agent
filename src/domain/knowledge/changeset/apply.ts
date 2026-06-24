import { sha256 } from "../../../storage/sha.js";
import type {
  ChangesetOperation,
  ContentHash,
  KnowledgeChangeset,
  NotePath,
  RevisionId,
  WorkspaceRevisionId
} from "../../../runtime/core/types.js";
import {
  assertChangesetApplyBaseIsCurrent,
  KnowledgeChangesetStaleError,
  KnowledgeChangesetValidationError,
  type KnowledgeChangesetRepository
} from "./store.js";

export interface KnowledgeVaultSnapshot {
  workspaceId: string;
  currentRevision: WorkspaceRevisionId;
  currentContentHashes: ReadonlyMap<NotePath, ContentHash>;
}

export interface KnowledgeVaultApplyCommit {
  expectedBaseRevision: WorkspaceRevisionId;
  nextRevision: RevisionId;
  files: ReadonlyMap<NotePath, string>;
}

export interface KnowledgeVaultApplyPort {
  snapshot(): KnowledgeVaultSnapshot;
  commit(commit: KnowledgeVaultApplyCommit): void;
}

export interface ApplyKnowledgeChangesetInput {
  repository: KnowledgeChangesetRepository;
  vault: KnowledgeVaultApplyPort;
  changesetId: string;
  appliedAt: string;
  nextRevision: RevisionId;
}

export interface ApplyKnowledgeChangesetResult {
  changesetId: string;
  appliedAt: string;
  appliedRevision: RevisionId;
  changedPaths: NotePath[];
}

export type KnowledgeChangesetApplyOutcome = "applied" | "stale";

export class InMemoryKnowledgeVault implements KnowledgeVaultApplyPort {
  private readonly workspaceId: string;
  private revision: WorkspaceRevisionId;
  private files: Map<NotePath, string>;

  constructor(input: { workspaceId: string; revision: WorkspaceRevisionId; files: ReadonlyMap<NotePath, string> }) {
    this.workspaceId = input.workspaceId;
    this.revision = input.revision;
    this.files = new Map(input.files);
  }

  snapshot(): KnowledgeVaultSnapshot {
    return {
      workspaceId: this.workspaceId,
      currentRevision: this.revision,
      currentContentHashes: new Map(
        Array.from(this.files.entries()).map(([path, content]) => [path, sha256(content)])
      )
    };
  }

  commit(commit: KnowledgeVaultApplyCommit): void {
    if (this.revision !== commit.expectedBaseRevision) {
      throw new KnowledgeChangesetStaleError(
        `Workspace revision changed from ${commit.expectedBaseRevision} to ${this.revision}`
      );
    }
    this.files = new Map(commit.files);
    this.revision = commit.nextRevision;
  }

  read(path: NotePath): string | null {
    return this.files.get(path) ?? null;
  }
}

export async function applyKnowledgeChangeset(
  input: ApplyKnowledgeChangesetInput
): Promise<ApplyKnowledgeChangesetResult> {
  const changeset = await input.repository.get(input.changesetId);
  if (!changeset) {
    throw new KnowledgeChangesetValidationError(`KnowledgeChangeset ${input.changesetId} was not found`);
  }
  if (changeset.status !== "AWAITING_APPROVAL") {
    throw new KnowledgeChangesetValidationError(`KnowledgeChangeset ${input.changesetId} is not awaiting approval`);
  }

  try {
    const snapshot = input.vault.snapshot();
    assertChangesetApplyBaseIsCurrent(changeset, snapshot);

    const stagedFiles = await stageOperations(input.vault, changeset.operations);
    input.vault.commit({
      expectedBaseRevision: changeset.baseRevision,
      nextRevision: input.nextRevision,
      files: stagedFiles
    });
  } catch (error) {
    if (error instanceof KnowledgeChangesetStaleError) {
      await input.repository.save({ ...changeset, status: "STALE" });
    }
    throw error;
  }

  await input.repository.save({
    ...changeset,
    status: "APPLIED",
    appliedAt: input.appliedAt,
    appliedRevision: input.nextRevision
  });

  return {
    changesetId: changeset.id,
    appliedAt: input.appliedAt,
    appliedRevision: input.nextRevision,
    changedPaths: changedPaths(changeset.operations)
  };
}

export function calculateStaleRejectionRate(outcomes: readonly KnowledgeChangesetApplyOutcome[]): number {
  if (outcomes.length === 0) {
    return 0;
  }
  return outcomes.filter((outcome) => outcome === "stale").length / outcomes.length;
}

async function stageOperations(
  vault: KnowledgeVaultApplyPort,
  operations: readonly ChangesetOperation[]
): Promise<Map<NotePath, string>> {
  const readable = vault as KnowledgeVaultApplyPort & { read?: (path: NotePath) => string | null };
  if (typeof readable.read !== "function") {
    throw new KnowledgeChangesetValidationError("KnowledgeVaultApplyPort must expose read() for staged apply");
  }

  const files = new Map<NotePath, string>();
  for (const [path] of vault.snapshot().currentContentHashes) {
    const content = readable.read(path);
    if (content !== null) {
      files.set(path, content);
    }
  }

  for (const operation of operations) {
    applyOperation(files, operation);
  }
  return files;
}

function applyOperation(files: Map<NotePath, string>, operation: ChangesetOperation): void {
  if (operation.kind === "create") {
    if (files.has(operation.path)) {
      throw new KnowledgeChangesetValidationError(`Cannot create existing note ${operation.path}`);
    }
    files.set(operation.path, operation.content);
    return;
  }

  if (operation.kind === "update") {
    if (!files.has(operation.path)) {
      throw new KnowledgeChangesetValidationError(`Cannot update missing note ${operation.path}`);
    }
    files.set(operation.path, operation.content);
    return;
  }

  if (operation.kind === "rename") {
    const content = files.get(operation.path);
    if (content === undefined) {
      throw new KnowledgeChangesetValidationError(`Cannot rename missing note ${operation.path}`);
    }
    if (files.has(operation.targetPath)) {
      throw new KnowledgeChangesetValidationError(`Cannot rename to existing note ${operation.targetPath}`);
    }
    files.delete(operation.path);
    files.set(operation.targetPath, content);
    return;
  }

  if (!files.has(operation.path)) {
    throw new KnowledgeChangesetValidationError(`Cannot trash missing note ${operation.path}`);
  }
  files.delete(operation.path);
}

function changedPaths(operations: readonly ChangesetOperation[]): NotePath[] {
  return operations.flatMap((operation) => {
    if (operation.kind === "rename") {
      return [operation.path, operation.targetPath];
    }
    return [operation.path];
  });
}
