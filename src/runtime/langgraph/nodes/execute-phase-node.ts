import { runMockNoteAgent } from "../../../agents/mock-note-agent.js";
import { classifyProviderError, workItemStatusForProviderError } from "../../../domain/llm-provider/provider-error.js";
import type { OrganizePlan } from "../../../domain/planning/plan.js";
import { checkMerge } from "../../../domain/patch/merge-guard.js";
import { publishBundle } from "../../../domain/patch/publisher.js";
import { validateBundle } from "../../../domain/validation/validator.js";
import { appendLlmTraceEvent } from "../../../domain/llm-trace/trace-writer.js";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import { readWorkspaceFile } from "../../../storage/workspace-fs.js";
import { selectNoteProvider, traceProviderForRuntime } from "../../provider/provider-registry.js";
import type { GraphState } from "../state.js";

export async function executePhaseNode(state: GraphState): Promise<Partial<GraphState>> {
  if (!state.autoApprove) {
    return {};
  }

  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const plan = await store.readJson<OrganizePlan>("plan.json");
  const noteItem = plan.workItems.find((item) => item.type === "REWRITE_TOPIC_NOTE");
  if (!noteItem) {
    return { status: "FAILED", lastError: "NO_REWRITE_WORK_ITEM" };
  }

  const sourceContent = await readWorkspaceFile(state.workspaceRoot, noteItem.sourcePaths[0]);
  const provider = selectNoteProvider(state.providerRuntime);
  let providerFailureLastError: string | undefined;
  const bundle = await runMockNoteAgent({
    runId: state.runId,
    workItem: noteItem,
    sourceContent,
    store,
    provider
  }).catch(async (error: unknown) => {
    const classification = classifyProviderError(error);
    providerFailureLastError = `PROVIDER_${classification.errorClass.toUpperCase()}`;
    const failedStatus = workItemStatusForProviderError(classification);
    const providerCallId = `${noteItem.id}:provider-failed`;
    await appendLlmTraceEvent(store, noteItem.id, {
      schemaVersion: "llm-trace.v1",
      type: "llm.call.failed",
      eventId: `${providerCallId}:failed`,
      runId: state.runId,
      workItemId: noteItem.id,
      agentNode: "note",
      providerCallId,
      provider: traceProviderForRuntime(state.providerRuntime),
      model: state.providerRuntime?.model ?? state.providerRuntime?.provider ?? "fake",
      timestamp: new Date(0).toISOString(),
      errorClass: classification.errorClass,
      retryable: classification.retryable,
      message: classification.message
    });
    await store.writeJson(`work-items/${noteItem.id}.json`, {
      ...noteItem,
      status: failedStatus,
      attempts: [
        ...noteItem.attempts,
        {
          attempt: noteItem.attempts.length + 1,
          status: failedStatus,
          message: classification.message
        }
      ]
    });
    return null;
  });

  if (!bundle) {
    return {
      status: "FAILED",
      lastError: providerFailureLastError ?? "PROVIDER_UNKNOWN"
    };
  }
  await store.writeJson(`patches/${noteItem.id}.patch.json`, bundle);

  const mergeDecision = await checkMerge({
    workspaceRoot: state.workspaceRoot,
    authorizedTargetPaths: noteItem.targetPaths,
    bundle
  });
  const validation = validateBundle({
    targetPaths: noteItem.targetPaths,
    files: bundle.files
  });

  await store.writeJson("validation.json", { mergeDecision, validation });
  if (!mergeDecision.allowed || !validation.allowed) {
    await store.writeJson(`work-items/${noteItem.id}.json`, {
      ...noteItem,
      status: validation.allowed ? "FAILED_EXECUTOR" : "BLOCKED_BY_VALIDATOR"
    });
    return { status: "FAILED", lastError: "PATCH_BLOCKED" };
  }

  await publishBundle({ workspaceRoot: state.workspaceRoot, bundle });
  await store.writeJson(`work-items/${noteItem.id}.json`, {
    ...noteItem,
    status: "PUBLISHED"
  });

  return {};
}
