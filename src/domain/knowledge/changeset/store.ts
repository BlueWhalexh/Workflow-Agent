import type {
  ChangesetOperation,
  ContentHash,
  KnowledgeChangeset,
  NotePath,
  WorkspaceRevisionId
} from "../../../runtime/core/types.js";

export class KnowledgeChangesetValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "KnowledgeChangesetValidationError";
  }
}

export class KnowledgeChangesetStaleError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "KnowledgeChangesetStaleError";
  }
}

export interface KnowledgeChangesetRepository {
  save(changeset: KnowledgeChangeset): Promise<KnowledgeChangeset>;
  get(changesetId: string): Promise<KnowledgeChangeset | null>;
  listByRun(runId: string): Promise<KnowledgeChangeset[]>;
}

export interface ChangesetApplyBaseSnapshot {
  currentRevision: WorkspaceRevisionId;
  currentContentHashes: ReadonlyMap<NotePath, ContentHash>;
}

export class InMemoryKnowledgeChangesetRepository implements KnowledgeChangesetRepository {
  private readonly records = new Map<string, KnowledgeChangeset>();

  async save(changeset: KnowledgeChangeset): Promise<KnowledgeChangeset> {
    assertKnowledgeChangesetIsStorable(changeset);
    const snapshot = cloneChangeset(changeset);
    this.records.set(snapshot.id, snapshot);
    return cloneChangeset(snapshot);
  }

  async get(changesetId: string): Promise<KnowledgeChangeset | null> {
    const record = this.records.get(changesetId);
    return record ? cloneChangeset(record) : null;
  }

  async listByRun(runId: string): Promise<KnowledgeChangeset[]> {
    return Array.from(this.records.values())
      .filter((record) => record.runId === runId)
      .map((record) => cloneChangeset(record));
  }
}

export function assertKnowledgeChangesetIsStorable(changeset: KnowledgeChangeset): void {
  if (!changeset.baseRevision?.trim()) {
    throw new KnowledgeChangesetValidationError("KnowledgeChangeset.baseRevision is required");
  }

  changeset.operations.forEach((operation, index) => {
    if (operationRequiresBaseContentHash(operation) && !operation.baseContentHash?.trim()) {
      throw new KnowledgeChangesetValidationError(
        `KnowledgeChangeset.operations[${index}].baseContentHash is required for ${operation.kind}`
      );
    }
  });
}

export function assertChangesetApplyBaseIsCurrent(
  changeset: KnowledgeChangeset,
  snapshot: ChangesetApplyBaseSnapshot
): void {
  if (changeset.baseRevision !== snapshot.currentRevision) {
    throw new KnowledgeChangesetStaleError(
      `Changeset ${changeset.id} is stale: baseRevision ${changeset.baseRevision} does not match current revision ${snapshot.currentRevision}`
    );
  }

  changeset.operations.forEach((operation, index) => {
    if (!operationRequiresBaseContentHash(operation)) {
      return;
    }

    const currentHash = snapshot.currentContentHashes.get(operation.path);
    if (currentHash !== operation.baseContentHash) {
      throw new KnowledgeChangesetStaleError(
        `Changeset ${changeset.id} is stale: operation ${index} baseContentHash for ${operation.path} no longer matches current content`
      );
    }
  });
}

function operationRequiresBaseContentHash(
  operation: ChangesetOperation
): operation is Extract<ChangesetOperation, { baseContentHash: ContentHash }> {
  return operation.kind === "update" || operation.kind === "rename" || operation.kind === "trash";
}

function cloneChangeset(changeset: KnowledgeChangeset): KnowledgeChangeset {
  return structuredClone(changeset);
}
