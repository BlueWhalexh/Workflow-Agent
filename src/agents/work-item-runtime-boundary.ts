import type { WorkItem, WorkItemAttempt, WorkItemStatus } from "../domain/planning/work-item.js";
import {
  isWorkItemAgentLoopReport,
  loopBudgetExceeded,
  type WorkItemAgentLoopReport
} from "./work-item-agent-runtime.js";

export interface WorkItemRuntime<I = unknown, O = unknown> {
  type: WorkItem["type"];
  buildContext(input: WorkItemRuntimeInput): Promise<I>;
  runLoop(input: WorkItemLoopInput<I>): Promise<WorkItemLoopResult<O>>;
}

export interface WorkItemRuntimeInput {
  runId: string;
  workspaceRoot: string;
  workItem: WorkItem;
}

export interface WorkItemLoopInput<I> extends WorkItemRuntimeInput {
  context: I;
}

export interface WorkItemLoopResult<O> {
  output: O;
  report: WorkItemAgentLoopReport | unknown;
}

export interface WorkItemRuntimeBoundaryInput<I = unknown, O = unknown> extends WorkItemRuntimeInput {
  runtime: WorkItemRuntime<I, O>;
}

export interface WorkItemRuntimeBoundaryResult<O = unknown> {
  status: WorkItemStatus;
  output: O | null;
  report: WorkItemAgentLoopReport | null;
  latestAttempt: WorkItemAttempt;
  publishableArtifactWritten: boolean;
}

function failureAttempt(workItem: WorkItem, reason: string): WorkItemAttempt {
  return {
    attempt: workItem.attempts.length + 1,
    status: "FAILED_EXECUTOR",
    message: reason,
    failureSource: "loop",
    failureReason: reason,
    retryable: false
  };
}

function outputIsPublishable(value: unknown): boolean {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export async function runWorkItemRuntimeBoundary<I, O>(
  input: WorkItemRuntimeBoundaryInput<I, O>
): Promise<WorkItemRuntimeBoundaryResult<O>> {
  const context = await input.runtime.buildContext(input);
  const result = await input.runtime.runLoop({ ...input, context });

  if (!isWorkItemAgentLoopReport(result.report) || !outputIsPublishable(result.output)) {
    return {
      status: "FAILED_EXECUTOR",
      output: null,
      report: isWorkItemAgentLoopReport(result.report) ? result.report : null,
      latestAttempt: failureAttempt(input.workItem, "LOOP_OUTPUT_SCHEMA_INVALID"),
      publishableArtifactWritten: false
    };
  }

  if (loopBudgetExceeded(result.report)) {
    return {
      status: "FAILED_EXECUTOR",
      output: null,
      report: result.report,
      latestAttempt: failureAttempt(input.workItem, "LOOP_BUDGET_EXCEEDED"),
      publishableArtifactWritten: false
    };
  }

  return {
    status: "SUCCEEDED",
    output: result.output,
    report: result.report,
    latestAttempt: {
      attempt: input.workItem.attempts.length + 1,
      status: "SUCCEEDED",
      message: "runtime boundary completed"
    },
    publishableArtifactWritten: false
  };
}
