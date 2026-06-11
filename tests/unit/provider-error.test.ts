import { describe, expect, it } from "vitest";
import {
  classifyProviderError,
  ProviderRuntimeError,
  workItemStatusForProviderError
} from "../../src/domain/llm-provider/provider-error.js";

describe("provider error classification", () => {
  it("classifies explicit provider runtime errors", () => {
    expect(classifyProviderError(new ProviderRuntimeError("timeout", "timed out"))).toEqual({
      errorClass: "timeout",
      retryable: true,
      message: "timed out"
    });
    expect(classifyProviderError(new ProviderRuntimeError("auth", "bad key"))).toEqual({
      errorClass: "auth",
      retryable: false,
      message: "bad key"
    });
    expect(classifyProviderError(new ProviderRuntimeError("schema", "bad response"))).toEqual({
      errorClass: "schema",
      retryable: false,
      message: "bad response"
    });
  });

  it("maps provider error classes to work item statuses", () => {
    expect(workItemStatusForProviderError({ errorClass: "timeout", retryable: true, message: "timed out" })).toBe(
      "FAILED_TIMEOUT"
    );
    expect(workItemStatusForProviderError({ errorClass: "auth", retryable: false, message: "bad key" })).toBe(
      "FAILED_EXECUTOR"
    );
  });
});
