# LangGraph Agent Loop Design

> 状态：第一阶段主 spec。本文记录半自动 knowledge workspace 整理的架构、技术选型、模块边界和验收切片。本文不代表最终平台架构。

## 1. 目标场景

第一阶段聚焦一个具体需求：

```text
用户说“整理全部知识库”
  -> 系统扫描 workspace
  -> 生成可审计 plan
  -> 用户确认 plan
  -> 系统按 work item 整理 raw 到 knowledge-base
  -> 系统维护 topic index 和 MOC
  -> 系统输出可恢复、可验证、可解释的 report
```

该场景必须支持失败恢复：

```text
用户说“继续上次整理”
  -> 系统找到未完成 run
  -> 优先从 LangGraph checkpoint 恢复
  -> checkpoint 不可用时，从 .agent-runs artifacts 重建恢复点
  -> 跳过已发布且 sha 仍匹配的 work item
  -> 重跑 timeout / executor failed / validator blocked 的 work item
```

第一版采用半自动模式：

- 系统先产出 plan。
- 用户确认 plan 后执行。
- 默认确认整份 plan。
- 高风险 work item 单独升级确认。
- 用户编辑页允许 agent merge，但只生成候选 patch，批准后才能发布。

## 2. 非目标

第一阶段不做：

- 完整前端平台。
- 用户、组织、权限、CRUD。
- 旧 compile 链路兼容。
- 博客发布或 L2 知识治理。
- 多 workspace / 多用户并发。
- 长任务队列、取消、重试平台。
- provider fallback 和 rate limit runtime。
- 让 LangGraph 成为领域事实源。

## 2.1 技术选型

第一阶段推荐 **TypeScript-first runtime**。

核心选择：

```text
Language: TypeScript
Runtime: Node.js, version pinned by package.json engines later
Workflow: @langchain/langgraph
Contracts: TypeScript types + runtime schema validation
Tests: Vitest
Storage: workspace filesystem + .agent-runs artifacts
CLI: Node-based local CLI
```

选择 TypeScript 的原因：

- 本阶段核心是 Markdown、JSON artifact、filesystem contract、sha、path guard 和 report，TypeScript 对这些确定性逻辑足够直接。
- `Plan`、`WorkItem`、`PatchBundle`、`ValidationResult` 等 contract 需要长期稳定，TypeScript 类型和 runtime schema 可以同时约束开发期与运行期。
- 后续如果接前端或本地 UI，TypeScript contract 更容易共享到 UI、CLI 和测试 fixture。
- LangGraph 官方 TypeScript 文档支持 `StateGraph`、persistence、interrupts、subgraphs 等能力，足够覆盖 Level 1 runtime shell。
- Domain Core 可以保持纯 TypeScript 单测，不需要启动 LangGraph 或真实 provider。

被拒绝的第一阶段选项：

| 选项 | 暂不采用原因 |
| --- | --- |
| Python-first | LangGraph Python 生态成熟，但本阶段更重文件契约、JSON contract、未来 UI/CLI 共享；跨语言会增加 spike 复杂度。 |
| DeepAgents-first | 适合快速搭 agent harness，但容易把 planning、subagents、filesystem、context 混到 runtime 中，削弱 domain core 边界。 |
| LangChain agents-first | 抽象层更高，不适合作为本阶段 workflow/control plane 的核心。 |
| LangSmith-first | 可作为后续 tracing/eval 平台，不作为第一阶段本地 spike 的必需依赖。 |
| 数据库持久化 | 第一阶段以 `.agent-runs` 文件产物验证恢复和审计，不引入 DB。 |

第一阶段依赖原则：

- 只引入能直接支撑验收切片的依赖。
- LangGraph 只用于 workflow orchestration，不承载 domain truth。
- runtime schema validation 用于保护 artifact 边界，不能替代 Validator。
- mock agent 先跑通，再加 real provider smoke。
- 不引入真实 provider SDK，直到 mock/fake 链路能证明 contract 正确。

官方参考：

- LangGraph TypeScript overview: `https://docs.langchain.com/oss/javascript/langgraph/overview`
- LangGraph Python overview: `https://docs.langchain.com/oss/python/langgraph/overview`

## 3. 总体架构

核心形态：

```text
LangGraph Workflow
  controls: order / state / approval / resume / phase transitions

Domain Core
  controls: workspace contract / plan / work item / patch / merge / validation

Agent Nodes
  control: local reasoning loop inside one bounded task
  output: PatchBundle or QualityFindings

Agent Runs Store
  controls: audit artifacts / recovery facts / trace / report
```

