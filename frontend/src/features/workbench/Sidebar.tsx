import type { WorkbenchViewModel } from "../../app/types";

type SidebarProps = {
  data: WorkbenchViewModel;
};

export function Sidebar({ data }: SidebarProps) {
  return (
    <aside className="sidebar">
      <div>
        <div className="brand">
          <div className="brand-mark">MW</div>
          <div>
            <strong>My Workflow</strong>
            <span>知识工作台</span>
          </div>
        </div>
        <section className="workspace-select" aria-label="当前工作区">
          <span>当前工作区</span>
          <strong>
            {data.workspaceName}
            <span>⌄</span>
          </strong>
        </section>
        <label className="search">
          <span>/</span>
          <input type="search" placeholder="搜索笔记、来源、任务" />
        </label>
      </div>

      <div className="side-scroll">
        <div className="side-title">
          <span>知识目录</span>
          <button className="icon-button" type="button" aria-label="新建知识页">
            +
          </button>
        </div>
        <nav className="tree" aria-label="知识目录">
          {data.treeItems.map((item) => (
            <div className={`tree-item${item.depth ? " depth" : ""}${item.active ? " active" : ""}`} key={item.id}>
              <span>{item.icon}</span>
              <span>{item.label}</span>
              <span className="tree-count">{item.count}</span>
            </div>
          ))}
        </nav>

        <div className="side-title">
          <span>最近变更</span>
          <button className="mini-icon" type="button" aria-label="刷新最近变更">
            ↻
          </button>
        </div>
        <div className="tree">
          {data.recentItems.map((item) => (
            <div className="tree-item" key={item.id}>
              <span>{item.icon}</span>
              <span>{item.label}</span>
              <span className="tree-count">{item.count}</span>
            </div>
          ))}
        </div>

        <section className="graph-card">
          <strong>主题图谱</strong>
          <p>{data.graphSummary}</p>
          <div className="mini-graph" aria-hidden="true">
            <span className="node a" />
            <span className="node b" />
            <span className="node c" />
            <span className="node d" />
            <span className="node e" />
          </div>
        </section>
      </div>

      <section className="admin-strip">
        <strong>管理入口</strong>
        <p>凭证、成员和审计作为次级入口，不占据第一屏主流程。</p>
        <div className="admin-links">
          <button type="button">成员</button>
          <button type="button">审计</button>
          <button type="button">凭证</button>
        </div>
      </section>
    </aside>
  );
}
