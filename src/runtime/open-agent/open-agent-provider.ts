import type { OpenAgentOutputPolicy } from "../../sdk/open-agent-runtime.js";
import { redactProviderEnvelope } from "../../domain/llm-provider/redaction.js";
import { ProviderRuntimeError } from "../../domain/llm-provider/provider-error.js";
import type { ProviderRuntimeDependencies } from "../provider/provider-registry.js";
import { normalizeProviderRuntimeConfig, type ProviderRuntimeConfig } from "../provider/provider-runtime-config.js";
import type {
  OpenAgentNextAction,
  OpenAgentPlan,
  OpenAgentProvider,
  OpenAgentSynthesisOutput
} from "./open-agent-state.js";

export class OpenAgentProviderValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "OpenAgentProviderValidationError";
  }
}

function isOutputPolicy(value: unknown): value is OpenAgentOutputPolicy {
  return value === "ANSWER_ONLY" || value === "DRAFT_ARTIFACT" || value === "CANDIDATE_PATCH";
}

function isNextAction(value: unknown): value is OpenAgentNextAction["action"] {
  return value === "READ_CONTEXT" || value === "SOLVED" || value === "REQUEST_WRITE_CONFIRMATION";
}

function hasNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function hasStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === "string");
}

export function parseOpenAgentPlan(value: OpenAgentPlan | string): OpenAgentPlan {
  const parsed = typeof value === "string" ? safeParseJson(value) : value;
  if (!parsed || typeof parsed !== "object") {
    throw new OpenAgentProviderValidationError("Open agent plan must be an object.");
  }
  const candidate = parsed as Partial<OpenAgentPlan>;
  if (typeof candidate.objective !== "string" || candidate.objective.trim().length === 0) {
    throw new OpenAgentProviderValidationError("Open agent plan objective must be a non-empty string.");
  }
  if (!isOutputPolicy(candidate.outputPolicy)) {
    throw new OpenAgentProviderValidationError("Open agent plan outputPolicy is invalid.");
  }
  if (!Array.isArray(candidate.steps) || !candidate.steps.every((step) => typeof step === "string")) {
    throw new OpenAgentProviderValidationError("Open agent plan steps must be a string array.");
  }
  const contextHints =
    typeof candidate.contextHints === "string"
      ? [candidate.contextHints]
      : Array.isArray(candidate.contextHints)
        ? candidate.contextHints
        : null;
  if (!contextHints || !contextHints.every((hint) => typeof hint === "string")) {
    throw new OpenAgentProviderValidationError("Open agent plan contextHints must be a string array.");
  }
  return {
    objective: candidate.objective,
    outputPolicy: candidate.outputPolicy,
    steps: candidate.steps,
    contextHints
  };
}

function stripJsonFence(value: string): string {
  const trimmed = value.trim();
  const fenced = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(trimmed);
  if (fenced) {
    return fenced[1].trim();
  }
  const start = trimmed.indexOf("{");
  const end = trimmed.lastIndexOf("}");
  if (start >= 0 && end > start) {
    return trimmed.slice(start, end + 1);
  }
  return trimmed;
}

function safeParseJson(value: string): unknown {
  try {
    return JSON.parse(stripJsonFence(value));
  } catch {
    throw new OpenAgentProviderValidationError("Open agent provider returned invalid JSON.");
  }
}

export function parseOpenAgentNextAction(value: OpenAgentNextAction | string): OpenAgentNextAction {
  const parsed = typeof value === "string" ? safeParseJson(value) : value;
  if (!parsed || typeof parsed !== "object") {
    throw new OpenAgentProviderValidationError("Open agent next action must be an object.");
  }
  const candidate = parsed as Partial<OpenAgentNextAction>;
  if (!isNextAction(candidate.action)) {
    throw new OpenAgentProviderValidationError("Open agent next action is invalid.");
  }
  if (typeof candidate.summary !== "string" || candidate.summary.trim().length === 0) {
    throw new OpenAgentProviderValidationError("Open agent next action summary must be a non-empty string.");
  }
  if (candidate.toolName !== undefined && typeof candidate.toolName !== "string") {
    throw new OpenAgentProviderValidationError("Open agent next action toolName must be a string.");
  }
  return {
    action: candidate.action,
    toolName: candidate.toolName,
    summary: candidate.summary
  };
}