主链路：

```text
Inventory Node                  deterministic
  -> Plan Node                   deterministic first
  -> Plan Approval Interrupt
  -> Phase A Note Agent Nodes    agent, output PatchBundle
  -> MergeGuard Node             deterministic
  -> Publish Node                deterministic
  -> Phase B Topic Index Agent   agent, output PatchBundle
  -> MergeGuard + Publish
  -> Phase C MOC Agent           agent or deterministic template first
  -> Quality Review Node         deterministic + optional agent
  -> Final Eval / Report
```

硬规则：

```text
Workflow controls the run.
Agent node solves one bounded task.
PatchBundle is the only write proposal.
MergeGuard and Validator decide safety.
Publisher is the only writer.
.agent-runs is the audit/recovery fact store.
LangGraph checkpoint is runtime state, not domain truth.
```

## 4. 组件边界

### LangGraph Runtime

第一版使用 LangGraph Level 1：

- 主流程 graph。
- run checkpoint。
- plan approval interrupt。
- resume。
- phase transitions。

LangGraph 负责 orchestration，不负责领域判断。

### Domain Core

Domain Core 包含：

- `WorkspaceInventory`
- `OrganizePlanner`
- `WorkItem` model
- `PageState` detector
- `PatchBundle` contract
- `MergeGuard`
- `Publisher`
- `Validator`
- `Eval / Report`

Domain Core 必须可在不启动 LangGraph 的情况下用 unit test 验证。

### Agent Nodes

Agent node 是 workflow 的一个受控计算节点，不是 workflow 本身。

第一版 agent nodes：

- `NoteAgentNode`
- `UserEditedMergeAgentNode`
- `TopicIndexAgentNode`
- `MOCAgentNode`
- `QualityReviewAgentNode`

Agent node 可以在内部执行多步 loop：

```text
read scoped context
  -> draft
  -> self-check
  -> revise
  -> emit PatchBundle
```

从 workflow 外部看，agent node 只有标准输入输出：

```text
input: WorkItem + scoped context
output: PatchBundle / QualityFindings
trace: .agent-runs/<runId>/traces/<workItemId>.jsonl
```

Agent node 不能：

- 直接写 workspace。
- 修改 `raw/` 或 `schema/`。
- 绕过 `MergeGuard` 或 `Validator`。
- 决定 publish。
- 修改 plan 结构。
- 在 Phase A 修改 topic index 或 MOC。

## 5. 执行阶段

第一版强制三阶段执行。

### Phase A: Topic Notes

处理正文页面：

- `CREATE_TOPIC_NOTE`
- `REWRITE_TOPIC_NOTE`
- `MERGE_USER_EDITED_NOTE`

约束：

- 只允许写授权 target note。
- 不允许写 `knowledge-base/moc.md`。
- 不允许写 `knowledge-base/topics/<topic>/index.md`。
- `MERGE_USER_EDITED_NOTE` 只生成候选 patch，等待批准。

### Topic Note Quality Contract

Phase A 的目标不是把 raw 拆到 topic 目录，也不是只把 raw mirror 改名为 organized note。每个新建或改写的 topic note 必须体现知识整理质量。

最低结构要求：

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

质量判断分两层：

- 结构性质量由 deterministic validator 阻断，例如缺摘要、缺来源、仍像 raw mirror。
- 内容性质量由 `QualityReviewAgentNode` 或启发式 eval 进入 report，例如摘要空泛、关键概念遗漏、关联关系薄弱。

### Phase B: Topic Indexes

维护 topic index：

- `MAINTAIN_TOPIC_INDEX`

约束：

- 只允许写对应 topic 的 `index.md`。
- 输入来自已经发布或候选明确的 topic notes snapshot。

### Phase C: Global Review

维护全局文件和质量报告：

- `MAINTAIN_MOC`
- `QUALITY_REVIEW`
- `FINAL_REPORT`

约束：

- MOC 只能链接 topic index。
- MOC 或 index 变化不能单独证明整理成功。
- 成功必须至少有一个 topic note 被创建或改写，除非 plan 明确说明没有可整理 raw。

## 6. Workspace 和运行产物

Workspace 目录：

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

职责：

- `langgraph.sqlite` 是 runtime checkpoint。
- 其他 JSON/Markdown 是 domain artifact 和审计事实。
- 测试主要断言 artifacts，而不是 LangGraph 内部状态。
- 如果 checkpoint 和 artifacts 冲突，恢复时以 artifacts + workspace current sha + validator 为准。

