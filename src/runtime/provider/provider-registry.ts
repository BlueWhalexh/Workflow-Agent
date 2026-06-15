import { createClaudeCodeFixtureNoteProvider } from "../../domain/llm-provider/claude-code-fixture-provider.js";
import { createDeepSeekFixtureNoteProvider } from "../../domain/llm-provider/deepseek-fixture-provider.js";
import { createDeepSeekRealNoteProvider } from "../../domain/llm-provider/deepseek-real-provider.js";
import { createFakeNoteProvider } from "../../domain/llm-provider/fake-note-provider.js";
import {
  createFailingNoteProvider,
  createInvalidContentNoteProvider,
  createWeakRelationsNoteProvider
} from "../../domain/llm-provider/harness-note-provider.js";
import { createMimoRealNoteProvider } from "../../domain/llm-provider/mimo-real-provider.js";
import { createMimoVllmFixtureNoteProvider } from "../../domain/llm-provider/mimo-vllm-fixture-provider.js";
import type { LlmNoteProvider } from "../../domain/llm-provider/provider.js";
import { ProviderRuntimeError } from "../../domain/llm-provider/provider-error.js";
import type { LlmTraceProvider } from "../../domain/llm-trace/trace-event.js";
import { normalizeProviderRuntimeConfig, type ProviderRuntimeConfig } from "./provider-runtime-config.js";

export interface ProviderRuntimeDependencies {
  env?: Record<string, string | undefined>;
  fetch?: typeof fetch;
}

const DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
const DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-pro";
const DEFAULT_DEEPSEEK_API_KEY_ENV = "DEEPSEEK_API_KEY";
const DEFAULT_MIMO_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1";
const DEFAULT_MIMO_MODEL = "mimo-v2.5";
const DEFAULT_MIMO_API_KEY_ENV = "MIMO_API_KEY";

export function selectNoteProvider(
  config?: Partial<ProviderRuntimeConfig>,
  dependencies: ProviderRuntimeDependencies = {}
): LlmNoteProvider {
  const normalized = normalizeProviderRuntimeConfig(config);

  if (normalized.provider === "fake") {
    return createFakeNoteProvider();
  }
  if (normalized.provider === "deepseek-fixture") {
    return createDeepSeekFixtureNoteProvider();
  }
  if (normalized.provider === "deepseek-real") {
    const env = dependencies.env ?? process.env;
    const apiKeyEnvName = normalized.apiKeyEnvName ?? DEFAULT_DEEPSEEK_API_KEY_ENV;
    const apiKey = env[apiKeyEnvName];
    if (!apiKey) {
      throw new ProviderRuntimeError("auth", "MISSING_DEEPSEEK_API_KEY", false);
    }
    return createDeepSeekRealNoteProvider({
      apiKey,
      baseUrl: normalized.baseUrl ?? DEFAULT_DEEPSEEK_BASE_URL,
      model: normalized.model ?? DEFAULT_DEEPSEEK_MODEL,
      maxTokens: normalized.maxTokens,
      temperature: normalized.temperature,
      fetch: dependencies.fetch
    });
  }
  if (normalized.provider === "claude-code-fixture") {
    return createClaudeCodeFixtureNoteProvider();
  }
  if (normalized.provider === "mimo-vllm-fixture") {
    return createMimoVllmFixtureNoteProvider();
  }
  if (normalized.provider === "mimo-real") {
    const env = dependencies.env ?? process.env;
    const apiKeyEnvName = normalized.apiKeyEnvName ?? DEFAULT_MIMO_API_KEY_ENV;
    const apiKey = env[apiKeyEnvName];
    if (!apiKey) {
      throw new ProviderRuntimeError("auth", "MISSING_MIMO_API_KEY", false);
    }
    return createMimoRealNoteProvider({
      apiKey,
      baseUrl: normalized.baseUrl ?? DEFAULT_MIMO_BASE_URL,
      model: normalized.model ?? DEFAULT_MIMO_MODEL,
      maxTokens: normalized.maxTokens,
      temperature: normalized.temperature,
      fetch: dependencies.fetch
    });
  }
  if (normalized.provider === "weak-relations-fixture") {
    return createWeakRelationsNoteProvider();
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
  if (normalized.provider === "deepseek-real") {
    return "deepseek";
  }
  if (normalized.provider === "claude-code-fixture") {
    return "claude-code";
  }
  if (normalized.provider === "mimo-vllm-fixture") {
    return "mimo-vllm";
  }
  if (normalized.provider === "mimo-real") {
    return "mimo-api";
  }
  return "fake";
}
