# Agent Loop Core 粗版架构

> 状态：粗版探索。本文只定义方向和边界，不代表最终技术选型或最终模块结构。

## 1. 目标

先把“整理知识库很差”的核心问题独立出来解决：

```text
输入：用户指令 + workspace
输出：可验证、可恢复、可解释的知识库整理结果
```

本阶段不追求完整平台，只追求 agent loop 的质量。

## 2. 核心链路

```text
User Instruction
  -> WorkspaceInventory
  -> OrganizePlan
  -> WorkItem Queue
  -> Subagent Executors
  -> PatchBundle
  -> MergeGuard
  -> Partial Validator
  -> Final Validator / Eval
  -> Result Report
```

## 3. 关键模块

### WorkspaceInventory

确定性扫描 workspace，不调用 LLM。

输出：

- raw 文件列表、hash、标题、heading。
- schema/CLAUDE.md 是否存在和规则摘要。
- knowledge-base 页面列表。
- bootstrap raw mirror 候选。
- placeholder 候选。
- MOC 和 topic index 当前状态。

### OrganizePlanner

把用户指令和 inventory 转成 work items。

Planner 只规划，不直接改文件。

典型 work items：

- `REWRITE_TOPIC_NOTE`
- `CREATE_TOPIC_NOTE`
- `MAINTAIN_TOPIC_INDEX`
- `MAINTAIN_MOC`
- `QUALITY_REVIEW`

### SubagentExecutor

每个 subagent 只处理一个小任务。

约束：

- 单次处理一个 topic/page/review。
- 只读指定 raw/source/schema 摘要。
- 只能写 target paths。
- 输出 patch bundle，不直接发布。
- 超时只影响当前 work item。

### PatchBundle

Subagent 输出的唯一写入载体。

建议结构：

```json
{
  "workItemId": "topic-tools-skill-vs-cli",
  "status": "SUCCEEDED",
  "targetPaths": ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  "patches": [
    {
      "path": "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md",
      "changeType": "MODIFIED",
      "baseSha": "...",
      "contentSha": "..."
    }
  ],
  "eval": {
    "rawFilesSeen": ["raw/tools/Skill vs CLI Tool 决策.md"],
    "rawMirrorConverted": true,
    "placeholderIntroduced": false,
    "wikilinksCreated": 3
  }
}
```

### MergeGuard

确定性合并，不让模型自己解决冲突。

阻断：

- 写入未授权路径。
- 两个 subagents 修改同一文件。
- patch baseSha 过期。
- 引入 placeholder。
- 修改 raw/schema。

### Validator / Eval

Validator 是硬闸门，Eval 是质量报告。

Validator 阻断发布；Eval 给 agent 和用户说明质量。

## 4. SDK 边界

SDK 只做执行 harness，不做领域事实源。

```text
Domain Core:
  inventory / plan / work item / patch / merge / validation

Harness Adapters:
  LangGraph
  DeepAgents
  Claude Agent SDK
  OpenAI-compatible
  Mock
```

候选判断：

- LangGraph 适合长任务状态机、持久化、重试和人审。
- DeepAgents 适合快速获得 planning、subagents、filesystem/context harness。
- Claude Agent SDK 适合作为 Claude 生态执行 adapter，但不应成为唯一主线。

## 5. 第一阶段验收

fixture：

```text
raw/go/Go 基础语法.md
raw/tools/Skill vs CLI Tool 决策.md
schema/CLAUDE.md
knowledge-base/topics/... 含 bootstrap raw mirror 页面
```

验收：

- Inventory 正确识别 raw、schema、mirror 页面。
- Planner 产生 topic/page 级 work items。
- Subagent 成功改写至少一个 mirror 页面。
- Placeholder patch 被阻断。
- 单个 work item timeout 不拖死整次任务。
- MergeGuard 能检测同文件冲突。
- Eval 输出 raw coverage、pagesRewritten、rawMirrorConverted、qualityIssues。

