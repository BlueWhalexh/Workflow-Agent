export type WorkItemType =
  | "CREATE_TOPIC_NOTE"
  | "REWRITE_TOPIC_NOTE"
  | "MERGE_USER_EDITED_NOTE"
  | "MAINTAIN_TOPIC_INDEX"
  | "MAINTAIN_MOC"
  | "QUALITY_REVIEW";

export type WorkItemStatus =
  | "PLANNED"
  | "SKIPPED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED_TIMEOUT"
  | "FAILED_EXECUTOR"
  | "BLOCKED_BY_VALIDATOR"
  | "WAITING_APPROVAL"
  | "PUBLISHED"
  | "NEEDS_REPLAN";

export type WorkItemFailureSource = "provider" | "loop" | "validator" | "merge" | "context";

export interface WorkItemAttempt {
  attempt: number;
  status: WorkItemStatus;
  message: string;
  failureSource?: WorkItemFailureSource;
  failureReason?: string;
  retryable?: boolean;
}

export interface WorkItem {
  id: string;
  type: WorkItemType;
  methodologyId?: string;
  phase: "phase-a-notes" | "phase-b-indexes" | "phase-c-global";
  status: WorkItemStatus;
  sourcePaths: string[];
  targetPaths: string[];
  baseShas: Record<string, string>;
  risk: "LOW" | "MEDIUM" | "HIGH";
  requiresApproval: boolean;
  reason: string;
  attempts: WorkItemAttempt[];
  publishPolicy?: "AUTO_PUBLISH" | "CANDIDATE_PATCH_ONLY";
}
