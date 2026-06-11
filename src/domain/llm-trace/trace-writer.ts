import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";
import type { AgentRunsStore } from "../../storage/agent-runs-store.js";
import type { LlmTraceEvent } from "./trace-event.js";

export function previewText(value: string, maxChars = 160): string {
  return value.slice(0, maxChars);
}

export async function appendLlmTraceEvent(
  store: AgentRunsStore,
  workItemId: string,
  event: LlmTraceEvent
): Promise<void> {
  const tracePath = store.artifactPath(`traces/${workItemId}.jsonl`);
  await mkdir(path.dirname(tracePath), { recursive: true });
  await appendFile(tracePath, `${JSON.stringify(event)}\n`, "utf8");
}
