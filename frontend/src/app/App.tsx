import { useEffect, useState } from "react";
import type { ApprovalDecisionView } from "../features/approvals/approval-api";
import { decideLatestRunApproval, NoPendingApprovalError } from "../features/approvals/approval-flow";
import { listRunArtifacts, readArtifact } from "../features/artifacts/artifact-api";
import { modeForAssistantMessage, runAssistantTask } from "../features/assistant/run-session";
import { KnowledgeWorkbench } from "../features/workbench/KnowledgeWorkbench";
import {
  activeWorkspaceIdFromWorkbench,
  applyApprovalDecisionToWorkbench,
  applyArtifactContentToWorkbench,
  applyAssistantRunSessionToWorkbench,
  loadWorkbenchBootstrapView,
  type WorkbenchBootstrapView,
} from "./bootstrap";
import { workbenchFixture } from "./fixtures";
import type { WorkbenchViewModel } from "./types";

export function App() {
  const [bootstrap, setBootstrap] = useState<WorkbenchBootstrapView>({
    status: "fixture-fallback",
    statusLabel: "离线预览",
    data: workbenchFixture,
  });
  const [assistantSubmitting, setAssistantSubmitting] = useState(false);
  const [assistantApprovalDeciding, setAssistantApprovalDeciding] = useState(false);
  const [assistantArtifactReading, setAssistantArtifactReading] = useState(false);

  useEffect(() => {
    let mounted = true;
    void loadWorkbenchBootstrapView(window.fetch.bind(window)).then((nextBootstrap) => {
      if (mounted) {
        setBootstrap(nextBootstrap);
      }
    });

    return () => {
      mounted = false;
    };
  }, []);

  async function handleAssistantSubmit(userMessage: string) {
    const workspaceId = activeWorkspaceIdFromWorkbench(bootstrap.data);
    if (!workspaceId || bootstrap.status !== "connected") {
      setBootstrap((current) => ({
        ...current,
        data: appendAssistantMessage(current.data, {
          author: "安全检查",
          kind: "ai",
          text: "后端未连接或暂无可用工作区，当前不会发起运行。",
        }),
      }));
      return;
    }

    setAssistantSubmitting(true);
    try {
      const session = await runAssistantTask(window.fetch.bind(window), {
        workspaceId,
        userMessage,
        mode: modeForAssistantMessage(userMessage),
      });
      setBootstrap((current) => ({
        ...current,
        data: applyAssistantRunSessionToWorkbench(current.data, session, userMessage),
      }));
    } catch {
      setBootstrap((current) => ({
        ...current,
        data: appendAssistantMessage(current.data, {
          author: "安全检查",
          kind: "ai",
          text: "运行请求失败，请稍后重试或检查后端连接。",
        }),
      }));
    } finally {
      setAssistantSubmitting(false);
    }
  }

  async function handleReadArtifact() {
    const runId = bootstrap.data.assistant.run.id;
    const artifactRef = bootstrap.data.assistant.approval.artifact;
    if (bootstrap.status !== "connected" || !runId || artifactRef === "无") {
      setBootstrap((current) => ({
        ...current,
        data: appendAssistantMessage(current.data, {
          author: "安全检查",
          kind: "ai",
          text: "后端未连接或当前运行没有可读取的产物。",
        }),
      }));
      return;
    }

    setAssistantArtifactReading(true);
    try {
      const artifacts = await listRunArtifacts(window.fetch.bind(window), runId);
      const selectedArtifact = artifacts.find((artifact) => artifact.artifactRef === artifactRef) ?? artifacts[0];
      if (!selectedArtifact) {
        setBootstrap((current) => ({
          ...current,
          data: appendAssistantMessage(current.data, {
            author: "安全检查",
            kind: "ai",
            text: "当前运行没有返回可读取的产物。",
          }),
        }));
        return;
      }

      const artifact = await readArtifact(window.fetch.bind(window), selectedArtifact.artifactId);
      setBootstrap((current) => ({
        ...current,
        data: applyArtifactContentToWorkbench(current.data, artifact),
      }));
    } catch {
      setBootstrap((current) => ({
        ...current,
        data: appendAssistantMessage(current.data, {
          author: "安全检查",
          kind: "ai",
          text: "读取产物失败，请稍后重试或检查后端连接。",
        }),
      }));
    } finally {
      setAssistantArtifactReading(false);
    }
  }

  async function handleApprovalDecision(decision: ApprovalDecisionView) {
    const runId = bootstrap.data.assistant.run.id;
    if (bootstrap.status !== "connected" || !runId) {
      setBootstrap((current) => ({
        ...current,
        data: appendAssistantMessage(current.data, {
          author: "安全检查",
          kind: "ai",
          text: "后端未连接或当前没有可审批的运行。",
        }),
      }));
      return;
    }

    setAssistantApprovalDeciding(true);
    try {
      const approval = await decideLatestRunApproval(window.fetch.bind(window), runId, decision);
      setBootstrap((current) => ({
        ...current,
        data: applyApprovalDecisionToWorkbench(current.data, approval),
      }));
    } catch (error) {
      const text = error instanceof NoPendingApprovalError
        ? "当前运行没有待处理审批，未提交审批决策。"
        : "提交审批决策失败，请稍后重试或检查后端连接。";
      setBootstrap((current) => ({
        ...current,
        data: appendAssistantMessage(current.data, {
          author: "安全检查",
          kind: "ai",
          text,
        }),
      }));
    } finally {
      setAssistantApprovalDeciding(false);
    }
  }

  return (
    <KnowledgeWorkbench
      backendStatusLabel={bootstrap.statusLabel}
      data={bootstrap.data}
      assistantApprovalDeciding={assistantApprovalDeciding}
      assistantArtifactReading={assistantArtifactReading}
      assistantSubmitting={assistantSubmitting}
      onAssistantDecideApproval={handleApprovalDecision}
      onAssistantReadArtifact={handleReadArtifact}
      onAssistantSubmit={handleAssistantSubmit}
    />
  );
}

function appendAssistantMessage(
  data: WorkbenchViewModel,
  message: WorkbenchViewModel["assistant"]["messages"][number],
): WorkbenchViewModel {
  return {
    ...data,
    assistant: {
      ...data.assistant,
      messages: [...data.assistant.messages, message],
    },
  };
}
