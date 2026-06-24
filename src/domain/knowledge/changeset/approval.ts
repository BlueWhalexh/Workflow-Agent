export type ChangesetApprovalAction = "approve" | "reject";
export type ChangesetApprovalScope = "changeset";
export type ChangesetApprovalStatus = "pending" | "consumed" | "expired";

export interface ChangesetApprovalRecord {
  approvalId: string;
  runId: string;
  changesetId: string;
  scope: ChangesetApprovalScope;
  status: ChangesetApprovalStatus;
  requestedEventId: string;
  expiresAt: string;
  decision?: ChangesetApprovalAction;
  decidedAt?: string;
  decidedBy?: "user";
}

export interface RequestChangesetApprovalInput {
  approvalId: string;
  runId: string;
  changesetId: string;
  requestedEventId: string;
  expiresAt: string;
}

export interface DecideChangesetApprovalInput {
  path: {
    runId: string;
    approvalId: string;
  };
  body: unknown;
  now: string;
}

export interface ChangesetApprovalDecisionRequest {
  approvalId: string;
  action: ChangesetApprovalAction;
  scope: ChangesetApprovalScope;
  changesetId: string;
  decidedAt: string;
  decidedBy: "user";
}

export interface ChangesetApprovalDecisionRecord {
  approvalId: string;
  action: ChangesetApprovalAction;
  scope: ChangesetApprovalScope;
  changesetId: string;
  expiresAt: string;
  appliedAt?: string;
  nextEvent: "changeset_apply_started" | "changeset_rejected_by_user";
}

export interface ChangesetApprovalRepository {
  save(record: ChangesetApprovalRecord): Promise<ChangesetApprovalRecord>;
  get(approvalId: string): Promise<ChangesetApprovalRecord | null>;
}

export class ChangesetApprovalValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ChangesetApprovalValidationError";
  }
}

export class ChangesetApprovalConflictError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ChangesetApprovalConflictError";
  }
}

export class InMemoryChangesetApprovalRepository implements ChangesetApprovalRepository {
  private readonly records = new Map<string, ChangesetApprovalRecord>();

  async save(record: ChangesetApprovalRecord): Promise<ChangesetApprovalRecord> {
    const snapshot = structuredClone(record);
    this.records.set(snapshot.approvalId, snapshot);
    return structuredClone(snapshot);
  }

  async get(approvalId: string): Promise<ChangesetApprovalRecord | null> {
    const record = this.records.get(approvalId);
    return record ? structuredClone(record) : null;
  }
}

export async function requestChangesetApproval(
  repository: ChangesetApprovalRepository,
  input: RequestChangesetApprovalInput
): Promise<ChangesetApprovalRecord> {
  for (const [field, value] of Object.entries(input)) {
    if (typeof value !== "string" || !value.trim()) {
      throw new ChangesetApprovalValidationError(`${field} is required`);
    }
  }

  return repository.save({
    approvalId: input.approvalId,
    runId: input.runId,
    changesetId: input.changesetId,
    scope: "changeset",
    status: "pending",
    requestedEventId: input.requestedEventId,
    expiresAt: input.expiresAt
  });
}

export async function decideChangesetApproval(
  repository: ChangesetApprovalRepository,
  input: DecideChangesetApprovalInput
): Promise<ChangesetApprovalDecisionRecord> {
  const request = parseDecisionRequest(input.body);

  if (input.path.approvalId !== request.approvalId) {
    throw new ChangesetApprovalValidationError("approvalId in path and body must match");
  }
  if (input.path.runId === "" || !input.path.runId.trim()) {
    throw new ChangesetApprovalValidationError("runId path parameter is required");
  }

  const approval = await repository.get(input.path.approvalId);
  if (!approval) {
    throw new ChangesetApprovalValidationError(`Approval ${input.path.approvalId} was not found`);
  }
  if (approval.runId !== input.path.runId) {
    throw new ChangesetApprovalValidationError("approval is not bound to this runId");
  }
  if (approval.changesetId !== request.changesetId) {
    throw new ChangesetApprovalValidationError("approval is not bound to this changesetId");
  }
  if (!approval.requestedEventId.trim()) {
    throw new ChangesetApprovalValidationError("approval is missing request_approval event binding");
  }
  if (approval.status !== "pending") {
    throw new ChangesetApprovalConflictError("approval is not pending");
  }
  if (Date.parse(input.now) > Date.parse(approval.expiresAt)) {
    await repository.save({ ...approval, status: "expired" });
    throw new ChangesetApprovalConflictError("approval has expired");
  }

  await repository.save({
    ...approval,
    status: "consumed",
    decision: request.action,
    decidedAt: request.decidedAt,
    decidedBy: request.decidedBy
  });

  return {
    approvalId: approval.approvalId,
    action: request.action,
    scope: "changeset",
    changesetId: approval.changesetId,
    expiresAt: approval.expiresAt,
    ...(request.action === "approve" ? { appliedAt: request.decidedAt } : {}),
    nextEvent: request.action === "approve" ? "changeset_apply_started" : "changeset_rejected_by_user"
  };
}

function parseDecisionRequest(body: unknown): ChangesetApprovalDecisionRequest {
  if (!isRecord(body)) {
    throw new ChangesetApprovalValidationError("approval decision body must be an object");
  }

  const allowedFields = new Set(["approvalId", "action", "scope", "changesetId", "decidedAt", "decidedBy"]);
  for (const field of Object.keys(body)) {
    if (!allowedFields.has(field)) {
      throw new ChangesetApprovalValidationError(`field ${field} is not allowed in approval decision body`);
    }
  }

  const approvalId = requiredString(body.approvalId, "approvalId");
  const action = parseAction(body.action);
  const scope = parseScope(body.scope);
  const changesetId = requiredString(body.changesetId, "changesetId");
  const decidedAt = requiredString(body.decidedAt, "decidedAt");
  const decidedBy = parseDecidedBy(body.decidedBy);

  if (Number.isNaN(Date.parse(decidedAt))) {
    throw new ChangesetApprovalValidationError("decidedAt must be an ISO timestamp");
  }

  return {
    approvalId,
    action,
    scope,
    changesetId,
    decidedAt,
    decidedBy
  };
}

function parseAction(value: unknown): ChangesetApprovalAction {
  if (value === "approve" || value === "reject") {
    return value;
  }
  throw new ChangesetApprovalValidationError("action must be approve or reject");
}

function parseScope(value: unknown): ChangesetApprovalScope {
  if (value === "changeset") {
    return value;
  }
  throw new ChangesetApprovalValidationError("scope must be changeset");
}

function parseDecidedBy(value: unknown): "user" {
  if (value === "user") {
    return value;
  }
  throw new ChangesetApprovalValidationError("decidedBy must be user");
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new ChangesetApprovalValidationError(`${field} is required`);
  }
  return value.trim();
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
