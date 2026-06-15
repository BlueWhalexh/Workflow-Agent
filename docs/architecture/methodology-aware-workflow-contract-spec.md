# Methodology-aware Workflow Contract Spec

> 状态：Phase 26 candidate spec。目标是把 `methodologyId` 从内部 registry 推进到 workflow contract，使 plan、validation、eval、report 都能明确说明本次运行使用哪套落库方法论。

## 1. Background

Phase 25 已经新增 `KnowledgeMethodology Registry` 和默认 `lmwiki-v1` profile。当前问题是：

- `runNoteQualityLoop` 已从 profile 读取 heading alias / placeholder blocker；
- 但 `plan.json`、`eval.json`、`report.md` 仍不知道本次运行使用哪套 methodology；
- `Validator` 仍然写死 `raw/`、`schema/`、`knowledge-base/topics/`、required note sections 和 placeholder blockers；
- CLI/SDK 入口没有一个受控的 methodology 选择点。

这会导致后续扩展新方法论时出现“局部可配置、主链路不可追踪”的问题。

## 2. Goal

把 methodology 变成 workflow 运行时事实：

```text
CLI/SDK input
  -> GraphState.methodologyId
  -> plan.json.methodologyId
  -> WorkItem.methodologyId
  -> Validator(methodology)
  -> eval.json.methodology
  -> report.md methodology summary
```

第一版只支持 `lmwiki-v1`，unknown methodology 必须 fail-fast。

## 3. Non-Goals

本阶段不做：

- 不实现用户自定义 methodology 动态加载。
- 不允许 methodology 执行脚本或直接写 workspace。
- 不改变真实 provider 行为。
- 不改变当前默认 organize 行为。
- 不重构完整 workspace inventory。
- 不把 flexible answer agent 接入主链路。

## 4. Runtime Contract

### GraphState

新增：

```ts
methodologyId: string;
```

默认值为 `lmwiki-v1`。

### OrganizePlan

新增：

```ts
methodologyId: string;
methodologyVersion: string;
```

`workspaceSnapshot` 继续记录 workspace root 和 counts。methodology 信息独立于 snapshot，表示本次 workflow 的规则选择。

### WorkItem

新增：

```ts
methodologyId?: string;
```

用于 work item artifact 自描述。第一版 executor 不依赖这个字段做分支，仍以 `plan.methodologyId` 为 run-level truth。

### Eval

新增：

```json
"methodology": {
  "id": "lmwiki-v1",
  "version": "1"
}
```

### Report

`report.md` 增加：

```text
- Methodology: lmwiki-v1@1
```

## 5. Validator Contract

`validateBundle` 增加可选 `methodologyId`：

```ts
validateBundle({
  targetPaths,
  files,
  methodologyId
})
```

规则：

- 默认 `lmwiki-v1`，保持现有调用兼容；
- unknown id fail-fast；
- raw/rules write blocker 读取 `methodology.layout.rawDir` 和 `methodology.layout.rulesDir`；
- topic note 判断读取 `methodology.layout.topicDir`；
- required sections 读取 `methodology.noteSchema.requiredSections`；
- placeholder blocker 读取 `methodology.noteSchema.placeholderBlockers`。

`requiredSections` 第一版支持两种表达：

- 普通 section，例如 `摘要`、`来源追踪`、`相关链接`；
- OR section，例如 `关键决策|关键概念|关键步骤`。

## 6. CLI Contract

`organize` CLI 新增可选参数：

```bash
--methodology lmwiki-v1
```

默认不传等价于 `lmwiki-v1`。unknown methodology 在启动 workflow 前报错，不创建 run artifacts。

## 7. Acceptance

- 不传 methodology 时，现有 organize workflow 行为不变。
- `plan.json` 包含 `methodologyId: "lmwiki-v1"` 和 `methodologyVersion: "1"`。
- 每个 work item artifact 包含 `methodologyId: "lmwiki-v1"`。
- `eval.json` 包含 methodology summary。
- `report.md` 包含 `Methodology: lmwiki-v1@1`。
- `validateBundle` 使用 methodology profile 的 layout / required sections / placeholders。
- CLI 传 unknown methodology 失败，stderr 包含 unknown methodology 信息。
- Full unit/integration/typecheck/diff checks 通过。

## 8. Review Focus

- `methodologyId` 是否只有一个 run-level truth source。
- Validator 是否从 profile 读取规则，而不是继续散落硬编码。
- Unknown methodology 是否在写 workspace 前失败。
- 默认 `lmwiki-v1` 是否保持完全向后兼容。
