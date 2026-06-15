import { createOpenAiCompatibleNoteProvider } from "./openai-compatible-note-provider.js";
import type { LlmNoteProvider } from "./provider.js";

export interface DeepSeekRealProviderConfig {
  apiKey: string;
  baseUrl: string;
  model: string;
  maxTokens?: number;
  temperature?: number;
  fetch?: typeof fetch;
  onRawEnvelope?: (envelope: unknown) => void | Promise<void>;
}

export class DeepSeekRealProviderError extends Error {
  constructor(
    message: string,
    readonly httpStatus?: number
  ) {
    super(message);
    this.name = "DeepSeekRealProviderError";
  }
}

export function createDeepSeekRealNoteProvider(config: DeepSeekRealProviderConfig): LlmNoteProvider {
  return createOpenAiCompatibleNoteProvider({
    ...config,
    traceProvider: "deepseek",
    providerCallSuffix: "deepseek-real",
    providerDisplayName: "DeepSeek",
    createError: (message, httpStatus) => new DeepSeekRealProviderError(message, httpStatus)
  });
}
