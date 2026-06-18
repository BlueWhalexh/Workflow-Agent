import type { ArtifactContentView } from "../features/artifacts/artifact-api.js";
import type { RunApprovalView } from "../features/approvals/approval-api.js";
import type { AssistantRunSessionView } from "../features/assistant/run-session.js";
import { loadIntegrationContract } from "../features/ops/integration-contract-api.js";
import { loadWorkspaceBootstrap, type WorkspaceSummaryView } from "../features/workspace/workspace-api.js";
import type { ApiFetch } from "../shared/api/envelope.js";
import { sanitizeForPublicUi } from "../shared/safety/public-fields.js";
import { workbenchFixture } from "./fixtures.js";
import type { TreeItemView, WorkbenchViewModel } from "./types.js";

export type WorkbenchBootstrapStatus = "connected" | "fixture-fallback" | "contract-mismatch";

export type WorkbenchBootstrapView = {
  status: WorkbenchBootstrapStatus;
  statusLabel: string;
  data: WorkbenchViewModel;
};

export async function loadWorkbenchBootstrapView(fetcher: ApiFetch): Promise<WorkbenchBootstrapView> {
  try {
    const contract = await loadIntegrationContract(fetcher);

    if (!contract.frontendReady) {
      return fixtureFallback("contract-mismatch", "后端契约不完整");
    }

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
    return fixtureFallback("fixture-fallback", "离线预览");
  }
}

function fixtureFallback(status: WorkbenchBootstrapStatus, statusLabel: string): WorkbenchBootstrapView {
  return {
    status,
    statusLabel,
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

export function applyAssistantRunProgressToWorkbench(
  data: WorkbenchViewModel,
  session: AssistantRunSessionView,
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
    },
  });
}

export function applyArtifactContentToWorkbench(
  data: WorkbenchViewModel,
  artifact: ArtifactContentView,
): WorkbenchViewModel {
  const publicArtifact = sanitizeForPublicUi(artifact) as ArtifactContentView;

  return publicWorkbench({
    ...data,
    assistant: {
      ...data.assistant,
      approval: {
        ...data.assistant.approval,
        artifact: publicArtifact.artifactRef,
        artifactPreview: {
          title: `${publicArtifact.kind} · ${publicArtifact.redactionStatus}`,
          contentType: publicArtifact.contentType,
          content: publicArtifact.content,
        },
      },
    },
  });
}

export function applyApprovalDecisionToWorkbench(
  data: WorkbenchViewModel,
  approval: RunApprovalView,
): WorkbenchViewModel {
  const publicApproval = sanitizeForPublicUi(approval) as RunApprovalView;
  const decisionSummary = summaryForApprovalDecision(publicApproval.decision);

  return publicWorkbench({
    ...data,
    assistant: {
      ...data.assistant,
      messages: [
        ...data.assistant.messages,
        { author: "安全检查", kind: "ai", text: decisionSummary },
      ],
      approval: {
        ...data.assistant.approval,
        title: titleForApprovalDecision(publicApproval.decision),
        summary: decisionSummary,
        artifact: publicApproval.artifactRef ?? data.assistant.approval.artifact,
        target: publicApproval.targetWorkspacePaths[0] ?? data.assistant.approval.target,
        approvalId: publicApproval.approvalId,
        status: publicApproval.status,
        decision: publicApproval.decision,
      },
    },
  });
}

function titleForApprovalDecision(decision: RunApprovalView["decision"]): string {
  switch (decision) {
    case "APPROVED":
      return "审批已批准";
    case "REJECTED":
      return "审批已拒绝";
    default:
      return "审批状态已更新";
  }
}

function summaryForApprovalDecision(decision: RunApprovalView["decision"]): string {
  switch (decision) {
    case "APPROVED":
      return "审批已批准；这是审批元数据更新，尚未执行候选补丁写入。";
    case "REJECTED":
      return "审批已拒绝；这是审批元数据更新，未执行候选补丁写入。";
    default:
      return "审批状态已更新；这是审批元数据更新，未执行候选补丁写入。";
  }
}
