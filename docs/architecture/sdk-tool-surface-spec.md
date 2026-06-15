# SDK And Tool Surface Spec

> 状态：Phase 27 implemented spec。目标是把当前可运行的 LangGraph agent loop 封装为后端可接入的 SDK，同时保留 CLI 作为开发/验收工具，并把 LangGraph 内部可调用能力整理为受控 tools。本文定义边界，不把所有内部函数直接暴露成 public API。

## 1. Current Assessment

当前代码已经具备完整 workflow 能力：

- `runOrganizeWorkflow` 可运行 LangGraph workflow。
- Domain 层已有 inventory、planner、patch、merge guard、publisher、validator、resume/eval。
- Agent 层已有 note quality loop、work item loop report、topic index、quality review。
- Provider 层已有 fake、fixture、MiMo real、DeepSeek real adapter。
- CLI 已有 `organize`、`resume`、`provider-smoke`。

Phase 27 前的缺口：

- `src/index.ts` 只导出 `projectName`，没有稳定 SDK 面。
- 后端如果现在接入，只能深层 import `src/runtime/langgraph/graph.ts`，会绑定内部结构。
- CLI 直接组装 workflow 参数；后续如果 SDK 接口变化，CLI 和后端会分叉。
- “tool” 概念还没分层：domain helper、LangGraph node、CLI command、backend SDK 容易混用。
- 真实 provider workflow smoke 还没有标准 CLI harness 和 summary contract。

Phase 27 已交付：

- `src/index.ts` 导出 `createKnowledgeWorkflowAgent`、`runOrganize`、`inspectRun` 和稳定 request/result types。
- `runOrganize` 返回后端可消费的 `artifactRoot`、`methodologyId`、`planPath`、`reportPath`、`lastError`。
- `inspectRun` 只读 `.agent-runs` artifacts，不重新执行 workflow。
- `organize` CLI 改为 SDK wrapper，输出 SDK result JSON。
- `resume` CLI 改为调用 SDK `inspectRun`，保留现有 resume decisions 输出。
- internal tool registry 第一版只包含 metadata 和 risk/exposure 分类，不暴露可直接写 workspace 的 public tool。

## 2. Terminology

### SDK

SDK 是给后端或其他 TypeScript 服务调用的稳定程序接口。

示例：

```ts
const agent = createKnowledgeWorkflowAgent(config);
const result = await agent.runOrganize(request);
```

SDK 负责：

- 参数 schema；
- provider/runtime dependency injection；
- workflow 调用；
- artifact path 返回；
- 错误分类；
- 后端可消费的 response contract。

SDK 不负责：

- 直接展示交互 UI；
- 读取 shell stdin；
- 打印 JSON；
- 绕过 Validator/Publisher。

### CLI

CLI 是 SDK 的薄包装，用于本地开发、验收、运维和真实 provider smoke。

示例：

```bash
node --import tsx src/cli/organize.ts ...
node --import tsx src/cli/real-workflow-smoke.ts ...
```

CLI 负责：

- argv/stdin/env 解析；
- secret-safe input；
- 调用 SDK；
- stdout 输出 JSON；
- shell 友好的 exit code。

CLI 不应成为后端集成主路径。

### Internal Tools

Internal tools 是 LangGraph node 或 SDK 内部调用的受控能力。

示例：

- `scanWorkspace`
- `createOrganizePlan`
- `runMockNoteAgent`
- `validateBundle`
- `publishBundle`
- `reportNode`

Internal tools 可以组合 workflow，但必须遵守：

- 写 workspace 只能经过 `PatchBundle -> MergeGuard -> Validator -> Publisher`。
- Provider adapter 不能直接写 workspace。
- Agent loop 不能直接写 publishable artifacts，除非通过 runtime boundary。
- Trace/raw envelope 不能参与 publish/resume 判定。

## 3. Target Layering

```text
Backend service
  -> Public SDK
    -> LangGraph Workflow
      -> Internal Tools
        -> Domain Core
        -> Agent Runtime
        -> Provider Adapters

CLI
  -> Public SDK
```

## 4. Public SDK Surface

第一版 SDK 建议只暴露少量稳定接口：

```ts
interface KnowledgeWorkflowAgent {
  runOrganize(request: RunOrganizeRequest): Promise<RunOrganizeResult>;
  inspectRun(request: InspectRunRequest): Promise<InspectRunResult>;
  resumeRun(request: ResumeRunRequest): Promise<ResumeRunResult>;
  runRealWorkflowSmoke(request: RealWorkflowSmokeRequest): Promise<RealWorkflowSmokeResult>;
}
```

### `runOrganize`

用于后端触发知识库整理。

输入：

