import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import { createFakeNoteProvider } from "../domain/llm-provider/fake-note-provider.js";
import { appendLlmTraceEvent } from "../domain/llm-trace/trace-writer.js";
import { sha256 } from "../storage/sha.js";
import type { WorkItemAgentInput } from "./work-item-agent.js";

export async function runMockNoteAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const sourcePath = input.workItem.sourcePaths[0];
  const baseSha = input.workItem.baseShas[targetPath] ?? null;
  const provider = input.provider ?? createFakeNoteProvider();
  const providerResult = await provider.generateNote({
    runId: input.runId,
    workItem: input.workItem,
    sourceContent: input.sourceContent
  });
  const providerCallId = providerResult.providerCallId;
  const timestamp = "2026-06-11T00:00:00.000Z";

  if (input.store) {
    await appendLlmTraceEvent(input.store, input.workItem.id, {
      schemaVersion: "llm-trace.v1",
      type: "llm.call.started",
      eventId: `${providerCallId}:started`,
      runId: input.runId,
      workItemId: input.workItem.id,
      agentNode: "note",
      providerCallId,
      provider: providerResult.provider,
      model: providerResult.model,
      timestamp,
      request: {
        messagesSha: sha256(input.sourceContent),
        temperature: 0,
        thinkingEnabled: false
      }
    });
  }

  const contentSha = sha256(providerResult.content);
  const finalizedContent = providerResult.content.replace("contentSha: pending", `contentSha: ${contentSha}`);

  if (input.store) {
    await appendLlmTraceEvent(input.store, input.workItem.id, {
      schemaVersion: "llm-trace.v1",
      type: "llm.call.completed",
      eventId: `${providerCallId}:completed`,
      runId: input.runId,
      workItemId: input.workItem.id,
      agentNode: "note",
      providerCallId,
      provider: providerResult.provider,
      model: providerResult.model,
      timestamp,
      finishReason: providerResult.finishReason,
      outputTextSha: sha256(finalizedContent),
      usage: providerResult.usage
    });
  }

  return {
    workItemId: input.workItem.id,
    status: "SUCCEEDED",
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: baseSha ? "MODIFIED" : "CREATED",
        baseSha,
        contentSha: sha256(finalizedContent),
        content: finalizedContent
      }
    ],
    eval: {
      rawFilesSeen: [sourcePath],
      rawMirrorConverted: input.workItem.type === "REWRITE_TOPIC_NOTE",
      placeholderIntroduced: false,
      wikilinksCreated: 0
    }
  };
}
