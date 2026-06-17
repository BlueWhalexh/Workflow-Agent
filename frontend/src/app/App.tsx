import { useEffect, useState } from "react";
import { runAssistantTask } from "../features/assistant/run-session";
import { KnowledgeWorkbench } from "../features/workbench/KnowledgeWorkbench";
import {
  activeWorkspaceIdFromWorkbench,
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
        mode: "deterministic-open-agent",
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

  return (
    <KnowledgeWorkbench
      backendStatusLabel={bootstrap.statusLabel}
      data={bootstrap.data}
      assistantSubmitting={assistantSubmitting}
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
