import { type ApiFetch, requestApiJson } from "../../shared/api/envelope.js";

type BackendMeResponse = {
  userId: string;
  teamId: string;
  displayName: string;
};

type BackendWorkspaceResponse = {
  workspaceId: string;
  name: string;
  defaultBranch: string;
  status: string;
};

export type PrincipalView = {
  userId: string;
  teamId: string;
  displayName: string;
};

export type WorkspaceSummaryView = {
  id: string;
  name: string;
  defaultBranch: string;
  status: string;
};

export type WorkspaceBootstrapView = {
  principal: PrincipalView;
  workspaces: WorkspaceSummaryView[];
  selectedWorkspaceId?: string;
};

export async function loadWorkspaceBootstrap(fetcher: ApiFetch): Promise<WorkspaceBootstrapView> {
  const [principal, workspaces] = await Promise.all([
    requestApiJson<BackendMeResponse>(fetcher, "/v1/me"),
    requestApiJson<BackendWorkspaceResponse[]>(fetcher, "/v1/workspaces"),
  ]);

  const mappedWorkspaces = workspaces.map(toWorkspaceSummary);

  return {
    principal: {
      userId: principal.userId,
      teamId: principal.teamId,
      displayName: principal.displayName,
    },
    workspaces: mappedWorkspaces,
    selectedWorkspaceId: mappedWorkspaces[0]?.id,
  };
}

function toWorkspaceSummary(workspace: BackendWorkspaceResponse): WorkspaceSummaryView {
  return {
    id: workspace.workspaceId,
    name: workspace.name,
    defaultBranch: workspace.defaultBranch,
    status: workspace.status,
  };
}