## 7. 数据模型

### Plan

```json
{
  "runId": "run-20260611-001",
  "instruction": "整理全部知识库",
  "mode": "SEMI_AUTOMATIC",
  "workspaceSnapshot": {
    "workspaceRoot": "...",
    "rawCount": 3,
    "knowledgeBasePageCount": 4,
    "schemaSha": "abc123"
  },
  "approval": {
    "status": "PENDING",
    "approvedAt": null
  },
  "phases": [
    {
      "id": "phase-a-notes",
      "type": "NOTE_WRITES",
      "workItemIds": ["rewrite-tools-skill-vs-cli"]
    },
    {
      "id": "phase-b-indexes",
      "type": "TOPIC_INDEXES",
      "workItemIds": ["maintain-tools-index"]
    },
    {
      "id": "phase-c-global",
      "type": "GLOBAL_REVIEW",
      "workItemIds": ["maintain-moc", "quality-review"]
    }
  ]
}
```

### WorkItem

```json
{
  "id": "rewrite-tools-skill-vs-cli",
  "type": "REWRITE_TOPIC_NOTE",
  "phase": "phase-a-notes",
  "status": "PLANNED",
  "sourcePaths": ["raw/tools/Skill vs CLI Tool 决策.md"],
  "targetPaths": ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  "baseShas": {
    "raw/tools/Skill vs CLI Tool 决策.md": "abc",
    "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md": "def"
  },
  "risk": "LOW",
  "requiresApproval": false,
  "reason": "existing page is bootstrap raw mirror",
  "attempts": []
}
```

`MERGE_USER_EDITED_NOTE` 必须使用：

```json
{
  "type": "MERGE_USER_EDITED_NOTE",
  "risk": "HIGH",
  "requiresApproval": true,
  "publishPolicy": "CANDIDATE_PATCH_ONLY"
}
```

### PatchBundle

PatchBundle canonical 存完整目标内容，不以 diff 作为领域事实。

```json
{
  "workItemId": "rewrite-tools-skill-vs-cli",
  "status": "SUCCEEDED",
  "targetPaths": ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
  "files": [
    {
      "path": "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md",
      "changeType": "MODIFIED",
      "baseSha": "def",
      "contentSha": "ghi",
      "content": "# Skill vs CLI Tool 决策\n..."
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

`MERGE_USER_EDITED_NOTE` 必须额外包含：

```json
{
  "mergeEvidence": {
    "preservedSections": ["背景", "取舍"],
    "rewrittenSections": ["摘要", "来源追踪"],
    "userContentDropped": [],
    "conflicts": []
  }
}
```

### Agent Meta

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

页面状态：

- `BOOTSTRAP_MIRROR`: 无 agent-meta，命中 raw mirror 模板。
- `AGENT_ORGANIZED`: 有 agent-meta，sourceSha 匹配，未检测到用户改动。
- `USER_EDITED`: 无 agent-meta 且不是 mirror，或用户明确接管。
- `MIXED`: 有 agent-meta，但 sourceSha 或 contentSha 发生变化。

硬规则：

```text
WorkItem 是恢复调度单位。
PatchBundle 是唯一写入单位。
agent-meta 是页面状态辅助事实。
三者不能互相替代。
```

## 8. 恢复状态机

Run 状态：

```text
CREATED
PLANNING
WAITING_PLAN_APPROVAL
RUNNING
WAITING_PATCH_APPROVAL
VALIDATING
SUCCEEDED
SUCCEEDED_WITH_WARNINGS
FAILED
CANCELLED
```

WorkItem 状态：

```text
PLANNED
SKIPPED
RUNNING
SUCCEEDED
FAILED_TIMEOUT
FAILED_EXECUTOR
BLOCKED_BY_VALIDATOR
WAITING_APPROVAL
PUBLISHED
NEEDS_REPLAN
```

恢复决策：

```text
PUBLISHED + currentSha == contentSha
-> skip

PUBLISHED + currentSha != contentSha
-> mark MIXED, create MERGE_USER_EDITED_NOTE or NEEDS_REPLAN

SUCCEEDED but not PUBLISHED
-> rerun MergeGuard/Validator, then publish if still valid

FAILED_TIMEOUT
-> retry executor, attempts + 1

FAILED_EXECUTOR
-> retry if retryable; otherwise report failed

BLOCKED_BY_VALIDATOR
-> do not publish; retry only if validator issue is fixable by executor

