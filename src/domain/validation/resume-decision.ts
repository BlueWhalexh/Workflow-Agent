import type { WorkItemStatus } from "../planning/work-item.js";

export type ResumeAction = "SKIP" | "RETRY" | "WAIT_FOR_APPROVAL" | "REPLAN" | "REPORT_FAILED";

export function decideResumeAction(input: {
  status: WorkItemStatus;
  currentSha?: string;
  contentSha?: string;
  retryable?: boolean;
}): ResumeAction {
  if (input.status === "PUBLISHED") {
    return input.currentSha && input.contentSha && input.currentSha === input.contentSha ? "SKIP" : "REPLAN";
  }
  if (input.status === "SUCCEEDED") {
    return "RETRY";
  }
  if (input.status === "FAILED_TIMEOUT") {
    return "RETRY";
  }
  if (input.status === "FAILED_EXECUTOR") {
    return input.retryable ? "RETRY" : "REPORT_FAILED";
  }
  if (input.status === "BLOCKED_BY_VALIDATOR") {
    return input.retryable === false ? "REPORT_FAILED" : "RETRY";
  }
  if (input.status === "WAITING_APPROVAL") {
    return "WAIT_FOR_APPROVAL";
  }
  if (input.status === "NEEDS_REPLAN") {
    return "REPLAN";
  }
  return "RETRY";
}
