import type { AssistantRunSessionView } from "../features/assistant/run-session.js";
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

export function activeWorkspaceIdFromWorkbench(data: WorkbenchViewModel): string | null {
  return data.treeItems.find((item) => item.active)?.id ?? data.treeItems[0]?.id ?? null;
}

export function applyAssistantRunSessionToWorkbench(
  data: WorkbenchViewModel,
  session: AssistantRunSessionView,
  userMessage: string,
): WorkbenchViewModel {
  return publicWorkbench({
    ...data,
    assistant: {
      ...data.assistant,
      run: {
        title: session.title,
        id: session.runId,
        progress: session.progress,
        events: session.events,
      },
      messages: [
        ...data.assistant.messages,
        { author: "你", kind: "user", text: userMessage },
        { author: "助手", kind: "ai", text: session.displayText ?? session.title },
      ],
      approval: {
        title: session.approval.status === "PENDING" ? "审批草稿" : "运行结果",
        summary: session.displayText ?? session.title,
        artifact: session.approval.artifactRefs[0] ?? "无",
        target: session.approval.targetWorkspacePaths[0] ?? "无",
        wroteWorkspace: session.approval.wroteWorkspace,
      },
    },
  });
}
