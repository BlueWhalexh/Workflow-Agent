import { type ApiFetch, requestApiJson } from "../../shared/api/envelope.js";
import { sanitizeForPublicUi } from "../../shared/safety/public-fields.js";

export type CreateAgentRunInput = {
  userMessage: string;
  mode?: string;
  execute?: boolean;
  autoApprove?: boolean;
  providerRuntimeRef?: string;
  remoteRunnerRef?: string;
};

type BackendRunResponse = {
  runId: string;
  workspaceId: string;
  status: string;
  outputKind: string;
  displayText?: string | null;
  requiresConfirmation: boolean;
  requiresApproval: boolean;
  artifactRefs: string[];
  wroteWorkspace: boolean;
  targetWorkspacePaths: string[];
  errorCode?: string | null;
  createdAt: string;
  updatedAt: string;
};

type BackendRunEventResponse = {
  eventId: string;
  runId: string;
  eventType: string;
  status: string;
  message: string;
  createdAt: string;
};

export type AgentRunView = {
  runId: string;
  workspaceId: string;
  status: string;
  outputKind: string;
  displayText?: string | null;
  requiresConfirmation: boolean;
  requiresApproval: boolean;
  artifactRefs: string[];
  wroteWorkspace: boolean;
  targetWorkspacePaths: string[];
  errorCode?: string;
  createdAt: string;
  updatedAt: string;
};

export type RunEventView = {
  eventId: string;
  runId: string;
  eventType: string;
  status: string;
  message: string;
  createdAt: string;
};

export async function createAgentRun(
  fetcher: ApiFetch,
  workspaceId: string,
  input: CreateAgentRunInput,
): Promise<AgentRunView> {
  return toAgentRunView(await requestApiJson<BackendRunResponse>(
    fetcher,
    `/v1/workspaces/${encodeURIComponent(workspaceId)}/agent-runs`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    },
  ));
}

export async function getAgentRun(fetcher: ApiFetch, runId: string): Promise<AgentRunView> {
  return toAgentRunView(await requestApiJson<BackendRunResponse>(
    fetcher,
    `/v1/agent-runs/${encodeURIComponent(runId)}`,
  ));
}

export async function listWorkspaceRuns(fetcher: ApiFetch, workspaceId: string): Promise<AgentRunView[]> {
  const runs = await requestApiJson<BackendRunResponse[]>(
    fetcher,
    `/v1/workspaces/${encodeURIComponent(workspaceId)}/agent-runs`,
  );
  return runs.map(toAgentRunView);
}

export async function cancelAgentRun(fetcher: ApiFetch, runId: string): Promise<AgentRunView> {
  return toAgentRunView(await requestApiJson<BackendRunResponse>(
    fetcher,
    `/v1/agent-runs/${encodeURIComponent(runId)}/cancel`,
    {
      method: "POST",
    },
  ));
}

export async function listRunEvents(fetcher: ApiFetch, runId: string): Promise<RunEventView[]> {
  const events = await requestApiJson<BackendRunEventResponse[]>(
    fetcher,
    `/v1/agent-runs/${encodeURIComponent(runId)}/events`,
  );
  return events.map(toRunEventView);
}

function toAgentRunView(run: BackendRunResponse): AgentRunView {
  const publicRun = sanitizeForPublicUi(run) as BackendRunResponse;
  return {
    runId: publicRun.runId,
    workspaceId: publicRun.workspaceId,
    status: publicRun.status,
    outputKind: publicRun.outputKind,
    displayText: publicRun.displayText,
    requiresConfirmation: publicRun.requiresConfirmation,
    requiresApproval: publicRun.requiresApproval,
    artifactRefs: publicRun.artifactRefs,
    wroteWorkspace: publicRun.wroteWorkspace,
    targetWorkspacePaths: publicRun.targetWorkspacePaths,
    errorCode: publicRun.errorCode ?? undefined,
    createdAt: publicRun.createdAt,
    updatedAt: publicRun.updatedAt,
  };
}

function toRunEventView(event: BackendRunEventResponse): RunEventView {
  const publicEvent = sanitizeForPublicUi(event) as BackendRunEventResponse;
  return {
    eventId: publicEvent.eventId,
    runId: publicEvent.runId,
    eventType: publicEvent.eventType,
    status: publicEvent.status,
    message: publicEvent.message,
    createdAt: publicEvent.createdAt,
  };
}
