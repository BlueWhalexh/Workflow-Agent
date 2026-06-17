import type { AssistantViewModel } from "../../app/types";

type AssistantPanelProps = {
  assistant: AssistantViewModel;
};

export function AssistantPanel({ assistant }: AssistantPanelProps) {
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
          </div>
          <div className="approval-actions">
            <button className="mini-button primary" type="button">
              批准
            </button>
            <button className="mini-button danger" type="button">
              拒绝
            </button>
            <button className="mini-button" type="button">
              对比
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

      <form className="composer">
        <textarea aria-label="向 AI 提问" defaultValue={assistant.composerText} />
        <button type="button" aria-label="发送">
          ›
        </button>
      </form>
    </aside>
  );
}
