import { createOpenAiCompatibleNoteProvider } from "./openai-compatible-note-provider.js";
import type { LlmNoteProvider } from "./provider.js";

export interface MimoRealProviderConfig {
  apiKey: string;
  baseUrl: string;
  model: string;
  maxTokens?: number;
  temperature?: number;
  fetch?: typeof fetch;
  onRawEnvelope?: (envelope: unknown) => void | Promise<void>;
}

export class MimoRealProviderError extends Error {
  constructor(
    message: string,
    readonly httpStatus?: number
  ) {
    super(message);
    this.name = "MimoRealProviderError";
  }
}

export function createMimoRealNoteProvider(config: MimoRealProviderConfig): LlmNoteProvider {
  return createOpenAiCompatibleNoteProvider({
    ...config,
    traceProvider: "mimo-api",
    providerCallSuffix: "mimo-real",
    providerDisplayName: "MiMo",
    createError: (message, httpStatus) => new MimoRealProviderError(message, httpStatus)
  });
}
