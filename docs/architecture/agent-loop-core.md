# Agent Loop Core

> 状态：当前架构摘要。详细设计以 `docs/architecture/langgraph-agent-loop-design.md` 为准。

## 1. 目标

把“整理知识库很差”的核心问题独立出来解决：

```text
输入：用户指令 + workspace
输出：可验证、可恢复、可解释的知识库整理结果
```

本阶段只追求 agent loop 质量，不追求完整平台能力。

## 2. 架构原则

```text
Workflow controls the run.
Agent node solves one bounded task.
PatchBundle is the only write proposal.
MergeGuard and Validator decide safety.
Publisher is the only writer.
.agent-runs is the audit/recovery fact store.
LangGraph checkpoint is runtime state, not domain truth.
```

第一阶段采用 **LangGraph-first, Domain-pure**：

- LangGraph 负责 workflow、checkpoint、resume、approval interrupt、phase transition。
- Domain Core 负责 workspace contract、plan、work item、patch、merge、validation、eval。
- Agent node 负责单个 work item 内部的局部多步推理 loop。

## 3. 核心链路

```text
User Instruction
  -> Inventory Node                  deterministic
  -> Plan Node                       deterministic first
  -> Plan Approval Interrupt
  -> Phase A Note Agent Nodes        agent, output PatchBundle
  -> MergeGuard Node                 deterministic
  -> Publish Node                    deterministic
  -> Phase B Topic Index Agent       agent, output PatchBundle
  -> MergeGuard + Publish
  -> Phase C MOC / Quality / Report
  -> Final Eval
```

## 4. 核心模块

### WorkspaceInventory

确定性扫描 workspace，不调用 LLM。

输出：

- raw 文件列表、hash、标题、heading。
- schema 文件列表和 `schema/CLAUDE.md` 状态。
- knowledge-base 页面列表。
- topic note 页面状态。
- bootstrap raw mirror 候选。
- placeholder 候选。
- MOC 和 topic index 当前状态。
- `.agent-runs` 历史 run 摘要。

### OrganizePlanner

把用户指令和 inventory 转成半自动 plan。

Planner 只规划，不直接改文件，不执行 agent。

典型 work items：

- `CREATE_TOPIC_NOTE`
- `REWRITE_TOPIC_NOTE`
- `MERGE_USER_EDITED_NOTE`
- `MAINTAIN_TOPIC_INDEX`
- `MAINTAIN_MOC`
- `QUALITY_REVIEW`

Planner 输出必须同时服务两类读者：

- 用户能审 plan。
- 系统能从 plan 和 work item artifacts 恢复执行。

### Agent Nodes

Agent node 是 workflow 的受控计算节点，不是 workflow 本身。

第一阶段 agent nodes：

- `NoteAgentNode`
- `UserEditedMergeAgentNode`
- `TopicIndexAgentNode`
- `MOCAgentNode`
- `QualityReviewAgentNode`

Agent node 内部可以多步 loop：

```text
read scoped context
  -> draft
  -> self-check
  -> revise
  -> emit PatchBundle
```

Agent node 只能输出标准化结果：

- `PatchBundle`
- `QualityFindings`

Agent node 不能直接写 workspace，不能绕过 MergeGuard、Validator 或 Publisher。

### PatchBundle

Agent node 的唯一写入提案。第一版 canonical 存完整目标文件内容，不以 diff 作为领域事实。

关键字段：

- `workItemId`
- `targetPaths`
- `files[].path`
- `files[].changeType`
- `files[].baseSha`
- `files[].contentSha`
- `files[].content`
- `eval`
- `mergeEvidence`，仅 `MERGE_USER_EDITED_NOTE` 必填。

### MergeGuard

确定性合并前检查，不让模型自己解决发布安全问题。

阻断：

- 写入未授权路径。
- 写入 `raw/` 或 `schema/`。
- 两个 bundles 修改同一文件。
- patch baseSha 过期。
- target path 和 work item 不匹配。
- `MERGE_USER_EDITED_NOTE` 缺少批准。

### Publisher

唯一真正写 workspace 的组件。

约束：

- 只能写 `knowledge-base/` 授权目标。
- 必须在 MergeGuard 和 Validator 通过后执行。
- 必须更新 work item publish 结果。
- 必须让 resume 能通过 sha 判断是否跳过已发布内容。

### Validator / Eval

Validator 是硬闸门，Eval 是质量报告。

Validator 阻断：

- placeholder。
- raw/schema 写入。
- 缺标题、摘要、来源追踪。
- 仍像 bootstrap raw mirror 的 topic note。
- 只有索引链接、没有整理后正文的 topic note。
- MOC/index 链接契约破坏。

Eval 报告：

- raw coverage。
- pagesRewritten。
- rawMirrorConverted。
- qualityIssues。
- timeout / blocked / skipped work items。
- topic note 是否只是轻微改写 raw、关键事实是否疑似遗漏、关联关系是否薄弱。

## 5. 三阶段执行

### Phase A: Topic Notes

执行：

- `CREATE_TOPIC_NOTE`
- `REWRITE_TOPIC_NOTE`
- `MERGE_USER_EDITED_NOTE`

不允许写 MOC 或 topic index。

### Phase B: Topic Indexes

执行：

- `MAINTAIN_TOPIC_INDEX`

只允许写对应 topic 的 `index.md`。

### Phase C: Global Review

执行：

- `MAINTAIN_MOC`
- `QUALITY_REVIEW`
- `FINAL_REPORT`

MOC 或 index 变化不能单独证明整理成功。成功必须至少有一个 topic note 被创建或改写，除非 plan 明确说明没有可整理 raw。

## 6. 第一阶段验收

fixture：

```text
raw/go/Go 基础语法.md
raw/tools/Skill vs CLI Tool 决策.md
raw/agent/Agent Loop 失败复盘.md
schema/CLAUDE.md
knowledge-base/moc.md
knowledge-base/topics/tools/Skill vs CLI Tool 决策.md  # bootstrap raw mirror
```

验收：

- Inventory 正确识别 raw、schema、mirror 页面、placeholder 候选。
- Planner 产生三阶段 plan 和 topic/page 级 work items。
- LangGraph 进入 `WAITING_PLAN_APPROVAL`，用户批准后继续执行。
- Mock `NoteAgentNode` 成功产出 PatchBundle。
- Placeholder patch 被阻断。
- raw/schema 写入被阻断。
- 单个 work item timeout 不拖死整次 run。
- 至少一个 mirror 页面被改写为 `AGENT_ORGANIZED`。
- 改写后的 topic note 满足 Topic Note Quality Contract。
- MergeGuard 能检测同文件冲突。
- Resume 能跳过已 published 且 sha 匹配的 work item。
- Eval 输出 raw coverage、pagesRewritten、rawMirrorConverted、qualityIssues。
