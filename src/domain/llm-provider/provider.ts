import type { WorkItem } from "../planning/work-item.js";
import type { LlmTraceProvider } from "../llm-trace/trace-event.js";

export interface LlmNoteProviderInput {
  runId: string;
  workItem: WorkItem;
  sourceContent: string;
}

export interface LlmNoteProviderResult {
  providerCallId: string;
  provider: LlmTraceProvider;
  model: string;
  finishReason: string | null;
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
    totalTokens?: number;
    reasoningTokens?: number;
    costUsd?: number;
  };
  content: string;
}

export interface LlmNoteProvider {
  generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult>;
}
