import type { WorkbenchViewModel } from "../../app/types";
import { ArticleView } from "./ArticleView";
import { AssistantPanel } from "./AssistantPanel";
import { Sidebar } from "./Sidebar";

type KnowledgeWorkbenchProps = {
  backendStatusLabel: string;
  data: WorkbenchViewModel;
  assistantArtifactReading?: boolean;
  assistantSubmitting?: boolean;
  onAssistantReadArtifact?: () => void | Promise<void>;
  onAssistantSubmit?: (userMessage: string) => void | Promise<void>;
};

export function KnowledgeWorkbench({
  backendStatusLabel,
  data,
  assistantArtifactReading,
  assistantSubmitting,
  onAssistantReadArtifact,
  onAssistantSubmit,
}: KnowledgeWorkbenchProps) {
  return (
    <div className="app">
      <Sidebar data={data} />
      <main className="main">
        <header className="topbar">
          <div className="crumbs">
            {data.breadcrumb.map((item, index) => (
              <span key={item}>
                {index > 0 ? <span className="crumb-separator">/</span> : null}
                {index === data.breadcrumb.length - 1 ? <b>{item}</b> : item}
              </span>
            ))}
          </div>
          <div className="top-actions">
            <span className="status-pill">
              <span className="status-dot" />
              {backendStatusLabel}
            </span>
            <button className="action-button" type="button">
              目录
            </button>
            <button className="action-button" type="button">
              来源
            </button>
            <button className="action-button primary" type="button">
              询问 AI
            </button>
          </div>
        </header>
        <ArticleView article={data.article} />
      </main>
      <AssistantPanel
        assistant={data.assistant}
        isReadingArtifact={assistantArtifactReading}
        isSubmitting={assistantSubmitting}
        onReadArtifact={onAssistantReadArtifact}
        onSubmit={onAssistantSubmit}
      />
    </div>
  );
}
