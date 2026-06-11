import type { WorkItemStatus } from "../planning/work-item.js";
import type { LlmCallFailed } from "../llm-trace/trace-event.js";

export type ProviderErrorClass = LlmCallFailed["errorClass"];

export interface ProviderErrorClassification {
  errorClass: ProviderErrorClass;
  retryable: boolean;
  message: string;
}

function defaultRetryable(errorClass: ProviderErrorClass): boolean {
  return errorClass === "timeout" || errorClass === "rate_limit" || errorClass === "network";
}

export class ProviderRuntimeError extends Error {
  readonly errorClass: ProviderErrorClass;
  readonly retryable: boolean;

  constructor(errorClass: ProviderErrorClass, message: string, retryable = defaultRetryable(errorClass)) {
    super(message);
    this.name = "ProviderRuntimeError";
    this.errorClass = errorClass;
    this.retryable = retryable;
  }
}

export function classifyProviderError(error: unknown): ProviderErrorClassification {
  if (error instanceof ProviderRuntimeError) {
    return {
      errorClass: error.errorClass,
      retryable: error.retryable,
      message: error.message
    };
  }

  if (error instanceof Error && error.name === "AbortError") {
    return {
      errorClass: "timeout",
      retryable: true,
      message: error.message
    };
  }

  return {
    errorClass: "unknown",
    retryable: false,
    message: error instanceof Error ? error.message : "Unknown provider error"
  };
}

export function workItemStatusForProviderError(classification: ProviderErrorClassification): WorkItemStatus {
  return classification.errorClass === "timeout" ? "FAILED_TIMEOUT" : "FAILED_EXECUTOR";
}
