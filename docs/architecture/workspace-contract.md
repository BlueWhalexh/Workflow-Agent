# Workspace Contract

> 状态：第一阶段文件契约。本文定义 agent loop spike 的 workspace 边界、页面状态和运行产物。

## 1. 目录结构

```text
workspace/
  raw/
  schema/
    CLAUDE.md
  knowledge-base/
    moc.md
    topics/
      <topic>/
        index.md
        <note>.md
  .agent-runs/
    <runId>/
      run.json
      inventory.json
      plan.json
      work-items/
      patches/
      approvals/
      traces/
      validation.json
      eval.json
      report.md
      langgraph.sqlite
```

`log.md` 可以作为未来用户可读日志，但第一阶段不把它作为恢复事实源。

## 2. 权限边界

| 路径 | 默认权限 | 说明 |
| --- | --- | --- |
| `raw/` | 只读 | 用户导入原始资料，不允许 agent 默认覆盖。 |
| `schema/` | 只读 | 规则层，修改需要单独任务和确认。 |
| `knowledge-base/topics/<topic>/<note>.md` | 授权后可写 | Phase A topic note agent 的目标。 |
| `knowledge-base/topics/<topic>/index.md` | Phase B 可写 | 只能由 topic index agent 维护。 |
| `knowledge-base/moc.md` | Phase C 串行写 | 只能由 MOC agent 或 deterministic maintainer 维护。 |
| `.agent-runs/<runId>/` | runtime 可写 | 保存 plan、work item、patch、trace、validation、report、checkpoint。 |

硬规则：

- Agent node 不能直接写 workspace。
- `PatchBundle` 是唯一写入提案。
- `Publisher` 是唯一写入者。
- `raw/` 和 `schema/` 默认只读。
- Phase A 不允许写 topic index 或 MOC。
- MOC/index/log 变化不能单独证明整理成功。

## 3. 页面状态

topic note 至少区分：

```text
BOOTSTRAP_MIRROR
AGENT_ORGANIZED
USER_EDITED
MIXED
```

识别规则：

- `BOOTSTRAP_MIRROR`: 无 `agent-meta`，且命中 `Raw mirror:`、`Source path:`、`## Content` 模板。
- `AGENT_ORGANIZED`: 有 `agent-meta`，sourceSha 匹配，未检测到用户改动。
- `USER_EDITED`: 无 `agent-meta` 且不是 mirror，或用户明确接管。
- `MIXED`: 有 `agent-meta`，但 sourceSha 或 contentSha 发生变化。

`MIXED` 或用户编辑页允许 agent merge，但必须使用 `MERGE_USER_EDITED_NOTE`，默认生成候选 patch 等待批准。

## 4. Agent Meta

整理后的 note 写入隐藏 metadata block：

```markdown
<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - raw/tools/Skill vs CLI Tool 决策.md
sourceShas:
  raw/tools/Skill vs CLI Tool 决策.md: abc123
lastRunId: run-20260611-001
contentSha: ghi789
-->
```

职责：

- 帮助识别页面状态。
- 帮助判断 sourceSha 和 contentSha 是否变化。
- 帮助恢复时决定 skip、merge、replan。

限制：

- `agent-meta` 不是唯一事实源。
- 恢复必须同时检查 `.agent-runs` artifacts 和 workspace current sha。
- 用户可见内容不能依赖隐藏 metadata 才成立。

## 5. `.agent-runs` 契约

`.agent-runs/<runId>/` 保存：

- `run.json`: run 状态。
- `inventory.json`: 扫描快照。
- `plan.json`: 用户确认的执行计划。
- `work-items/*.json`: work item 状态、baseShas、attempts。
- `patches/*.json`: full-content PatchBundle。
- `approvals/*.json`: plan 或高风险 patch 审批记录。
- `traces/*.jsonl`: agent node 内部 loop trace。
- `validation.json`: hard blocker 检查结果。
- `eval.json`: 质量指标。
- `report.md`: 用户和 agent 可读报告。
- `langgraph.sqlite`: LangGraph runtime checkpoint。

事实源规则：

```text
LangGraph checkpoint 说明上次停在哪里。
.agent-runs artifacts 说明领域产物是什么。
workspace current sha 说明现在是否还能安全继续。
Validator 决定是否允许继续发布。
```

如果 checkpoint 与 artifacts 冲突，恢复时以 artifacts + workspace current sha + Validator 为准。

## 6. 发布前最低要求

`整理全部知识库` 不能只改：

- MOC。
- topic index。
- run artifacts。
- schema snapshot。
- log。

至少需要：

- 一个 topic note 被创建或改写。
- 如果存在 bootstrap mirror，至少一个 mirror 页面被改写为 organized note。
- 不引入 placeholder。
- 不写 raw/schema。
- MOC 能链接 topic index。
- topic index 能链接 topic notes。
- report 能说明 raw coverage、pagesRewritten、rawMirrorConverted、qualityIssues。

## 7. Topic Note Quality Contract

新建或改写的 topic note 必须满足最低结构要求：

- 标题能表达主题，不只是 raw 文件名机械复制。
- 有摘要，说明该 note 解决什么问题或沉淀什么知识。
- 有来源追踪，列出 source paths 和 source shas。
- 有关键概念、决策、步骤或事实，不允许只有索引链接。
- 有关联关系，使用 wikilink 或明确说明暂无相关链接。
- 不保留 bootstrap raw mirror 标记，例如 `Raw mirror:`、`Source path:`、`## Content` 模板。

最低整理要求：

- 对 raw 内容进行归纳、去重、重组，而不是原文整段搬运。
- 保留 raw 中的关键事实、判断和约束，不能只写泛化总结。
- 如果 raw 包含决策或取舍，note 必须保留 decision、rationale、trade-off。
- 如果 raw 包含操作步骤，note 必须保留可执行步骤或明确说明步骤不完整。
- 如果多个 source 合并到一个 note，必须说明来源之间的关系。
- 如果是 `MERGE_USER_EDITED_NOTE`，必须保留用户编辑内容的可追踪证据。

Validator 硬阻断结构性质量问题；QualityReview/Eval 报告内容性质量问题。

## 8. 禁止新增

新写入或改写内容禁止新增：

- `TODO`
- `TBD`
- `<placeholder>`
- 空洞的“后续补充”

这些属于 hard blocker，不进入 publish。