WAITING_APPROVAL
-> keep waiting for user approval

NEEDS_REPLAN
-> run planner for affected sources only
```

Checkpoint 只能说明上次停在哪里，不能证明现在还能安全继续。恢复时必须重新检查 workspace current sha、授权路径、placeholder、raw/schema 禁写和 MOC/index 链接有效性。

## 9. Validator 和 Eval

Hard blockers 阻断 merge/publish：

- 写入 `raw/` 或 `schema/`。
- 写入未授权 target paths。
- `baseSha` 不匹配。
- 同一阶段两个 bundle 写同一 path。
- 新内容包含 `TODO`、`TBD`、`<placeholder>`、空洞的“后续补充”。
- topic note 缺标题。
- topic note 缺来源追踪。
- topic note 缺摘要。
- topic note 缺关键概念、决策、步骤或事实。
- topic note 仍保留 bootstrap raw mirror 模板。
- topic note 只有索引链接，没有整理后的正文内容。
- MOC 不链接任何 topic index。
- topic index 不链接任何 topic note。
- `MERGE_USER_EDITED_NOTE` 缺 `mergeEvidence`。

Quality issues 只进入 report：

- 摘要太短或空泛。
- 内容只是 raw 的轻微改写，缺少归纳、去重或重组。
- raw 中的关键事实、决策或约束疑似遗漏。
- 多 source 合并时没有说明来源关系。
- wikilink 数量为 0。
- raw coverage 不完整。
- note 没有相关链接且说明不充分。
- 某个 topic 下只有一个孤立页面。
- sourceSha 过期，需要重新整理。

最终状态：

- 没有 hard blocker，且没有 quality issue: `SUCCEEDED`
- 没有 hard blocker，但存在 quality issue: `SUCCEEDED_WITH_WARNINGS`
- 存在 hard blocker 且无法恢复: `FAILED`

## 10. LangGraph 使用层级和升级条件

原则：

```text
LangGraph 可以逐步接管 runtime complexity，但不能接管 domain truth。
只有 orchestration 复杂度上升时升级 LangGraph 使用深度。
如果只是知识库规则变复杂，应该升级 domain service，不升级 graph。
```

### Level 1: Runtime Shell

第一版即使用：

- 主流程 graph。
- checkpoint / resume。
- plan approval interrupt。
- phase transition。

### Level 2: WorkItem Subgraph

满足任意两个条件才升级：

- 单次 run 的 work item 数量大于 20。
- Phase A 中超过 3 个 work item 可能 timeout / retry。
- 需要 work item 级 pause / resume。
- 需要 work item 并发数控制。
- 需要不同 work item 使用不同 executor/provider。
- report 需要展示每个 work item 的实时状态流。
- agent 内部 loop 出现复杂分支。

升级后仍保持：

- subgraph 输出 PatchBundle。
- MergeGuard 是确定性代码。
- `.agent-runs` 是审计事实源。

### Level 3: Full Runtime

满足任意一个高强度条件才升级：

- 需要跨进程、跨机器恢复运行中的整理任务。
- 需要 UI 实时订阅 graph event。
- 需要长任务排队、取消、重试。
- 需要多个用户或多个 workspace 同时运行。
- 需要 provider fallback、retry policy、rate limit 进入 runtime 层。
- 需要 human review 成为正式流程节点。

升级后 LangGraph 才承担更多 runtime 职责：

- durable run orchestration。
- event stream。
- retry policy。
- interrupt lifecycle。
- task queue integration。
- runtime persistence adapter。

不随升级改变：

- `WorkspaceInventory`
- `OrganizePlanner`
- `PatchBundle`
- `MergeGuard`
- `Validator / Eval`
- workspace 文件契约

## 11. 模块分层

推荐目录结构：

```text
package.json
tsconfig.json
vitest.config.ts
src/
  domain/
    workspace/
    planning/
    patch/
    validation/
  runtime/
    langgraph/
  agents/
  executors/
  storage/
  cli/
tests/
  fixtures/
    workspaces/
      basic-raw-mirror/
      placeholder-blocked/
      resume-run/
  unit/
  integration/
