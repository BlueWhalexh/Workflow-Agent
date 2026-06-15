import { createMimoRealNoteProvider, MimoRealProviderError } from "../../domain/llm-provider/mimo-real-provider.js";
import type { WorkItem } from "../../domain/planning/work-item.js";
import { writeOpenAgentRawProviderArtifacts } from "../open-agent/open-agent-artifacts.js";
import { runOpenAgentGraph, type RunOpenAgentGraphResult } from "../open-agent/open-agent-graph.js";
import {
  type OpenAgentOutputPolicy,
  runOpenAgentTask,
  type RunOpenAgentTaskResult
} from "../../sdk/open-agent-runtime.js";

export type OpenAgentRealSmokeProvider = "mimo-open-agent-smoke";
export type OpenAgentRealSmokeMode = "deterministic" | "llm-graph";
export type OpenAgentRealSmokeStatus = "SKIPPED" | "PASSED" | "FAILED";

export interface OpenAgentRealSmokeResult {
  provider: OpenAgentRealSmokeProvider;
  status: OpenAgentRealSmokeStatus;
  realExternalCall: boolean;
  reason: "MISSING_ENV" | "EXECUTE_REAL_NOT_SET" | "REAL_EXTERNAL_CALL_PASSED" | "REAL_EXTERNAL_CALL_FAILED";
  requiredEnv: string[];
  httpStatus?: number;
  errorMessage?: string;
  artifactPath?: string;
  rawProviderRequestPath?: string;
  rawProviderResponsePath?: string;
  openAgent?: RunOpenAgentTaskResult;
  openAgentGraph?: RunOpenAgentGraphResult;
}

const MIMO_REQUIRED_ENV = ["MIMO_API_KEY", "MIMO_BASE_URL", "MIMO_MODEL"];

const smokeWorkItem: WorkItem = {
  id: "mimo-open-agent-smoke",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/smoke.md"],
  targetPaths: ["knowledge-base/drafts/mimo-open-agent-smoke.md"],
  baseShas: {
    "raw/smoke.md": "smoke-source-sha"
  },
  risk: "LOW",
  requiresApproval: false,
  reason: "open agent real smoke",
  attempts: [],
  publishPolicy: "CANDIDATE_PATCH_ONLY"
};

function missingEnv(env: Record<string, string | undefined>): string[] {
  return MIMO_REQUIRED_ENV.filter((name) => !env[name]);
}

export function inspectOpenAgentRealSmoke(input: {
  provider: OpenAgentRealSmokeProvider;
  env: Record<string, string | undefined>;
}): OpenAgentRealSmokeResult {
  const missing = missingEnv(input.env);
  if (missing.length > 0) {
    return {
      provider: input.provider,
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv: MIMO_REQUIRED_ENV
    };
  }

  return {
    provider: input.provider,
    status: "SKIPPED",
    realExternalCall: false,
    reason: "EXECUTE_REAL_NOT_SET",
    requiredEnv: MIMO_REQUIRED_ENV
  };
}

export async function runOpenAgentRealSmoke(input: {
  provider: OpenAgentRealSmokeProvider;
  workspaceRoot: string;
  env: Record<string, string | undefined>;
  executeReal: boolean;
  mode?: OpenAgentRealSmokeMode;
  outputPolicy?: OpenAgentOutputPolicy;
  fetch?: typeof fetch;
}): Promise<OpenAgentRealSmokeResult> {
  const inspected = inspectOpenAgentRealSmoke({ provider: input.provider, env: input.env });
  if (inspected.reason === "MISSING_ENV" || !input.executeReal) {
    return inspected;
  }

  try {
    if (input.mode === "llm-graph") {
      const openAgentGraph = await runOpenAgentGraph({
        workspaceRoot: input.workspaceRoot,
        taskId: "mimo-open-agent-smoke",
        message: "MiMo real smoke for the knowledge workspace agent graph.",
        methodologyId: "lmwiki-v1",
        outputPolicy: input.outputPolicy ?? "ANSWER_ONLY",
        providerRuntime: {
          provider: "mimo-real",
          timeoutMs: 30000,
          baseUrl: input.env.MIMO_BASE_URL,
          model: input.env.MIMO_MODEL,
          apiKeyEnvName: "MIMO_API_KEY"
        },
        providerRuntimeDependencies: {
          env: input.env,
          fetch: input.fetch
        }
      });
      const firstRawRef = openAgentGraph.rawProviderRefs[0];
      const graphPassed =
        openAgentGraph.status === "SUCCEEDED" &&
        openAgentGraph.providerCalls >= 3 &&
        openAgentGraph.synthesis?.providerBacked === true;

      return {
        provider: input.provider,
        status: graphPassed ? "PASSED" : "FAILED",
        realExternalCall: true,
        reason: graphPassed ? "REAL_EXTERNAL_CALL_PASSED" : "REAL_EXTERNAL_CALL_FAILED",
        requiredEnv: MIMO_REQUIRED_ENV,
        artifactPath: openAgentGraph.artifactPath,
        rawProviderRequestPath: firstRawRef?.requestPath,
        rawProviderResponsePath: firstRawRef?.responsePath,
        openAgentGraph
      };
    }

    let rawEnvelope: { request?: unknown; response?: unknown } | null = null;
    const note = await createMimoRealNoteProvider({
      apiKey: input.env.MIMO_API_KEY!,
      baseUrl: input.env.MIMO_BASE_URL!,
      model: input.env.MIMO_MODEL!,
      maxTokens: 96,
      fetch: input.fetch,
      onRawEnvelope: (envelope) => {
        rawEnvelope = envelope as { request?: unknown; response?: unknown };
      }
    }).generateNote({
      runId: "mimo-open-agent-smoke",
      workItem: smokeWorkItem,
      sourceContent: "# Open Agent Smoke\n\nReply with a compact markdown note for a candidate patch smoke test."
    });

    const openAgent = await runOpenAgentTask({
      workspaceRoot: input.workspaceRoot,
      taskId: "mimo-open-agent-smoke",
      methodologyId: "lmwiki-v1",
      objective: `MiMo real smoke produced provider output for candidate patch eval. Finish reason: ${note.finishReason}.`,
      risk: "DRAFT_ONLY",
      outputPolicy: "CANDIDATE_PATCH",
      allowedToolNames: ["workspace.scan", "artifact.readEval", "patch.validate"],
      blockedToolNames: ["patch.publish"]
    });

    return {
      provider: input.provider,
      status: "PASSED",
      realExternalCall: true,
      reason: "REAL_EXTERNAL_CALL_PASSED",
      requiredEnv: MIMO_REQUIRED_ENV,
      artifactPath: openAgent.artifactPath,
      openAgent
    };
  } catch (error) {
    return {
      provider: input.provider,
      status: "FAILED",
      realExternalCall: true,
      reason: "REAL_EXTERNAL_CALL_FAILED",
      requiredEnv: MIMO_REQUIRED_ENV,
      httpStatus: error instanceof MimoRealProviderError ? error.httpStatus : undefined,
      errorMessage: error instanceof Error ? error.message : String(error)
    };
  }
}
