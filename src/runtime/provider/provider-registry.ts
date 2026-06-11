import { createClaudeCodeFixtureNoteProvider } from "../../domain/llm-provider/claude-code-fixture-provider.js";
import { createDeepSeekFixtureNoteProvider } from "../../domain/llm-provider/deepseek-fixture-provider.js";
import { createFakeNoteProvider } from "../../domain/llm-provider/fake-note-provider.js";
import { createFailingNoteProvider, createInvalidContentNoteProvider } from "../../domain/llm-provider/harness-note-provider.js";
import type { LlmNoteProvider } from "../../domain/llm-provider/provider.js";
import type { LlmTraceProvider } from "../../domain/llm-trace/trace-event.js";
import { normalizeProviderRuntimeConfig, type ProviderRuntimeConfig } from "./provider-runtime-config.js";

export function selectNoteProvider(config?: Partial<ProviderRuntimeConfig>): LlmNoteProvider {
  const normalized = normalizeProviderRuntimeConfig(config);

  if (normalized.provider === "fake") {
    return createFakeNoteProvider();
  }
  if (normalized.provider === "deepseek-fixture") {
    return createDeepSeekFixtureNoteProvider();
  }
  if (normalized.provider === "claude-code-fixture") {
    return createClaudeCodeFixtureNoteProvider();
  }
  if (normalized.provider === "timeout-fixture") {
    return createFailingNoteProvider("timeout");
  }
  if (normalized.provider === "invalid-content-fixture") {
    return createInvalidContentNoteProvider();
  }

  const exhaustive: never = normalized.provider;
  throw new Error(`Unsupported provider runtime: ${exhaustive}`);
}

export function traceProviderForRuntime(config?: Partial<ProviderRuntimeConfig>): LlmTraceProvider {
  const normalized = normalizeProviderRuntimeConfig(config);
  if (normalized.provider === "deepseek-fixture") {
    return "deepseek";
  }
  if (normalized.provider === "claude-code-fixture") {
    return "claude-code";
  }
  return "fake";
}
