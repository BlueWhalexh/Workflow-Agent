export type ProviderRuntimeName =
  | "fake"
  | "deepseek-fixture"
  | "claude-code-fixture"
  | "timeout-fixture"
  | "invalid-content-fixture";

export interface ProviderRuntimeConfig {
  provider: ProviderRuntimeName;
  timeoutMs: number;
  model?: string;
  temperature?: number;
  maxTokens?: number;
}

export const DEFAULT_PROVIDER_RUNTIME_CONFIG: ProviderRuntimeConfig = {
  provider: "fake",
  timeoutMs: 30000
};

export function normalizeProviderRuntimeConfig(input?: Partial<ProviderRuntimeConfig>): ProviderRuntimeConfig {
  return {
    ...DEFAULT_PROVIDER_RUNTIME_CONFIG,
    ...input,
    provider: input?.provider ?? DEFAULT_PROVIDER_RUNTIME_CONFIG.provider
  };
}
