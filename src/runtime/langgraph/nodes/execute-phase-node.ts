import { runMockNoteAgent } from "../../../agents/mock-note-agent.js";
import { runMockTopicIndexAgent } from "../../../agents/mock-topic-index-agent.js";
import { runQualityReviewAgent } from "../../../agents/quality-review-agent.js";
import { classifyProviderError, workItemStatusForProviderError } from "../../../domain/llm-provider/provider-error.js";
import type { OrganizePlan } from "../../../domain/planning/plan.js";
import type { WorkItem } from "../../../domain/planning/work-item.js";
import type { PatchBundle } from "../../../domain/patch/patch-bundle.js";
import { checkMerge } from "../../../domain/patch/merge-guard.js";
import { publishBundle } from "../../../domain/patch/publisher.js";
import { validateBundle } from "../../../domain/validation/validator.js";
import { inspectResumeWorkItem } from "../../../domain/validation/resume-inspector.js";
import { appendLlmTraceEvent } from "../../../domain/llm-trace/trace-writer.js";
import { AgentRunsStore } from "../../../storage/agent-runs-store.js";
import { sha256 } from "../../../storage/sha.js";
import { readWorkspaceFile } from "../../../storage/workspace-fs.js";
import {
  buildLoopReport,
  budgetForWorkItemType,
  isWorkItemAgentLoopReport,
  loopBudgetExceeded,
  outputRefForWorkItem,
  writeLoopReport
} from "../../../agents/work-item-agent-runtime.js";
import {
  selectNoteProvider,
  traceProviderForRuntime,
  type ProviderRuntimeDependencies
} from "../../provider/provider-registry.js";
import type { GraphState } from "../state.js";

async function readStoredWorkItem(store: AgentRunsStore, workItemId: string): Promise<WorkItem | null> {
  return store.readJson<WorkItem>(`work-items/${workItemId}.json`).catch((error: unknown) => {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    throw error;
  });
}

async function inspectRuntimeResume(input: {
  state: GraphState;
  store: AgentRunsStore;
  workItem: WorkItem;
}): Promise<"SKIP" | "NEEDS_REPLAN" | "RUN"> {
  const storedWorkItem = await readStoredWorkItem(input.store, input.workItem.id);
  if (!storedWorkItem) {
    return "RUN";
  }

  const patch = await input.store.readJson<PatchBundle>(`patches/${input.workItem.id}.patch.json`).catch((error: unknown) => {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    throw error;
  });
  const latestAttempt = storedWorkItem.attempts.at(-1);
  const decision = await inspectResumeWorkItem({
    workspaceRoot: input.state.workspaceRoot,
    workItem: {
      id: storedWorkItem.id,
      status: storedWorkItem.status,
      targetPaths: storedWorkItem.targetPaths,
      contentSha: patch?.files[0]?.contentSha,
      retryable: latestAttempt?.retryable
    }
  });
  if (decision.action === "SKIP") {
    return "SKIP";
  }
  if (decision.action === "REPLAN") {
    await input.store.writeJson(`work-items/${input.workItem.id}.json`, {
      ...storedWorkItem,
      status: "NEEDS_REPLAN"
    });
    return "NEEDS_REPLAN";
  }
  return "RUN";
}

async function inspectLoopReportGate(input: {
  store: AgentRunsStore;
  runId: string;
  workItem: WorkItem;
}): Promise<{ allowed: true } | { allowed: false; reason: string }> {
  const report = await input.store.readJson<unknown>(`agent-loop/${input.workItem.id}.json`).catch((error: unknown) => {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    throw error;
  });
  if (!isWorkItemAgentLoopReport(report)) {
    return { allowed: false, reason: "LOOP_REPORT_SCHEMA_INVALID" };
  }
  if (
    report.runId !== input.runId ||
    report.workItemId !== input.workItem.id ||
    report.workItemType !== input.workItem.type
  ) {
    return { allowed: false, reason: "LOOP_REPORT_CONTEXT_MISMATCH" };
  }
  if (loopBudgetExceeded(report)) {
    return { allowed: false, reason: "LOOP_BUDGET_EXCEEDED" };
  }
  const expectedOutputRef = outputRefForWorkItem(input.workItem);
  if (
    !report.outputRef ||
    report.outputRef.kind !== expectedOutputRef?.kind ||
    report.outputRef.path !== expectedOutputRef.path
  ) {
    return { allowed: false, reason: "LOOP_OUTPUT_REF_INVALID" };
  }
  return { allowed: true };
}