```ts
interface RunOrganizeRequest {
  workspaceRoot: string;
  instruction: string;
  runId?: string;
  methodologyId?: string;
  autoApprove: boolean;
  providerRuntime?: ProviderRuntimeConfig;
  providerRuntimeDependencies?: ProviderRuntimeDependencies;
}
```

输出：

```ts
interface RunOrganizeResult {
  runId: string;
  status: "WAITING_PLAN_APPROVAL" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  methodologyId: string;
  planPath?: string;
  reportPath?: string;
  lastError?: string;
  artifactRoot: string;
}
```

### `inspectRun`

用于后端读取某个 run 的 artifacts，不重新执行 workflow。

输出应包括：

- plan summary；
- work item statuses；
- eval summary；
- report path；
- approval state；
- needs replan items。

### `resumeRun`

用于后端对同一 `runId` 恢复或重跑。

规则：

- 不覆盖旧 plan；
- published + sha match -> skip；
- published + sha mismatch -> needs replan；
- retryable failure -> retry；
- non-retryable failure -> report failed。

### `runRealWorkflowSmoke`

用于开发/验收环境，不是生产业务接口。

规则：

- 必须显式 `executeReal=true`。
- API key 只允许通过 dependency injection / stdin wrapper 注入，不写入 config。
- 只运行临时 fixture workspace。
- 输出 `real-provider-workflow-smoke.v1` summary。

## 5. CLI Surface

保留并收敛为 SDK wrapper：

| CLI | Purpose | Calls SDK |
| --- | --- | --- |
| `organize` | 本地跑 organize workflow | `agent.runOrganize` |
| `resume` | 查看或恢复 run | `agent.inspectRun` / `agent.resumeRun` |
| `provider-smoke` | 单 provider API smoke | provider smoke helper |
| `real-workflow-smoke` | 真实 workflow smoke + promotion gate | `agent.runRealWorkflowSmoke` |

CLI 输出必须是 agent-readable JSON，除 usage/error message 外不输出自由文本。

## 6. Internal Tool Registry

第一版不需要引入复杂 tool framework，只需要显式 registry：

```ts
interface InternalTool<I, O> {
  name: string;
  description: string;
  risk: "READ_ONLY" | "ARTIFACT_WRITE" | "WORKSPACE_WRITE";
  execute(input: I): Promise<O>;
}
```

建议工具分组：

### Read tools

- `workspace.scan`
- `artifact.readPlan`
- `artifact.readWorkItems`
- `artifact.readEval`

### Planning tools

- `plan.createOrganizePlan`

### Agent tools

- `agent.runNoteWorkItem`
- `agent.runTopicIndexWorkItem`
- `agent.runMocWorkItem`
- `agent.runQualityReview`

### Safety tools

- `patch.checkMerge`
- `patch.validate`
- `patch.publish`

### Reporting tools

- `report.aggregateEval`
- `report.writeRunReport`

第一版 registry 只给 LangGraph/SDK 内部使用，不暴露给 LLM 自由调用。

## 7. What Not To Expose

不要作为 public SDK 暴露：

- `executePhaseNode`、`planNode` 等 LangGraph node 内部函数；
- raw provider envelope writer；
- `publishBundle` 单独入口；
- 能直接写 workspace 的工具；
- 真实 provider API key 配置字段；
- checkpoint internals。

这些能力只能通过 workflow 或 internal tool registry 受控调用。

## 8. Next Delivery Acceptance

Phase 24 完成后应满足：

- `src/index.ts` 导出稳定 SDK API，而不是只有 `projectName`。
- 后端只需要 import public SDK，不需要 import `runtime/langgraph/*`。
- CLI 改为调用 SDK wrapper。
- 有 unit tests 验证 SDK exports 和 response contract。
- 有 integration tests 验证 CLI 和 SDK 输出一致。
- 真实 workflow smoke 仍作为 opt-in 验收，不进入默认 `npm test`。
- `npm test`、`npm run typecheck`、`git diff --check` 通过。

## 9. Open Decisions

下一轮实现前需要确认：

1. SDK 包名是否继续用当前 package，还是未来拆出 `@my-workflow/agent-sdk`。
2. 后端调用 SDK 时 workspaceRoot 是本地路径，还是后续会改为 workspaceId + storage adapter。
3. 第一版 `inspectRun` 是否只读 artifacts，还是要包含 resume decision。
4. Internal tool registry 是否现在实现，还是先只定义 SDK facade。

建议第一版选择：

- 继续用当前 package。
- workspaceRoot 仍为本地路径。
- `inspectRun` 包含 artifact summary + resume decision。
- registry 只做最小类型和 read/safety tool 列表，不急着让 LLM 动态调用。
