# AGENTS.md

本项目是 `My-Workflow` 的 Agent Loop Core 重构试验目录。默认中文协作。

## 当前状态

- 架构状态：粗版探索，不是最终定稿。
- 当前目标：先验证 knowledge workspace agent loop，不做完整前后端平台。
- 禁止把本目录视为旧项目的直接平移；这里只迁移关键事实和设计约束。

## 工作原则

- 先做 agent loop，再接 CRUD、前端和平台能力。
- 核心业务规则必须在确定性代码中表达，不依赖 prompt 兜底。
- SDK 只能作为 adapter，不允许成为领域架构中心。
- 每个可验证切片都必须有 agent-readable 输出。
- 不把 mock/fake 测试描述成真实 provider 链路。
- 允许在本地 ignored env 文件（如 `.env`、`.env.local`）中保存开发用 token/API key/cookie/私钥；这些文件必须被 `.gitignore` 忽略，不能提交、推送或写入文档、日志、测试 fixture、snapshot、artifact。

## 当前优先级

1. WorkspaceInventory：确定性扫描 workspace。
2. OrganizePlanner：把用户指令拆成 work items。
3. SubagentExecutor：短循环处理 topic/page。
4. PatchBundle + MergeGuard：隔离写入、检测冲突。
5. Validator + Eval：阻断假成功，输出可读质量报告。

## 暂不做

- 不接前端。
- 不做用户/组织/权限 CRUD。
- 不做旧 compile 兼容。
- 不做博客发布或 L2 知识治理。
- 不确定 LangGraph / DeepAgents / Claude SDK 的最终选型前，不做深度绑定。
