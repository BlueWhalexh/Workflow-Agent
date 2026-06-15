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
4. `provider-runtime-spec.md`
   - 真实 LLM provider 接入前的主 spec。
   - 定义 provider runtime 目标、非目标、adapter 边界、运行时选择、错误分类、验收矩阵和升级条件。
5. `work-item-agent-runtime-spec.md`
   - 下一大阶段候选 spec。
   - 定义统一 work item agent runtime、bounded loop artifact、预算、失败语义、structured quality findings 和 report aggregation。
6. `agent-execution-quality-spec.md`
   - 已实现的 note agent quality loop spec。
   - 定义 bounded draft/self-check/repair/patch 的当前能力和 repair set。
7. `real-provider-quality-harness-spec.md`
   - 下一版本候选 spec。
   - 定义真实 provider workflow smoke 的可重复 harness、summary contract、promotion gate 和 secret-safe 边界。
8. `sdk-tool-surface-spec.md`
   - 已实现的后端接入 spec。
   - 定义 public SDK、CLI wrapper、LangGraph/internal tool registry 的分层边界。
9. `knowledge-methodology-registry-spec.md`
   - 已实现的 methodology registry spec。
   - 定义可注册落库方法论、`lmwiki-v1` 默认 profile、三层知识库结构和规则替换边界。
10. `methodology-aware-workflow-contract-spec.md`
   - 已实现的 workflow contract spec。
   - 定义 `methodologyId` 如何进入 GraphState、Plan、WorkItem、Validator、Eval、Report 和 CLI。
11. `hybrid-agent-command-router-spec.md`
   - 已实现的 SDK command router spec。
   - 定义固定 workflow、开放 agent task、写入确认之间的 lane/risk/capability envelope。
12. `knowledge-scoped-open-agent-runtime-spec.md`
   - 已实现的 open agent runtime baseline spec。
   - 定义知识库范围内的 plan/context/output/self-check loop、answer/draft artifact 和 no-direct-publish 边界。
13. `open-agent-candidate-patch-and-real-smoke-spec.md`
   - 已实现的 candidate patch / real smoke spec。
   - 定义 open agent candidate patch、fixed workflow handoff 和 MiMo 真实 smoke/eval 边界。
14. `llm-backed-open-agent-orchestrator-spec.md`
   - 已实现的 graph runner baseline spec。
   - 定义 LLM-backed OpenAgentGraph、bounded tool loop、provider trace、self-check 和后端 opt-in 集成。
15. `open-agent-provider-backed-graph-spec.md`
   - 已实现的 provider-backed graph spec。
   - 定义 OpenAI-compatible provider 如何进入 OpenAgentGraph plan/action，及 raw envelope redaction/artifact 边界。
16. `open-agent-provider-backed-synthesis-spec.md`
    - 当前候选 spec。
    - 定义 provider-backed synthesis 如何生成 answer/draft/candidate content，并经过 schema、grounding、no-publish self-check。
17. `java-team-backend-platform-spec.md`
   - 当前设计草案。
   - 定义 Java 中心化团队后端的技术选型、模块边界、多用户多 workspace 控制面、服务端托管 workspace 和 TS worker bridge。
18. `agent-loop-core.md`
    - 架构摘要。
    - 用于快速理解核心链路、模块边界和第一阶段验收。

`docs/handoff/from-my-workflow.md` 是旧项目交接事实，不是当前架构定义源。

`docs/changes/` 记录架构方向变化和规划转向。后续如果 product direction、workflow 类型、public contract 或安全边界变化，必须同步新增 change 文档。

## Backend Phase-One Tracking

- `docs/architecture/java-team-backend-platform-spec.md` 是 Java 中心化团队后端的当前技术方案和状态源。
- `docs/reports/java-backend-phase-one-completion-audit.md` 跟踪后端一期完成度、剩余缺口和当前 implementation gate。
- `docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md` 是下一步 J25A public provider credential metadata API 的待批准执行计划。

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
- Provider 只能作为 runtime adapter，经 agent node 包装为 `PatchBundle`，不能直接写 workspace 或决定 publish。
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
