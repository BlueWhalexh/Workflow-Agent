import type { ArticleViewModel, ChipView } from "../../app/types";

type ArticleViewProps = {
  article: ArticleViewModel;
};

function chipClass(chip: ChipView) {
  return ["article-chip", chip.tone].filter(Boolean).join(" ");
}

export function ArticleView({ article }: ArticleViewProps) {
  return (
    <section className="article-shell">
      <article className="article">
        <div className="article-meta">
          {article.chips.map((chip) => (
            <span className={chipClass(chip)} key={chip.label}>
              {chip.label}
            </span>
          ))}
        </div>
        <h1>{article.title}</h1>
        <p className="subtitle">{article.subtitle}</p>

        <nav className="toc-strip" aria-label="本页目录">
          {article.toc.map((item) => (
            <a className="toc-chip" href={`#${item}`} key={item}>
              {item}
            </a>
          ))}
        </nav>

        <div className="article-content">
          <h2 id="摘要">摘要</h2>
          {article.summary.map((paragraph) => (
            <p key={paragraph}>{paragraph}</p>
          ))}
          <div className="boundary-note">{article.boundaryNote}</div>

          <h2 id="状态">当前状态</h2>
          <div className="status-row">
            {article.metrics.map((metric) => (
              <div className="metric" key={metric.label}>
                <span>{metric.label}</span>
                <strong>{metric.value}</strong>
              </div>
            ))}
          </div>

          <h2 id="边界">关键边界</h2>
          <ul>
            {article.boundaries.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>

          <h2 id="来源">来源与关联</h2>
          <div className="source-grid">
            {article.sources.map((source) => (
              <div className="source-card" key={source.path}>
                <strong>{source.title}</strong>
                <span>{source.path}</span>
              </div>
            ))}
          </div>

          <h2 id="候选变更">候选变更</h2>
          <p>右侧 AI 面板可以把建议转换成候选补丁。候选补丁需要展示 artifact、目标路径、审批状态和真实写入状态。</p>
          <section className="patch-preview" aria-label="候选变更预览">
            <header>
              <strong>{article.patch.label}</strong>
              <span className="article-chip gold">{article.patch.status}</span>
            </header>
            <p>{article.patch.summary}</p>
            <p>
              <code>{article.patch.targetPath}</code>
            </p>
          </section>

          <h2>关联页面</h2>
          <div className="page-links">
            {article.links.map((link) => (
              <div className="page-link" key={link.title}>
                <strong>{link.title}</strong>
                <span>{link.subtitle}</span>
              </div>
            ))}
          </div>
        </div>
      </article>
    </section>
  );
}
