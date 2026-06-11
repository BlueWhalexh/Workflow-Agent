# Architecture Docs

> 状态：架构文档入口。本文只定义阅读顺序和文档职责，不重复完整设计。

## Current Truth Order

当架构文档之间出现冲突时，按以下顺序判断：

1. `langgraph-agent-loop-design.md`
   - 第一阶段主设计。
   - 定义 LangGraph-first / Domain-pure 架构、技术选型、agent node contract、三阶段执行、恢复状态机、Validator/Eval、项目骨架、升级条件。
2. `workspace-contract.md`
   - workspace 文件契约。
   - 定义目录结构、权限边界、页面状态、`agent-meta`、`.agent-runs`、topic note 质量底线。
3. `llm-call-trace-contract.md`
   - LLM 调用记录契约。
   - 定义 `.agent-runs/<runId>/traces/*.jsonl` 的 canonical trace、provider raw envelope、redaction、Claude/DeepSeek/MiMo 兼容边界。
4. `agent-loop-core.md`
   - 架构摘要。
   - 用于快速理解核心链路、模块边界和第一阶段验收。

`docs/handoff/from-my-workflow.md` 是旧项目交接事实，不是当前架构定义源。

## Architecture Decision Summary

第一阶段采用：

```text
LangGraph-first, Domain-pure
```

含义：

- LangGraph 负责 workflow、checkpoint、resume、approval interrupt、phase transition。
- 当前 checkpoint 已接入 `MemorySaver` 边界验证；跨进程或长任务恢复前仍需 durable file/SQLite saver。
- Domain Core 负责 inventory、plan、work item、patch、merge、publish、validation、eval。
- Agent node 是 workflow 内的受控计算节点，只处理一个 bounded work item。
- Agent node 只能输出 `PatchBundle` 或 `QualityFindings`。
- `Publisher` 是唯一真正写 workspace 的组件。
- `.agent-runs/<runId>/` 是审计与恢复事实源。
- `traces/*.jsonl` 记录 agent node 内部 LLM loop，但不能替代 artifacts、workspace sha 或 Validator。
- LangGraph checkpoint 是 runtime state，不是 domain truth。

技术选型：

- 第一阶段推荐 TypeScript-first runtime。
- LangGraph 使用 `@langchain/langgraph`。
- 测试使用 Vitest。
- 存储先用 workspace filesystem 和 `.agent-runs` artifacts，不引入数据库。
- mock agent 先跑通 contract，再加 optional real provider smoke。

## First Slice

第一阶段只验证一个最小但完整的 agent loop：

```text
fixture workspace
  -> WorkspaceInventory
  -> OrganizePlanner
  -> LangGraph Level 1 workflow
  -> plan approval pause
  -> mock NoteAgentNode
  -> PatchBundle
  -> MergeGuard
  -> Publisher
  -> Validator / Eval report
  -> artifact-based resume
```

不做完整前端、CRUD、旧 compile 兼容、多 workspace、多用户、provider fallback、rate limit 或长任务队列。