async function failLoopGate(input: {
  store: AgentRunsStore;
  workItem: WorkItem;
  previousAttempts: WorkItem["attempts"];
  reason: string;
}): Promise<void> {
  await input.store.writeJson(`work-items/${input.workItem.id}.json`, {
    ...input.workItem,
    status: "FAILED_EXECUTOR",
    attempts: [
      ...input.previousAttempts,
      {
        attempt: input.previousAttempts.length + 1,
        status: "FAILED_EXECUTOR",
        message: input.reason,
        failureSource: "loop",
        failureReason: input.reason,
        retryable: false
      }
    ]
  });
}

async function runPatchWorkItem(input: {
  state: GraphState;
  store: AgentRunsStore;
  workItem: WorkItem;
  topicIndexPaths?: string[];
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}): Promise<{ failed: boolean; lastError?: string }> {
  const resumeDecision = await inspectRuntimeResume(input);
  if (resumeDecision === "SKIP") {
    return { failed: false };
  }
  if (resumeDecision === "NEEDS_REPLAN") {
    return { failed: true, lastError: "WORK_ITEM_NEEDS_REPLAN" };
  }

  const provider = selectNoteProvider(input.state.providerRuntime, input.providerRuntimeDependencies);
  const storedWorkItem = await readStoredWorkItem(input.store, input.workItem.id);
  const previousAttempts = storedWorkItem?.attempts ?? input.workItem.attempts;
  let providerFailureLastError: string | undefined;
  let bundle: PatchBundle | null = null;

  if (input.workItem.type === "CREATE_TOPIC_NOTE" || input.workItem.type === "REWRITE_TOPIC_NOTE") {
    const sourceContent = await readWorkspaceFile(input.state.workspaceRoot, input.workItem.sourcePaths[0]);
    bundle = await runMockNoteAgent({
      runId: input.state.runId,
      workItem: input.workItem,
      sourceContent,
      store: input.store,
      provider
    }).catch(async (error: unknown) => {
      const classification = classifyProviderError(error);
      providerFailureLastError = `PROVIDER_${classification.errorClass.toUpperCase()}`;
      const failedStatus = workItemStatusForProviderError(classification);
      const providerCallId = `${input.workItem.id}:provider-failed`;
      await appendLlmTraceEvent(input.store, input.workItem.id, {
        schemaVersion: "llm-trace.v1",
        type: "llm.call.failed",
        eventId: `${providerCallId}:failed`,
        runId: input.state.runId,
        workItemId: input.workItem.id,
        agentNode: "note",
        providerCallId,
        provider: traceProviderForRuntime(input.state.providerRuntime),
        model: input.state.providerRuntime?.model ?? input.state.providerRuntime?.provider ?? "fake",
        timestamp: new Date(0).toISOString(),
        errorClass: classification.errorClass,
        retryable: classification.retryable,
        message: classification.message
      });
      await input.store.writeJson(`work-items/${input.workItem.id}.json`, {
        ...input.workItem,
        status: failedStatus,
        attempts: [
          ...previousAttempts,
          {
            attempt: previousAttempts.length + 1,
            status: failedStatus,
            message: classification.message,
            failureSource: "provider",
            failureReason: classification.errorClass,
            retryable: classification.retryable
          }
        ]
      });
      return null;
    });
  } else if (input.workItem.type === "MAINTAIN_TOPIC_INDEX") {
    bundle = await runMockTopicIndexAgent({
      runId: input.state.runId,
      workItem: input.workItem,
      sourceContent: "",
      store: input.store
    });
  } else if (input.workItem.type === "MAINTAIN_MOC") {
    const targetPath = input.workItem.targetPaths[0];
    const topicLinks = (input.topicIndexPaths ?? [])
      .map((topicIndexPath) => {
        const topicSlug = topicIndexPath
          .replace(/^knowledge-base\/topics\//, "")
          .replace(/\/index\.md$/, "");
        const topicLabel = topicSlug.split("/").at(-1) ?? topicSlug;
        return `- [${topicLabel}](topics/${topicSlug}/index.md)`;
      })
      .sort()
      .join("\n");
    const content = `# MOC

${topicLinks}
`;
    bundle = {
      workItemId: input.workItem.id,
      status: "SUCCEEDED",
      targetPaths: [targetPath],
      files: [
        {
          path: targetPath,
          changeType: "MODIFIED",
          baseSha: input.workItem.baseShas[targetPath] ?? null,
          contentSha: sha256(content),
          content
        }
      ],
      eval: {
        rawFilesSeen: [],
        rawMirrorConverted: false,
        placeholderIntroduced: false,
        wikilinksCreated: input.topicIndexPaths?.length ?? 0
      }
    };
    await writeLoopReport(
      input.store,
      buildLoopReport({
        runId: input.state.runId,
        workItemId: input.workItem.id,
        workItemType: input.workItem.type,
        agentNode: "moc",
        status: "SUCCEEDED",
        budget: budgetForWorkItemType(input.workItem.type),
        usage: { iterations: 1, providerCalls: 0 },
        steps: [
          {
            name: "DRAFT_MOC",
            kind: "draft",
            status: "SUCCEEDED",
            issues: [],
            repairedIssues: []
          }
        ],
        repairedIssues: [],
        remainingIssues: [],
        outputRef: { kind: "patch", path: `patches/${input.workItem.id}.patch.json` }
      })
    );
  }

  if (!bundle) {
    return {
      failed: true,
      lastError: providerFailureLastError ?? `UNSUPPORTED_WORK_ITEM:${input.workItem.type}`
    };
  }

  const loopGate = await inspectLoopReportGate({
    store: input.store,
    runId: input.state.runId,
    workItem: input.workItem
  });
  if (!loopGate.allowed) {
    await failLoopGate({
      store: input.store,
      workItem: input.workItem,
      previousAttempts,
      reason: loopGate.reason
    });
    return { failed: true, lastError: loopGate.reason };
  }

  await input.store.writeJson(`patches/${input.workItem.id}.patch.json`, bundle);

  const mergeDecision = await checkMerge({
    workspaceRoot: input.state.workspaceRoot,
    authorizedTargetPaths: input.workItem.targetPaths,
    bundle
  });
  const validation = validateBundle({
    targetPaths: input.workItem.targetPaths,
    files: bundle.files,
    methodologyId: input.workItem.methodologyId
  });

  await input.store.writeJson(`validation/${input.workItem.id}.json`, { mergeDecision, validation });
  await input.store.writeJson("validation.json", { mergeDecision, validation });
  if (!mergeDecision.allowed || !validation.allowed) {
    const blockedStatus = validation.allowed ? "FAILED_EXECUTOR" : "BLOCKED_BY_VALIDATOR";
    await input.store.writeJson(`work-items/${input.workItem.id}.json`, {
      ...input.workItem,
      status: blockedStatus,
      attempts: [
        ...previousAttempts,
          {
            attempt: previousAttempts.length + 1,
            status: blockedStatus,
            message: validation.allowed ? "merge guard blocked patch" : "validator blocked patch",
            failureSource: validation.allowed ? "merge" : "validator",
            failureReason: validation.allowed ? "MERGE_GUARD_BLOCKED" : validation.hardBlockers[0] ?? "VALIDATOR_BLOCKED",
            retryable: false
          }
        ]
      });
    return { failed: true, lastError: "PATCH_BLOCKED" };
  }

  await publishBundle({ workspaceRoot: input.state.workspaceRoot, bundle });
  await input.store.writeJson(`work-items/${input.workItem.id}.json`, {
    ...input.workItem,
    status: "PUBLISHED",
    attempts: [
      ...previousAttempts,
      {
        attempt: previousAttempts.length + 1,
        status: "PUBLISHED",
        message: "published"
      }
    ]
  });

  return { failed: false };
}

async function runQualityReviewWorkItem(input: {
  state: GraphState;
  store: AgentRunsStore;
  workItem: WorkItem;
  noteTargetPaths: string[];
}): Promise<{ failed: boolean; lastError?: string }> {
  const storedWorkItem = await readStoredWorkItem(input.store, input.workItem.id);
  const previousAttempts = storedWorkItem?.attempts ?? input.workItem.attempts;
  const noteContents = await Promise.all(
    input.noteTargetPaths.map((targetPath) => readWorkspaceFile(input.state.workspaceRoot, targetPath))
  );
  const findings = await runQualityReviewAgent({ noteContents });
  await writeLoopReport(
    input.store,
    buildLoopReport({
      runId: input.state.runId,
      workItemId: input.workItem.id,
      workItemType: input.workItem.type,
      agentNode: "quality-review",
      status: findings.issues.length === 0 ? "SUCCEEDED" : "SUCCEEDED_WITH_WARNINGS",
      budget: budgetForWorkItemType(input.workItem.type),
      usage: { iterations: 1, providerCalls: 0 },
      steps: [
        {
          name: "REVIEW_PUBLISHED_NOTES",
          kind: "validate",
          status: "SUCCEEDED",
          issues: findings.issues,
          repairedIssues: []
        }
      ],
      repairedIssues: [],
      remainingIssues: findings.issues,
      outputRef: { kind: "quality", path: `quality/${input.workItem.id}.json` }
    })
  );
  const loopGate = await inspectLoopReportGate({
    store: input.store,
    runId: input.state.runId,
    workItem: input.workItem
  });
  if (!loopGate.allowed) {
    await failLoopGate({
      store: input.store,
      workItem: input.workItem,
      previousAttempts,
      reason: loopGate.reason
    });
    return { failed: true, lastError: loopGate.reason };
  }
  await input.store.writeJson(`quality/${input.workItem.id}.json`, findings);
  await input.store.writeJson(`work-items/${input.workItem.id}.json`, {
    ...input.workItem,
    status: "SUCCEEDED",
    attempts: [
      ...previousAttempts,
      {
        attempt: previousAttempts.length + 1,
        status: "SUCCEEDED",
        message: "quality review completed"
      }
    ]
  });
  return { failed: false };
}

export async function executePhaseNode(
  state: GraphState,
  providerRuntimeDependencies?: ProviderRuntimeDependencies
): Promise<Partial<GraphState>> {
  if (!state.autoApprove) {
    return {};
  }

  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const plan = await store.readJson<OrganizePlan>("plan.json");
  const noteItems = plan.workItems.filter((item) => item.phase === "phase-a-notes");
  const indexItems = plan.workItems.filter((item) => item.phase === "phase-b-indexes");
  const mocItems = plan.workItems.filter((item) => item.type === "MAINTAIN_MOC");
  const qualityItems = plan.workItems.filter((item) => item.type === "QUALITY_REVIEW");
  if (noteItems.length + indexItems.length + mocItems.length + qualityItems.length === 0) {
    return { status: "FAILED", lastError: "NO_EXECUTABLE_WORK_ITEM" };
  }

  const noteResults = [];
  for (const workItem of noteItems) {
    noteResults.push(await runPatchWorkItem({ state, store, workItem, providerRuntimeDependencies }));
  }
  const failedNote = noteResults.find((result) => result.failed);
  if (failedNote) {
    return { status: "FAILED", lastError: failedNote.lastError ?? "EXECUTE_PHASE_FAILED" };
  }

  const indexResults = [];
  for (const workItem of indexItems) {
    indexResults.push(await runPatchWorkItem({ state, store, workItem, providerRuntimeDependencies }));
  }
  const failedIndex = indexResults.find((result) => result.failed);
  if (failedIndex) {
    return { status: "FAILED", lastError: failedIndex.lastError ?? "EXECUTE_PHASE_FAILED" };
  }

  const topicIndexPaths = indexItems.flatMap((item) => item.targetPaths);
  const mocResults = [];
  for (const workItem of mocItems) {
    mocResults.push(await runPatchWorkItem({ state, store, workItem, topicIndexPaths, providerRuntimeDependencies }));
  }
  const failedMoc = mocResults.find((result) => result.failed);
  if (failedMoc) {
    return { status: "FAILED", lastError: failedMoc.lastError ?? "EXECUTE_PHASE_FAILED" };
  }

  const noteTargetPaths = noteItems.flatMap((item) => item.targetPaths);
  const qualityResults = [];
  for (const workItem of qualityItems) {
    qualityResults.push(await runQualityReviewWorkItem({ state, store, workItem, noteTargetPaths }));
  }
  const failedQuality = qualityResults.find((result) => result.failed);
  if (failedQuality) {
    return { status: "FAILED", lastError: failedQuality.lastError ?? "EXECUTE_PHASE_FAILED" };
  }

  return {};
}
