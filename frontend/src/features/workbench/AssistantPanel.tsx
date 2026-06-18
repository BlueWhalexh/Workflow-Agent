import type { FormEvent } from "react";
import type { AssistantViewModel } from "../../app/types";
import type { ApprovalDecisionView } from "../approvals/approval-api";

type AssistantPanelProps = {
  assistant: AssistantViewModel;
  isDecidingApproval?: boolean;
  isOpeningRun?: boolean;
  isReadingArtifact?: boolean;
  isSubmitting?: boolean;
  onDecideApproval?: (decision: ApprovalDecisionView) => void | Promise<void>;
  onOpenRecentRun?: (runId: string) => void | Promise<void>;
  onReadArtifact?: () => void | Promise<void>;
  onSubmit?: (userMessage: string) => void | Promise<void>;
};

export function AssistantPanel({
  assistant,
  isDecidingApproval = false,
  isOpeningRun = false,
  isReadingArtifact = false,
  isSubmitting = false,
  onDecideApproval,
  onOpenRecentRun,
  onReadArtifact,
  onSubmit,
}: AssistantPanelProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const formData = new FormData(form);
    const userMessage = String(formData.get("userMessage") ?? "").trim();
    if (!userMessage) {
      return;
    }
    void onSubmit?.(userMessage);
  }

  return (
    <aside className="copilot">
      <div className="copilot-head">
        <div>
          <strong>AI 助手</strong>
          <span>上下文：当前页、来源、审批草稿</span>
        </div>
        <button className="icon-button" type="button" aria-label="折叠 AI 面板">
          −
        </button>
      </div>

      <div className="context" aria-label="当前上下文">
        {assistant.contextChips.map((chip) => (
          <span className="context-chip" key={chip}>
            {chip}
          </span>
        ))}
      </div>

      <section className="run-card" aria-label="运行状态">
        <header>
          <strong>{assistant.run.title}</strong>
          <code>{assistant.run.id}</code>
        </header>
        <div className="progress" aria-hidden="true">
          <span style={{ width: `${assistant.run.progress}%` }} />
        </div>
        <div className="event-list">
          {assistant.run.events.map((event) => (
            <div className="event" key={`${event.time}-${event.label}`}>
              <span>{event.time}</span>
              <span>{event.label}</span>
            </div>
          ))}
        </div>
      </section>

      <div className="chat">
        {assistant.recentRuns.length > 0 ? (
          <section className="recent-runs" aria-label="最近运行">
            <strong>最近运行</strong>
            <div className="recent-run-list">
              {assistant.recentRuns.map((run) => (
                <button
                  className="recent-run-button"
                  disabled={isOpeningRun}
                  key={run.runId}
                  type="button"
                  onClick={() => void onOpenRecentRun?.(run.runId)}
                >
                  <span>{run.title}</span>
                  <code>{run.status}</code>
                </button>
              ))}
            </div>
          </section>
        ) : null}

        {assistant.messages.slice(0, 3).map((message) => (
          <div className={`message ${message.kind}`} key={`${message.author}-${message.text}`}>
            <strong>{message.author}</strong>
            <p>{message.text}</p>
          </div>
        ))}

        <section className="approval-card" aria-label="审批草稿">
          <strong>{assistant.approval.title}</strong>
          <p>{assistant.approval.summary}</p>
          <div className="approval-meta">
            <span>artifact: {assistant.approval.artifact}</span>
            <span>target: {assistant.approval.target}</span>
            <span>wroteWorkspace: {String(assistant.approval.wroteWorkspace)}</span>
            {assistant.approval.status ? <span>approvalStatus: {assistant.approval.status}</span> : null}
            {assistant.approval.decision ? <span>decision: {assistant.approval.decision}</span> : null}
          </div>
          {assistant.approval.artifactPreview ? (
            <div className="artifact-preview">
              <span>
                {assistant.approval.artifactPreview.title} · {assistant.approval.artifactPreview.contentType}
              </span>
              <pre>{assistant.approval.artifactPreview.content}</pre>
            </div>
          ) : null}
          <div className="approval-actions">
            <button
              className="mini-button primary"
              type="button"
              disabled={isDecidingApproval}
              onClick={() => void onDecideApproval?.("APPROVED")}
            >
              {isDecidingApproval ? "提交中" : "批准"}
            </button>
            <button
              className="mini-button danger"
              type="button"
              disabled={isDecidingApproval}
              onClick={() => void onDecideApproval?.("REJECTED")}
            >
              拒绝
            </button>
            <button className="mini-button" type="button" disabled={isReadingArtifact} onClick={() => void onReadArtifact?.()}>
              {isReadingArtifact ? "读取中" : "对比"}
            </button>
          </div>
        </section>

        {assistant.messages.slice(3).map((message) => (
          <div className={`message ${message.kind}`} key={`${message.author}-${message.text}`}>
            <strong>{message.author}</strong>
            <p>{message.text}</p>
          </div>
        ))}
      </div>

      <form className="composer" onSubmit={handleSubmit}>
        <textarea
          aria-label="向 AI 提问"
          defaultValue={assistant.composerText}
          disabled={isSubmitting}
          name="userMessage"
        />
        <button type="submit" aria-label="发送" disabled={isSubmitting}>
          {isSubmitting ? "…" : "›"}
        </button>
      </form>
    </aside>
  );
}
