import {
  createDeepSeekRealNoteProvider,
  DeepSeekRealProviderError
} from "../../domain/llm-provider/deepseek-real-provider.js";
import { createMimoRealNoteProvider, MimoRealProviderError } from "../../domain/llm-provider/mimo-real-provider.js";
import { writeRawEnvelopeArtifacts } from "../../domain/llm-trace/raw-envelope-writer.js";
import type { WorkItem } from "../../domain/planning/work-item.js";
import type { AgentRunsStore } from "../../storage/agent-runs-store.js";

export type RealSmokeProvider = "deepseek-real-smoke" | "mimo-real-smoke" | "claude-code-real-smoke";

export type RealSmokeStatus = "SKIPPED" | "BLOCKED" | "PASSED" | "FAILED";

export interface RealProviderSmokeResult {
  provider: RealSmokeProvider;
  status: RealSmokeStatus;
  realExternalCall: boolean;
  reason: string;
  requiredEnv: string[];
  httpStatus?: number;
  errorMessage?: string;
}

const DEEPSEEK_REQUIRED_ENV = ["DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"];
const MIMO_REQUIRED_ENV = ["MIMO_API_KEY", "MIMO_BASE_URL", "MIMO_MODEL"];

function smokeWorkItem(provider: RealSmokeProvider): WorkItem {
  const id = provider === "mimo-real-smoke" ? "mimo-real-smoke" : "deepseek-real-smoke";
  return {
    id,
    type: "REWRITE_TOPIC_NOTE",
    phase: "phase-a-notes",
    status: "PLANNED",
    sourcePaths: ["raw/smoke.md"],
    targetPaths: ["knowledge-base/topics/smoke.md"],
    baseShas: {
      "raw/smoke.md": "smoke-source-sha"
    },
    risk: "LOW",
    requiresApproval: false,
    reason: "real provider smoke",
    attempts: [],
    publishPolicy: "CANDIDATE_PATCH_ONLY"
  };
}

const deepSeekSmokeWorkItem: WorkItem = {
  id: "deepseek-real-smoke",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/smoke.md"],
  targetPaths: ["knowledge-base/topics/smoke.md"],
  baseShas: {
    "raw/smoke.md": "smoke-source-sha"
  },
  risk: "LOW",
  requiresApproval: false,
  reason: "real provider smoke",
  attempts: [],
  publishPolicy: "CANDIDATE_PATCH_ONLY"
};

function missingEnv(requiredEnv: string[], env: Record<string, string | undefined>): string[] {
  return requiredEnv.filter((name) => !env[name]);
}

export function inspectRealProviderSmoke(input: {
  provider: RealSmokeProvider;
  env: Record<string, string | undefined>;
}): RealProviderSmokeResult {
  if (input.provider === "claude-code-real-smoke") {
    return {
      provider: input.provider,
      status: "BLOCKED",
      realExternalCall: false,
      reason: "CLAUDE_CODE_SDK_NOT_WIRED",
      requiredEnv: []
    };
  }

  const requiredEnv = input.provider === "mimo-real-smoke" ? MIMO_REQUIRED_ENV : DEEPSEEK_REQUIRED_ENV;
  const missing = missingEnv(requiredEnv, input.env);
  if (missing.length > 0) {
    return {
      provider: input.provider,
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv
    };
  }

  return {
    provider: input.provider,
    status: "SKIPPED",
    realExternalCall: false,
    reason: "EXECUTE_REAL_NOT_SET",
    requiredEnv
  };
}

export async function runRealProviderSmoke(input: {
  provider: RealSmokeProvider;
  env: Record<string, string | undefined>;
  executeReal: boolean;
  fetch?: typeof fetch;
  store?: AgentRunsStore;
}): Promise<RealProviderSmokeResult> {
  const inspected = inspectRealProviderSmoke(input);
  if (inspected.status !== "SKIPPED" || inspected.reason === "MISSING_ENV" || !input.executeReal) {
    return inspected;
  }

  try {
    if (input.provider === "mimo-real-smoke") {
      const workItem = smokeWorkItem(input.provider);
      await createMimoRealNoteProvider({
        apiKey: input.env.MIMO_API_KEY!,
        baseUrl: input.env.MIMO_BASE_URL!,
        model: input.env.MIMO_MODEL!,
        maxTokens: 64,
        fetch: input.fetch,
        onRawEnvelope: input.store
          ? async (envelope) =>
              writeRawEnvelopeArtifacts({
                store: input.store!,
                runId: "mimo-real-smoke",
                workItemId: workItem.id,
                providerCallId: `${workItem.id}:mimo-real`,
                provider: "mimo-api",
                model: input.env.MIMO_MODEL!,
                envelope: envelope as { request?: unknown; response?: unknown }
              })
          : undefined
      }).generateNote({
        runId: "mimo-real-smoke",
        workItem,
        sourceContent: "# Smoke\n\nReply with a compact markdown note."
      });
    } else {
      await createDeepSeekRealNoteProvider({
        apiKey: input.env.DEEPSEEK_API_KEY!,
        baseUrl: input.env.DEEPSEEK_BASE_URL!,
        model: input.env.DEEPSEEK_MODEL!,
        maxTokens: 64,
        fetch: input.fetch,
        onRawEnvelope: input.store
          ? async (envelope) =>
              writeRawEnvelopeArtifacts({
                store: input.store!,
                runId: "deepseek-real-smoke",
                workItemId: deepSeekSmokeWorkItem.id,
                providerCallId: `${deepSeekSmokeWorkItem.id}:deepseek-real`,
                provider: "deepseek",
                model: input.env.DEEPSEEK_MODEL!,
                envelope: envelope as { request?: unknown; response?: unknown }
              })
          : undefined
      }).generateNote({
        runId: "deepseek-real-smoke",
        workItem: deepSeekSmokeWorkItem,
        sourceContent: "# Smoke\n\nReply with a compact markdown note."
      });
    }
  } catch (error) {
    const httpStatus =
      error instanceof DeepSeekRealProviderError || error instanceof MimoRealProviderError ? error.httpStatus : undefined;
    return {
      provider: input.provider,
      status: "FAILED",
      realExternalCall: true,
      reason: "REAL_EXTERNAL_CALL_FAILED",
      requiredEnv: inspected.requiredEnv,
      httpStatus,
      errorMessage: error instanceof Error ? error.message : String(error)
    };
  }

  return {
    provider: input.provider,
    status: "PASSED",
    realExternalCall: true,
    reason: "REAL_EXTERNAL_CALL_PASSED",
    requiredEnv: inspected.requiredEnv
  };
}
