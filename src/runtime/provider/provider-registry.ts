import { createFakeNoteProvider } from "../../domain/llm-provider/fake-note-provider.js";
import type { LlmNoteProvider } from "../../domain/llm-provider/provider.js";
import { normalizeProviderRuntimeConfig, type ProviderRuntimeConfig } from "./provider-runtime-config.js";

export function selectNoteProvider(config?: Partial<ProviderRuntimeConfig>): LlmNoteProvider {
  const normalized = normalizeProviderRuntimeConfig(config);

  if (normalized.provider === "fake") {
    return createFakeNoteProvider();
  }

  const exhaustive: never = normalized.provider;
  throw new Error(`Unsupported provider runtime: ${exhaustive}`);
}
