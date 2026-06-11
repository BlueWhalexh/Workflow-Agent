import { ProviderRuntimeError, type ProviderErrorClass } from "./provider-error.js";
import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

export function createFailingNoteProvider(errorClass: ProviderErrorClass): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      throw new ProviderRuntimeError(errorClass, `${input.workItem.id} ${errorClass}`);
    }
  };
}

export function createInvalidContentNoteProvider(): LlmNoteProvider {
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      return {
        providerCallId: `${input.workItem.id}:invalid-content-fixture`,
        provider: "fake",
        model: "invalid-content-fixture",
        finishReason: "stop",
        usage: {
          inputTokens: 1,
          outputTokens: 1,
          totalTokens: 2
        },
        content: "# Invalid Provider Output\n\ncontentSha: pending\n"
      };
    }
  };
}
