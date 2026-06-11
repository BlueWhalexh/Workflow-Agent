# My-Workflow Agent Loop Core

这是从 `My-Workflow` 拆出的新一轮 Agent Loop Core 重构试验目录。

## 定位

本目录不是旧项目重写版，也不是最终架构仓库。目前只是粗版架构探索与后续实现的起点。

核心问题：

```text
用户输入“整理全部知识库”
  -> 系统如何稳定地扫描、规划、拆分、执行、合并、验证
  -> 最终得到真正 Obsidian 风格的知识库整理结果
```

## 为什么新开目录

旧项目已经混合了：

- 旧 compile 链路。
- Phase 7 workspace-native 改造。
- 前后端联调代码。
- provider 适配。
- 多轮临时修复。

继续在旧目录里补 agent loop，容易把架构边界继续打乱。新目录先把 agent loop 做小、做清楚、做可验证。

## 初始架构方向

```text
User Instruction
  -> WorkspaceInventory
  -> OrganizePlanner
  -> WorkItem Queue
  -> Subagent Executors
  -> PatchBundle
  -> MergeGuard
  -> Validator / Eval
  -> Agent-readable Result
```

## SDK 策略

SDK 暂不定稿。候选包括：

- LangGraph：状态机、持久化、重试、人审中断。
- DeepAgents：规划、subagents、filesystem/context harness。
- Claude Agent SDK：Claude 生态 adapter。
- OpenAI-compatible providers：DeepSeek、MiMo、Kimi 等 adapter。

领域核心不直接依赖任一 SDK。

## 成功标准

第一阶段成功不看前端，也不看 CRUD，而看 agent loop 是否能通过 fixture 验证：

- 能识别 raw、schema、knowledge-base 当前状态。
- 能拆出 topic/page 级 work items。
- 单个 topic timeout 不拖死整个任务。
- placeholder patch 被阻断。
- raw mirror 页面能被改写为 agent organized note。
- MOC/index 由专门步骤统一维护。
- 最终结果能用 JSON/Markdown 报告清楚表达系统状态。

