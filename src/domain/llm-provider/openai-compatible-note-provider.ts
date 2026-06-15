import type { LlmTraceProvider } from "../llm-trace/trace-event.js";
import { redactProviderEnvelope } from "./redaction.js";
import type { LlmNoteProvider, LlmNoteProviderInput, LlmNoteProviderResult } from "./provider.js";

export interface OpenAiCompatibleNoteProviderConfig {
  apiKey: string;
  baseUrl: string;
  model: string;
  traceProvider: LlmTraceProvider;
  providerCallSuffix: string;
  providerDisplayName: string;
  maxTokens?: number;
  temperature?: number;
  fetch?: typeof fetch;
  onRawEnvelope?: (envelope: unknown) => void | Promise<void>;
  createError?: (message: string, httpStatus?: number) => Error;
}

interface ChatCompletionResponse {
  model: string;
  choices: Array<{
    finish_reason: string | null;
    message: {
      role: string;
      content?: string | null;
      reasoning_content?: string | null;
    };
  }>;
  usage?: {
    prompt_tokens?: number;
    completion_tokens?: number;
    total_tokens?: number;
    completion_tokens_details?: {
      reasoning_tokens?: number;
    };
  };
}

function chatCompletionsUrl(baseUrl: string): string {
  return `${baseUrl.replace(/\/$/, "")}/chat/completions`;
}

function buildMessages(input: LlmNoteProviderInput): Array<{ role: "system" | "user"; content: string }> {
  return [
    {
      role: "system",
      content:
        "You are a bounded knowledge workspace note writer. Return one complete markdown topic note with title, summary, source tracking, key content, and related links."
    },
    {
      role: "user",
      content: `Work item: ${input.workItem.id}
Source paths:
${input.workItem.sourcePaths.map((sourcePath) => `- ${sourcePath}`).join("\n")}

Source content:
${input.sourceContent}`
    }
  ];
}

export function createOpenAiCompatibleNoteProvider(config: OpenAiCompatibleNoteProviderConfig): LlmNoteProvider {
  const fetchImpl = config.fetch ?? fetch;
  return {
    async generateNote(input: LlmNoteProviderInput): Promise<LlmNoteProviderResult> {
      const requestBody = {
        model: config.model,
        messages: buildMessages(input),
        stream: false,
        max_tokens: config.maxTokens,
        temperature: config.temperature
      };
      const requestHeaders = {
        Authorization: `Bearer ${config.apiKey}`,
        "Content-Type": "application/json"
      };
      const url = chatCompletionsUrl(config.baseUrl);
      const response = await fetchImpl(url, {
        method: "POST",
        headers: requestHeaders,
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        const errorBody = await response.text().catch(() => "");
        const sanitizedErrorBody = errorBody.replaceAll(config.apiKey, "[REDACTED]").slice(0, 500);
        const detail = sanitizedErrorBody ? `: ${sanitizedErrorBody}` : "";
        const message = `${config.providerDisplayName} request failed with HTTP ${response.status}${detail}`;
        throw config.createError?.(message, response.status) ?? new Error(message);
      }

      const payload = (await response.json()) as ChatCompletionResponse;
      await config.onRawEnvelope?.(
        redactProviderEnvelope({
          request: {
            url,
            method: "POST",
            headers: requestHeaders,
            body: requestBody
          },
          response: {
            status: response.status,
            body: payload
          }
        })
      );

      const choice = payload.choices[0];
      return {
        providerCallId: `${input.workItem.id}:${config.providerCallSuffix}`,
        provider: config.traceProvider,
        model: payload.model,
        finishReason: choice?.finish_reason ?? null,
        usage: {
          inputTokens: payload.usage?.prompt_tokens,
          outputTokens: payload.usage?.completion_tokens,
          totalTokens: payload.usage?.total_tokens,
          reasoningTokens: payload.usage?.completion_tokens_details?.reasoning_tokens
        },
        content: choice?.message.content ?? ""
      };
    }
  };
}
