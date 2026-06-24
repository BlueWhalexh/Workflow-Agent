import { z } from "zod";
import { AgentRuntimeNameSchema, ExecutionEvidenceSchema } from "../core/types.js";

export const agentEventTypes = [
  "run_started",
  "run_completed",
  "run_failed",
  "run_cancelled",
  "run_paused_for_approval",
  "model_call_started",
  "model_call_finished",
  "tool_call_started",
  "tool_finished",
  "changeset_proposed",
  "validator_failed",
  "changeset_ready_for_review",
  "request_approval",
  "approval_consumed",
  "approval_expired",
  "changeset_applied",
  "changeset_stale",
  "changeset_rejected_by_user",
  "chat_message_appended",
  "plan_updated",
  "context_compacted",
  "heartbeat"
] as const;

export const AgentEventTypeSchema = z.enum(agentEventTypes);
export type AgentEventType = z.infer<typeof AgentEventTypeSchema>;

const JsonValueSchema: z.ZodType<unknown> = z.lazy(() =>
  z.union([
    z.string(),
    z.number(),
    z.boolean(),
    z.null(),
    z.array(JsonValueSchema),
    z.record(z.string(), JsonValueSchema)
  ])
);

export const AgentEventEnvelopeSchema = z.object({
  schemaVersion: z.literal(1),
  eventId: z.string().min(1),
  sequence: z.number().int().nonnegative(),
  occurredAt: z.string().datetime(),
  sessionId: z.string().min(1),
  runId: z.string().min(1),
  engine: AgentRuntimeNameSchema,
  evidence: ExecutionEvidenceSchema,
  type: AgentEventTypeSchema,
  payload: JsonValueSchema
}).strict();

export type AgentEventEnvelope = z.infer<typeof AgentEventEnvelopeSchema>;

export type AgentEventEnvelopeDraft = Omit<AgentEventEnvelope, "schemaVersion">;

export function createAgentEventEnvelope(draft: AgentEventEnvelopeDraft): AgentEventEnvelope {
  return AgentEventEnvelopeSchema.parse({
    schemaVersion: 1,
    ...draft
  });
}
