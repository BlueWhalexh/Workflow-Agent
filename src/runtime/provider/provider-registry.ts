import { createClaudeCodeFixtureNoteProvider } from "../../domain/llm-provider/claude-code-fixture-provider.js";
import { createDeepSeekFixtureNoteProvider } from "../../domain/llm-provider/deepseek-fixture-provider.js";
import { createFakeNoteProvider } from "../../domain/llm-provider/fake-note-provider.js";
import type { LlmNoteProvider } from "../../domain/llm-provider/provider.js";
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

  const exhaustive: never = normalized.provider;
  throw new Error(`Unsupported provider runtime: ${exhaustive}`);
}
