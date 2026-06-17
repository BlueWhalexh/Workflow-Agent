import { loadWorkspaceBootstrap, type WorkspaceSummaryView } from "../features/workspace/workspace-api.js";
import type { ApiFetch } from "../shared/api/envelope.js";
import { sanitizeForPublicUi } from "../shared/safety/public-fields.js";
import { workbenchFixture } from "./fixtures.js";
import type { TreeItemView, WorkbenchViewModel } from "./types.js";

export type WorkbenchBootstrapStatus = "connected" | "fixture-fallback";

export type WorkbenchBootstrapView = {
  status: WorkbenchBootstrapStatus;
  statusLabel: string;
  data: WorkbenchViewModel;
};

export async function loadWorkbenchBootstrapView(fetcher: ApiFetch): Promise<WorkbenchBootstrapView> {
  try {
    const bootstrap = await loadWorkspaceBootstrap(fetcher);
    const selectedWorkspace = bootstrap.workspaces.find((workspace) => workspace.id === bootstrap.selectedWorkspaceId);

    if (!selectedWorkspace) {
      return {
        status: "connected",
        statusLabel: "后端已连接",
        data: publicWorkbench({
          ...workbenchFixture,
          workspaceName: "暂无工作区",
          breadcrumb: ["暂无工作区", ...workbenchFixture.breadcrumb.slice(1)],
          treeItems: [],
        }),
      };
    }

    return {
      status: "connected",
      statusLabel: "后端已连接",
      data: publicWorkbench({
        ...workbenchFixture,
        workspaceName: selectedWorkspace.name,
        breadcrumb: [selectedWorkspace.name, ...workbenchFixture.breadcrumb.slice(1)],
        treeItems: workspaceTreeItems(bootstrap.workspaces, selectedWorkspace.id),
      }),
    };
  } catch {
    return fixtureFallback();
  }
}

function fixtureFallback(): WorkbenchBootstrapView {
  return {
    status: "fixture-fallback",
    statusLabel: "离线预览",
    data: publicWorkbench(workbenchFixture),
  };
}

function workspaceTreeItems(workspaces: WorkspaceSummaryView[], selectedWorkspaceId: string): TreeItemView[] {
  return workspaces.map((workspace, index) => ({
    id: workspace.id,
    icon: index === 0 ? "▾" : "•",
    label: workspace.name,
    count: workspace.status,
    depth: index > 0 || undefined,
    active: workspace.id === selectedWorkspaceId || undefined,
  }));
}

function publicWorkbench(data: WorkbenchViewModel): WorkbenchViewModel {
  return sanitizeForPublicUi(data) as WorkbenchViewModel;
}
