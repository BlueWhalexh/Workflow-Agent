# Open Agent Candidate Patch And Real Smoke Spec

> 状态：Phase 30 candidate spec。目标是把 open agent 从 answer/draft baseline 推进到“可提议写入但不直接写入”的生产边界，并给 MiMo 真实调用增加 secret-safe smoke/eval 入口。

## Goal

Open agent 需要支持三类输出：

1. `ANSWER_ONLY`: 基于 workspace context 给出 grounded answer。
2. `DRAFT_ARTIFACT`: 生成不落库的草稿。
3. `CANDIDATE_PATCH`: 生成候选写入提案，但不 publish、不改 workspace。

候选写入提案用于回答“如果要把这个结果落库，会写到哪里、写什么、为什么安全/不安全”。真正写入仍必须交给固定 workflow 或后续显式确认链路。

## Non-goals

- 不把 open agent 升级成任意 workspace writer。
- 不改变 `PatchBundle -> MergeGuard -> Validator -> Publisher` 的唯一写入链路。
- 不改变 resume/publish truth source。
- 不把真实 token 写入代码、文档、artifact、trace 或测试 fixture。
- 不引入新外部依赖。

## Runtime Contract

`RunOpenAgentTaskRequest.outputPolicy` 新增：

```ts
type OpenAgentOutputPolicy = "ANSWER_ONLY" | "DRAFT_ARTIFACT" | "CANDIDATE_PATCH";
```

`CANDIDATE_PATCH` 输出：

```ts
interface CandidatePatchProposal {
  kind: "CANDIDATE_PATCH_PROPOSAL";
  publishable: false;
  targetPaths: string[];
  files: Array<{
    path: string;
    changeType: "CREATED" | "MODIFIED";
    baseSha: string | null;
    contentSha: string;
    content: string;
  }>;
  rationale: string;
  handoff: FixedWorkflowHandoff;
}
```

关键约束：

- `publishable` 必须是 `false`。
- `targetPaths` 只允许指向 `knowledge-base/`。
- 不能调用 `publishBundle`。
- 不能写 `knowledge-base/` 目标文件。
- 只能写 `.agent-runs/open-agent/<taskId>.json` 审计 artifact。

## Tool Evidence

Open agent report 必须记录 tool calls：

- `methodology.read`: 读取 methodology profile。
- `workspace.scan`: 扫描 workspace context。
- `open-agent.output`: 生成 answer/draft/candidate patch。
- `open-agent.selfCheck`: 校验不直接 publish。

如果 `allowedToolNames` 包含 `patch.publish`，runtime 返回 `FAILED_POLICY`，report 记录 policy failure。

## Confirmation Handoff

`handleCommand` 对模糊写入请求继续返回 `CONFIRMATION_REQUIRED`，但 confirmation 需要带上 handoff：

```ts
interface FixedWorkflowHandoff {
  type: "FIXED_WORKFLOW";
  capabilityId: "workflow.organizeWorkspace";
  executeRequired: true;
  confirmationRequired: true;
  methodologyId: string;
  instruction: string;
}
```

后端可以把这个 handoff 映射成确认 UI 或固定 workflow 调用，但不能把 open agent 的 candidate proposal 直接当成 publish artifact。

## Real MiMo Smoke

新增 open-agent real smoke helper 和 CLI：

```text
node --import tsx src/cli/open-agent-smoke.ts \
  --provider mimo-open-agent-smoke \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model <model> \
  --workspace-root <fixture-or-temp-workspace> \
  --execute-real
```

默认行为：

- 缺 env: `SKIPPED / MISSING_ENV`。
- 未传 `--execute-real`: `SKIPPED / EXECUTE_REAL_NOT_SET`。
- 传 `--execute-real`: 通过 MiMo OpenAI-compatible adapter 做一次真实调用，并运行一次 `CANDIDATE_PATCH` open-agent eval。

输出必须只报告状态、artifact path、provider、reason，不打印 API key。

## Acceptance

- Unit tests cover candidate patch proposal and no workspace write.
- Unit tests cover confirmation handoff for ambiguous workspace write command.
- Unit tests cover tool call evidence and answer grounding refs.
- Unit tests cover MiMo open-agent smoke inspect, injected-fetch pass, CLI stdin skip without key leakage.
- Full `npm test`, `npm run typecheck`, `git diff --check` pass.
