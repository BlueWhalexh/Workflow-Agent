import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import { createFakeNoteProvider } from "../domain/llm-provider/fake-note-provider.js";
import { appendLlmTraceEvent } from "../domain/llm-trace/trace-writer.js";
import { sha256 } from "../storage/sha.js";
import type { WorkItemAgentInput } from "./work-item-agent.js";
import { runNoteQualityLoop, type NoteQualityLoopStep } from "./note-quality-loop.js";
import {
  buildLoopReport,
  budgetForWorkItemType,
  writeLoopReport,
  type WorkItemAgentLoopStep
} from "./work-item-agent-runtime.js";

function loopStepKind(step: NoteQualityLoopStep): WorkItemAgentLoopStep["kind"] {
  if (step.name === "GENERATE_NOTE") {
    return "draft";
  }
  if (step.name === "REPAIR_NOTE") {
    return "repair";
  }
  return "self_check";
}

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

  const qualityLoop = runNoteQualityLoop({
    workItemId: input.workItem.id,
    draftContent: providerResult.content
  });
  const loopIterations = qualityLoop.report.repairedIssues.length > 0 ? 2 : 1;
  if (input.store) {
    await writeLoopReport(
      input.store,
      buildLoopReport({
        runId: input.runId,
        workItemId: input.workItem.id,
        workItemType: input.workItem.type,
        agentNode: "note",
        status: qualityLoop.report.remainingIssues.length === 0 ? "SUCCEEDED" : "SUCCEEDED_WITH_WARNINGS",
        budget: budgetForWorkItemType(input.workItem.type),
        usage: { iterations: loopIterations, providerCalls: 1 },
        steps: qualityLoop.report.steps.map((step) => ({
          name: step.name,
          kind: loopStepKind(step),
          status: step.status,
          issues: step.issues,
          repairedIssues: step.repairedIssues
        })),
        repairedIssues: qualityLoop.report.repairedIssues,
        remainingIssues: qualityLoop.report.remainingIssues,
        outputRef: { kind: "patch", path: `patches/${input.workItem.id}.patch.json` }
      })
    );
  }
  const contentSha = sha256(qualityLoop.content);
  const finalizedContent = qualityLoop.content.replace("contentSha: pending", `contentSha: ${contentSha}`);

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
