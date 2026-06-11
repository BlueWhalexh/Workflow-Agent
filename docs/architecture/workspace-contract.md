# Workspace Contract 粗版

> 状态：粗版探索。本文定义 agent loop spike 的 workspace 文件契约，后续可调整。

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
  log.md
```

## 2. 权限边界

| 路径 | 默认权限 | 说明 |
| --- | --- | --- |
| `raw/` | 只读 | 用户导入原始资料，不允许 agent 默认覆盖。 |
| `schema/` | 只读 | 规则层，修改需要单独任务和确认。 |
| `knowledge-base/` | staging 可写 | Agent 主要整理区域。 |
| `knowledge-base/moc.md` | 串行写 | 只能由 MOC maintainer 统一维护。 |
| `knowledge-base/topics/<topic>/index.md` | topic indexer 写 | 不让 topic organizer 随意改。 |
| `log.md` | 串行写 | 记录维护日志，避免多 subagent 并发写。 |

## 3. 页面状态

topic note 至少区分：

```text
BOOTSTRAP_MIRROR
AGENT_ORGANIZED
USER_EDITED
MIXED
```

第一阶段可以用启发式识别：

- 包含 `Raw mirror:`、`Source path:`、`## Content` 的页面视为 mirror 候选。
- 经 subagent 改写并通过 validator 后，视为 agent organized。

## 4. 发布前最低要求

`整理全部知识库` 不能只改：

- MOC
- topic index
- log
- schema snapshot

至少需要：

- 一个 topic note 被创建或改写。
- 如果存在 bootstrap mirror，至少一个 mirror 页面被改写为 organized note。
- 不引入 placeholder。
- 不写 raw/schema。
- MOC 能链接 topic index。
- topic index 能链接 topic notes。

## 5. 质量底线

新写入或改写的 topic note 必须包含：

- 标题。
- 摘要。
- 来源追踪。
- 关键概念或决策。
- 相关链接或明确说明暂无相关链接。

禁止新增：

- `TODO`
- `TBD`
- `<placeholder>`
- 空洞的“后续补充”