```

领域接口示意：

```text
WorkspaceInventory.scan(workspaceRoot) -> WorkspaceInventory
OrganizePlanner.createPlan(instruction, inventory) -> OrganizePlan
WorkItemAgent.run(workItem, scopedContext) -> PatchBundle
MergeGuard.check(workItem, bundle) -> MergeDecision
Publisher.publish(bundle) -> PublishResult
Validator.validateBundle(workItem, bundle) -> ValidationResult
EvalReporter.buildReport(run, plan, workItems, validations) -> EvalReport
```

LangGraph state 只保存轻量引用：

```text
runId
workspaceRoot
instruction
currentPhase
planPath
pendingApproval
lastError
```

不要把完整 note 内容、patch content、大段 raw 放进 graph state。

## 11.1 项目骨架与测试方案

第一阶段推荐一个小而硬的 TypeScript 项目骨架，不先做框架化平台。

### 代码分层

```text
src/domain/
  workspace/
    inventory.ts
    page-state.ts
    workspace-contract.ts
  planning/
    plan.ts
    work-item.ts
    organize-planner.ts
  patch/
    patch-bundle.ts
    merge-guard.ts
    publisher.ts
  validation/
    validator.ts
    eval-reporter.ts

src/runtime/langgraph/
  graph.ts
  state.ts
  nodes/
    inventory-node.ts
    plan-node.ts
    approval-node.ts
    execute-phase-node.ts
    merge-node.ts
    publish-node.ts
    validate-node.ts
    report-node.ts

src/agents/
  work-item-agent.ts
  mock-note-agent.ts
  mock-topic-index-agent.ts
  quality-review-agent.ts

src/storage/
  workspace-fs.ts
  agent-runs-store.ts
  sha.ts
  json-schema.ts

src/cli/
  organize.ts
  resume.ts
```

### 测试分层

第一阶段测试优先级：

```text
unit/domain
  -> integration/runtime
  -> optional real provider smoke
```

Domain unit tests 必须覆盖：

- Inventory 识别 raw、schema、knowledge-base、bootstrap raw mirror。
- PageState 识别 `BOOTSTRAP_MIRROR`、`AGENT_ORGANIZED`、`USER_EDITED`、`MIXED`。
- Planner 生成三阶段 work items。
- MergeGuard 阻断 raw/schema 写入、未授权 target、baseSha mismatch、同文件冲突。
- Validator 阻断 placeholder、缺摘要、缺来源追踪、仍像 raw mirror 的 topic note。
- Resume decision 跳过已 published 且 sha 匹配的 work item。

Runtime integration tests 必须覆盖：

- LangGraph workflow 到达 `WAITING_PLAN_APPROVAL`。
- 批准 plan 后执行 mock `NoteAgentNode`。
- mock `NoteAgentNode` 产出 full-content `PatchBundle`。
- MergeGuard + Publisher 写入 authorized topic note。
- timeout work item 不拖死整个 run。
- 从 checkpoint 或 `.agent-runs` artifacts 恢复。

Fixture 目录：

```text
tests/fixtures/workspaces/basic-raw-mirror/
  raw/
  schema/
  knowledge-base/

tests/fixtures/workspaces/placeholder-blocked/
  raw/
  schema/
  knowledge-base/

tests/fixtures/workspaces/resume-run/
  raw/
  schema/
  knowledge-base/
  .agent-runs/
```

测试原则：

- 测 artifact，不测 LangGraph 私有状态。
- mock/fake 测试必须明确标注为 mock/fake。
- real provider smoke 只允许作为补充，不作为第一阶段 contract 的唯一证据。
- 每个 fixture 都要能被 agent-readable report 解释。

## 12. 第一阶段验收切片

Fixture workspace：

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
- Planner 生成三阶段 plan 和 topic/page 级 work items。
- LangGraph 进入 `WAITING_PLAN_APPROVAL`。
- 用户批准 plan 后继续执行。
- Mock `NoteAgentNode` 能产出 PatchBundle。
- MergeGuard 阻断 raw/schema 写入。
- Validator 阻断 placeholder patch。
- Validator 阻断缺摘要、缺来源追踪、仍像 raw mirror 的 topic note。
- 至少一个 bootstrap mirror 被改写为 `AGENT_ORGANIZED`。
- 改写后的 topic note 满足 Topic Note Quality Contract，不只是移动 raw 或拆分目录。
- `agent-meta` 写入 organized note。
- Topic index 和 MOC 由专门阶段维护。
- Quality review report 能指出空泛摘要、关键事实遗漏、关联关系薄弱等 topic 质量问题。
- Timeout work item 不拖死整个 run。
- Resume 能跳过已 published 且 sha 匹配的 work item。
- Eval 输出 raw coverage、pagesRewritten、rawMirrorConverted、qualityIssues。

第一阶段可以加一个 real provider smoke，但不能把 mock/fake 测试描述成真实 provider 链路。
