# From My-Workflow Handoff

> 状态：交接事实。本文记录从旧项目带入新 agent-loop-core 试验目录的关键事实，不迁移旧实现。

## 1. 旧项目当前问题

旧项目已经实现过 Phase 7 workspace-native 链路：

```text
Source / raw / schema
  -> AgentWorkspaceTask
  -> staging workspace
  -> WorkspaceVersion current
  -> IndexState / WikiPage / Search
  -> Preview UI
```

但真实 provider 测试暴露了 agent loop 不稳定：

```text
ORGANIZE_KNOWLEDGE FAILED
The read operation timed out
tools 48
schema 已读取
wikilinks 126
raw 3 个 / created pages 0 个
new placeholder marker introduced in knowledge-base/topics/tools/Skill vs CLI Tool 决策.md
```

## 2. 根因判断

问题不只是 provider timeout。

核心根因：

- 单个长 tool loop 承担了全库扫描、规划、写正文、维护索引、补链接、质量收敛。
- validator 太晚，只能在结尾阻断。
- bootstrap raw mirror 和 agent organized 页面在体验上混在一起。
- 前端只能看到最终成败，无法看到 work item 级进度。
- SDK 被过早放到中心位置，领域架构不够清晰。

## 3. 新目录要保留的经验

- raw/schema 默认只读。
- Agent 只能写 staging。
- 成功不能只看 MOC/index/log 变化。
- 必须检测 placeholder。
- 必须检测 raw mirror 是否被真正改写。
- publish 前必须有 deterministic validator。
- provider timeout 必须是 work item 级失败，不应拖死整个任务。
- 测试结果必须 agent-readable。

## 4. 新目录不迁移的东西

- 旧 compile 主链路。
- 前端页面。
- Spring Boot 后端。
- 数据库 schema。
- provider setting UI。
- 历史兼容代码。

## 5. 下一步建议

先实现 runtime-only spike：

```text
fixture workspace
  -> inventory
  -> plan
  -> subagent mock/deepagents executor
  -> patch bundle
  -> merge guard
  -> validation/eval report
```

只有 agent loop 质量过关后，再考虑接回后端和前端。

