import type { ApiFetch } from "../../shared/api/envelope.js";
import {
  listRunArtifacts,
  readArtifact,
  type ArtifactContentView,
} from "../artifacts/artifact-api.js";
import {
  createAgentRun,
  getAgentRun,
  listRunEvents,
  type AgentRunView,
  type CreateAgentRunInput,
  type RunEventView,
} from "../runs/run-api.js";
import { streamRunEvents } from "../runs/run-event-stream.js";

export type AssistantRunTaskInput = CreateAgentRunInput & {
  workspaceId: string;
  maxPolls?: number;
  onUpdate?: (session: AssistantRunSessionView) => void | Promise<void>;
  pollDelayMs?: number;
  streamEvents?: boolean;
};

export type AssistantRunSessionView = {
  runId: string;
  status: string;
  terminal: boolean;
  title: string;
  progress: number;
  displayText?: string | null;
  events: Array<{
    time: string;
    label: string;
  }>;
  approval: {
    status: "NONE" | "PENDING";
    artifactRefs: string[];
    targetWorkspacePaths: string[];
    wroteWorkspace: boolean;
  };
  artifacts: AssistantRunArtifactView[];
};

export type AssistantRunArtifactView = {
  artifactId: string;
  artifactRef: string;
  kind: string;
  contentType: string;
  content: string;
};

const DEFAULT_MAX_POLLS = 20;
const DEFAULT_POLL_DELAY_MS = 250;
const WRITE_INTENT_PATTERN = /落库|写入|保存|发布|导入|publish|persist|write|import/i;

export function modeForAssistantMessage(userMessage: string): string {
  return WRITE_INTENT_PATTERN.test(userMessage) ? "llm-open-agent" : "deterministic-open-agent";
}

export async function runAssistantTask(
  fetcher: ApiFetch,
  input: AssistantRunTaskInput,
): Promise<AssistantRunSessionView> {
  const createdRun = await createAgentRun(fetcher, input.workspaceId, {
    userMessage: input.userMessage,
    mode: input.mode,
    execute: input.execute,
    autoApprove: input.autoApprove,
    providerRuntimeRef: input.providerRuntimeRef,
    remoteRunnerRef: input.remoteRunnerRef,
  });
  const streamedEvents = input.streamEvents
    ? await streamRunEventsForSession(fetcher, createdRun, input.onUpdate)
    : [];
  const finalRun = await pollRun(
    fetcher,
    createdRun,
    input.maxPolls ?? DEFAULT_MAX_POLLS,
    input.pollDelayMs ?? DEFAULT_POLL_DELAY_MS,
  );
  const events = streamedEvents.length > 0 ? streamedEvents : await listRunEvents(fetcher, finalRun.runId);
  const artifacts = await loadRunArtifactPreviews(fetcher, finalRun);
  return toSessionView(finalRun, events, artifacts);
}

async function streamRunEventsForSession(
  fetcher: ApiFetch,
  createdRun: AgentRunView,
  onUpdate: AssistantRunTaskInput["onUpdate"],
): Promise<RunEventView[]> {
  const streamedEvents: RunEventView[] = [];
  return streamRunEvents(fetcher, createdRun.runId, {
    onEvent: async (event) => {
      streamedEvents.push(event);
      await onUpdate?.(toSessionView(runFromEvent(createdRun, event), streamedEvents));
    },
  });
}

function runFromEvent(run: AgentRunView, event: RunEventView): AgentRunView {
  return {
    ...run,
    status: event.status,
    updatedAt: event.createdAt,
  };
}

async function pollRun(
  fetcher: ApiFetch,
  createdRun: AgentRunView,
  maxPolls: number,
  pollDelayMs: number,
): Promise<AgentRunView> {
  let currentRun = createdRun;
  for (let pollIndex = 0; pollIndex < maxPolls && !isTerminalStatus(currentRun.status); pollIndex += 1) {
    if (pollIndex > 0 && pollDelayMs > 0) {
      await delay(pollDelayMs);
    }
    currentRun = await getAgentRun(fetcher, currentRun.runId);
  }
  return currentRun;
}

function delay(delayMs: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, delayMs));
}

async function loadRunArtifactPreviews(fetcher: ApiFetch, run: AgentRunView): Promise<AssistantRunArtifactView[]> {
  if (run.status !== "SUCCEEDED" || run.artifactRefs.length === 0) {
    return [];
  }

  const artifacts = await listRunArtifacts(fetcher, run.runId);
  const selectedArtifacts = artifacts.filter((artifact) => run.artifactRefs.includes(artifact.artifactRef));
  return Promise.all(selectedArtifacts.map((artifact) => readArtifact(fetcher, artifact.artifactId).then(toArtifactPreview)));
}

function toArtifactPreview(artifact: ArtifactContentView): AssistantRunArtifactView {
  return {
    artifactId: artifact.artifactId,
    artifactRef: artifact.artifactRef,
    kind: artifact.kind,
    contentType: artifact.contentType,
    content: artifact.content,
  };
}

function toSessionView(
  run: AgentRunView,
  events: RunEventView[],
  artifacts: AssistantRunArtifactView[] = [],
): AssistantRunSessionView {
  return {
    runId: run.runId,
    status: run.status,
    terminal: isTerminalStatus(run.status),
    title: titleForStatus(run.status),
    progress: progressForStatus(run.status),
    displayText: run.displayText,
    events: events.map(toSessionEvent),
    approval: {
      status: run.requiresApproval ? "PENDING" : "NONE",
      artifactRefs: run.artifactRefs,
      targetWorkspacePaths: run.targetWorkspacePaths,
      wroteWorkspace: run.wroteWorkspace,
    },
    artifacts,
  };
}

function toSessionEvent(event: RunEventView) {
  return {
    time: event.createdAt.slice(11, 19),
    label: event.message,
  };
}

function isTerminalStatus(status: string) {
  return !["QUEUED", "RUNNING"].includes(status);
}

function titleForStatus(status: string) {
  switch (status) {
    case "QUEUED":
      return "Run 已排队";
    case "RUNNING":
      return "Run 正在执行";
    case "WAITING_APPROVAL":
      return "Run 正在等待审批";
    case "SUCCEEDED":
      return "Run 已完成";
    case "FAILED":
      return "Run 执行失败";
    case "CANCELLED":
      return "Run 已取消";
    default:
      return `Run 状态：${status}`;
  }
}

function progressForStatus(status: string) {
  switch (status) {
    case "QUEUED":
      return 15;
    case "RUNNING":
      return 55;
    case "WAITING_APPROVAL":
      return 72;
    case "SUCCEEDED":
      return 100;
    case "FAILED":
    case "CANCELLED":
      return 100;
    default:
      return 0;
  }
}