export function parseOpenAgentSynthesisOutput(value: OpenAgentSynthesisOutput | string): OpenAgentSynthesisOutput {
  const parsed = typeof value === "string" ? safeParseJson(value) : value;
  if (!parsed || typeof parsed !== "object") {
    throw new OpenAgentProviderValidationError("Open agent synthesis output must be an object.");
  }
  const candidate = parsed as Partial<OpenAgentSynthesisOutput>;
  if (!hasStringArray(candidate.groundingRefs) || candidate.groundingRefs.length === 0) {
    throw new OpenAgentProviderValidationError("Open agent synthesis groundingRefs must be a non-empty string array.");
  }
  if (candidate.kind === "ANSWER") {
    if (!hasNonEmptyString(candidate.answer)) {
      throw new OpenAgentProviderValidationError("Open agent answer synthesis must include a non-empty answer.");
    }
    return {
      kind: "ANSWER",
      answer: candidate.answer,
      groundingRefs: candidate.groundingRefs
    };
  }
  if (candidate.kind === "DRAFT_ARTIFACT") {
    if (!hasNonEmptyString(candidate.title) || !hasNonEmptyString(candidate.content)) {
      throw new OpenAgentProviderValidationError("Open agent draft synthesis must include non-empty title and content.");
    }
    return {
      kind: "DRAFT_ARTIFACT",
      title: candidate.title,
      content: candidate.content,
      groundingRefs: candidate.groundingRefs
    };
  }
  if (candidate.kind === "CANDIDATE_PATCH") {
    if (
      !hasNonEmptyString(candidate.title) ||
      !hasNonEmptyString(candidate.content) ||
      !hasNonEmptyString(candidate.targetPath)
    ) {
      throw new OpenAgentProviderValidationError(
        "Open agent candidate synthesis must include non-empty title, content, and targetPath."
      );
    }
    return {
      kind: "CANDIDATE_PATCH",
      title: candidate.title,
      content: candidate.content,
      targetPath: candidate.targetPath,
      groundingRefs: candidate.groundingRefs
    };
  }
  throw new OpenAgentProviderValidationError("Open agent synthesis kind is invalid.");
}

interface ChatCompletionResponse {
  model: string;
  choices: Array<{
    finish_reason: string | null;
    message: {
      role: string;
      content?: string | null;
    };
  }>;
  usage?: {
    prompt_tokens?: number;
    completion_tokens?: number;
    total_tokens?: number;
  };
}

export interface OpenAiCompatibleOpenAgentProviderConfig {
  apiKey: string;
  baseUrl: string;
  model: string;
  maxTokens?: number;
  temperature?: number;
  fetch?: typeof fetch;
  onRawEnvelope?: (input: {
    providerCallId: string;
    envelope: {
      request?: unknown;
      response?: unknown;
    };
  }) => void | Promise<void>;
}

function chatCompletionsUrl(baseUrl: string): string {
  return `${baseUrl.replace(/\/$/, "")}/chat/completions`;
}

export function createOpenAiCompatibleOpenAgentProvider(config: OpenAiCompatibleOpenAgentProviderConfig): OpenAgentProvider {
  const fetchImpl = config.fetch ?? fetch;
  let callIndex = 0;

  async function callJson(input: {
    kind: "plan" | "next-action" | "synthesize";
    system: string;
    user: string;
  }): Promise<string> {
    callIndex += 1;
    const providerCallId = `open-agent-${input.kind}-${callIndex}`;
    const requestBody = {
      model: config.model,
      messages: [
        { role: "system", content: input.system },
        { role: "user", content: input.user }
      ],
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
      const detail = errorBody.replaceAll(config.apiKey, "[REDACTED]").slice(0, 500);
      throw new ProviderRuntimeError("provider", `OPEN_AGENT_PROVIDER_HTTP_${response.status}${detail ? `:${detail}` : ""}`, true);
    }

    const payload = (await response.json()) as ChatCompletionResponse;
    await config.onRawEnvelope?.({
      providerCallId,
      envelope: redactProviderEnvelope({
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
      }) as { request?: unknown; response?: unknown }
    });
    return payload.choices[0]?.message.content ?? "";
  }

  return {
    async plan(input) {
      const content = await callJson({
        kind: "plan",
        system:
          "You are a bounded knowledge workspace planning agent. This is not web Open Graph metadata. Return only JSON with objective, outputPolicy, steps, and contextHints as an array of short path/search hint strings. Do not include markdown.",
        user: `Objective: ${input.objective}
Output policy: ${input.outputPolicy}`
      });
      return parseOpenAgentPlan(content);
    },
    async nextAction(input) {
      const content = await callJson({
        kind: "next-action",
        system:
          "You are a bounded knowledge workspace tool-loop controller. This is not web Open Graph metadata. Return only JSON with action and summary, optionally toolName. Allowed action values: READ_CONTEXT, SOLVED, REQUEST_WRITE_CONFIRMATION. If grounding refs are already provided and the task can be answered from them, return SOLVED. Do not repeatedly ask to read files; the runtime, not you, performs file reads.",
        user: `Iteration: ${input.iteration}
Plan objective: ${input.plan.objective}
Grounding refs:
${input.groundingRefs.map((ref) => `- ${ref}`).join("\n")}`
      });
      return parseOpenAgentNextAction(content);
    },
    async synthesize(input) {
      const content = await callJson({
        kind: "synthesize",
        system:
          "You are a bounded knowledge workspace synthesis agent. Return only JSON. Allowed output shapes: ANSWER_ONLY must return {\"kind\":\"ANSWER\",\"answer\":\"...\",\"groundingRefs\":[...]}; DRAFT_ARTIFACT must return {\"kind\":\"DRAFT_ARTIFACT\",\"title\":\"...\",\"content\":\"...\",\"groundingRefs\":[...]}; CANDIDATE_PATCH must return {\"kind\":\"CANDIDATE_PATCH\",\"title\":\"...\",\"content\":\"...\",\"targetPath\":\"knowledge-base/drafts/name.md\",\"groundingRefs\":[...]}. Answer, draft, and candidate content must include Sources that cite provided grounding refs. Draft content must include the exact marker phrase \"Draft only\". Candidate targetPath must stay under knowledge-base/. Do not publish, do not write files, and do not include markdown fences.",
        user: `Objective: ${input.objective}
Output policy: ${input.outputPolicy}
Methodology: ${input.methodologyId}
Grounding refs:
${input.groundingRefs.map((ref) => `- ${ref}`).join("\n")}
Context digest:
${input.contextDigest.map((entry) => `Path: ${entry.path}\nExcerpt: ${entry.excerpt}`).join("\n\n")}`
      });
      return parseOpenAgentSynthesisOutput(content);
    }
  };
}

const DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";
const DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-pro";
const DEFAULT_DEEPSEEK_API_KEY_ENV = "DEEPSEEK_API_KEY";
const DEFAULT_MIMO_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1";
const DEFAULT_MIMO_MODEL = "mimo-v2.5";
const DEFAULT_MIMO_API_KEY_ENV = "MIMO_API_KEY";

export function selectOpenAgentProvider(input: {
  config?: Partial<ProviderRuntimeConfig>;
  dependencies?: ProviderRuntimeDependencies;
  onRawEnvelope?: OpenAiCompatibleOpenAgentProviderConfig["onRawEnvelope"];
}): OpenAgentProvider {
  const normalized = normalizeProviderRuntimeConfig(input.config);
  if (normalized.provider === "mimo-real") {
    const env = input.dependencies?.env ?? process.env;
    const apiKey = env[normalized.apiKeyEnvName ?? DEFAULT_MIMO_API_KEY_ENV];
    if (!apiKey) {
      throw new ProviderRuntimeError("auth", "MISSING_MIMO_API_KEY", false);
    }
    return createOpenAiCompatibleOpenAgentProvider({
      apiKey,
      baseUrl: normalized.baseUrl ?? DEFAULT_MIMO_BASE_URL,
      model: normalized.model ?? DEFAULT_MIMO_MODEL,
      maxTokens: normalized.maxTokens,
      temperature: normalized.temperature,
      fetch: input.dependencies?.fetch,
      onRawEnvelope: input.onRawEnvelope
    });
  }
  if (normalized.provider === "deepseek-real") {
    const env = input.dependencies?.env ?? process.env;
    const apiKey = env[normalized.apiKeyEnvName ?? DEFAULT_DEEPSEEK_API_KEY_ENV];
    if (!apiKey) {
      throw new ProviderRuntimeError("auth", "MISSING_DEEPSEEK_API_KEY", false);
    }
    return createOpenAiCompatibleOpenAgentProvider({
      apiKey,
      baseUrl: normalized.baseUrl ?? DEFAULT_DEEPSEEK_BASE_URL,
      model: normalized.model ?? DEFAULT_DEEPSEEK_MODEL,
      maxTokens: normalized.maxTokens,
      temperature: normalized.temperature,
      fetch: input.dependencies?.fetch,
      onRawEnvelope: input.onRawEnvelope
    });
  }
  return createDeterministicOpenAgentProvider();
}

export function createDeterministicOpenAgentProvider(): OpenAgentProvider {
  return {
    async plan(input: { objective: string; outputPolicy: OpenAgentOutputPolicy }): Promise<OpenAgentPlan> {
      return {
        objective: input.objective,
        outputPolicy: input.outputPolicy,
        steps: ["scan workspace", "read context", "synthesize output", "self-check"],
        contextHints: ["raw", "knowledge-base"]
      };
    },
    async nextAction(): Promise<OpenAgentNextAction> {
      return {
        action: "SOLVED",
        summary: "Deterministic open agent provider completed after context gather."
      };
    }
  };
}
