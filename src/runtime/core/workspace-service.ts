export interface WorkspaceRegistration {
  workspaceId: string;
  vaultRoot: string;
  currentRevision: string;
  rulesetVersion: string;
}

export interface PrivateWorkspaceResolution {
  workspaceId: string;
  vaultRoot: string;
  currentRevision: string;
  rulesetVersion: string;
}

export interface PublicWorkspaceMetadata {
  workspaceId: string;
  revision: string;
  rulesetVersion: string;
}

export class WorkspaceService {
  private readonly workspaces = new Map<string, WorkspaceRegistration>();

  constructor(registrations: WorkspaceRegistration[]) {
    for (const registration of registrations) {
      this.workspaces.set(registration.workspaceId, { ...registration });
    }
  }

  resolvePrivateWorkspace(workspaceId: string): PrivateWorkspaceResolution {
    const workspace = this.workspaces.get(workspaceId);
    if (!workspace) {
      throw new Error(`Unknown workspaceId: ${workspaceId}`);
    }
    return { ...workspace };
  }

  getPublicWorkspace(workspaceId: string): PublicWorkspaceMetadata {
    const workspace = this.resolvePrivateWorkspace(workspaceId);
    return {
      workspaceId: workspace.workspaceId,
      revision: workspace.currentRevision,
      rulesetVersion: workspace.rulesetVersion
    };
  }
}
