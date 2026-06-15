import type { AgentRunsStore } from "../../storage/agent-runs-store.js";
import { redactProviderEnvelope } from "../llm-provider/redaction.js";
import type { LlmTraceProvider } from "./trace-event.js";
import { appendLlmTraceEvent } from "./trace-writer.js";

export async function writeRawEnvelopeArtifacts(input: {
  store: AgentRunsStore;
  runId: string;
  workItemId: string;
  providerCallId: string;
  provider: LlmTraceProvider;
  model: string;
  envelope: {
    request?: unknown;
    response?: unknown;
  };
}): Promise<void> {
  const requestPath = `raw-provider/${input.workItemId}/request.json`;
  const responsePath = `raw-provider/${input.workItemId}/response.json`;
  await input.store.writeJson(requestPath, redactProviderEnvelope(input.envelope.request ?? null));
  await input.store.writeJson(responsePath, redactProviderEnvelope(input.envelope.response ?? null));
  await appendLlmTraceEvent(input.store, input.workItemId, {
    schemaVersion: "llm-trace.v1",
    type: "llm.provider.raw_ref",
    eventId: `${input.providerCallId}:raw-ref`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: input.provider,
    model: input.model,
    timestamp: new Date(0).toISOString(),
    requestPath,
    responsePath,
    redaction: "applied"
  });
}
