# Work Item Agent Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified work item agent runtime so every executed work item produces bounded loop evidence, budget accounting, structured quality output, durable failure semantics, and exact eval coverage without changing publish/resume truth sources.

**Architecture:** Keep LangGraph as workflow control and Domain Core as truth. Introduce a runtime boundary for context/result validation, a runtime registry for work item agent nodes, `work-item-agent-loop.v1` artifacts for note/index/MOC/quality work items, durable failure metadata in work item attempts, and exact loop evidence aggregation in `eval.json` and `report.md`.

**Tech Stack:** TypeScript, Vitest, existing LangGraph workflow, `.agent-runs` filesystem artifacts.

---

## Execution Status

已执行并通过验证：

- Runtime boundary helper：invalid output / budget exceeded 会返回 non-retryable `FAILED_EXECUTOR`。
- Unified loop artifact：note、topic-index、MOC、quality-review 都写 `work-item-agent-loop.v1`。
- Execute phase loop gate：写 patch/quality artifact 前校验 loop schema、context、budget、outputRef。
- Durable attempts：provider / loop / validator / merge failure attempts 带 `failureSource`、`failureReason`、`retryable`。
- Resume semantics：resume 读取最新 attempt 的 `retryable`，不再硬编码失败都可重试。
- Context contract：第一版提供声明式 per-work-item scope，不做文件系统 sandbox enforcement。
- Structured quality findings：quality review 输出 `severity: "warning"` 和 evidence。
- Eval/report aggregation：`eval.agentLoop` 统计 total、reports、byNode、providerCalls、budgetExceeded、missing/corrupt artifacts、repaired/remaining issues。
- Atomic artifacts：`AgentRunsStore` 使用 temp file + rename 写 JSON/text artifact。

验收命令：

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

结果：29 test files / 96 tests passed；typecheck passed；diff check passed。

保留边界：

- `contextContractForWorkItem` 目前是声明式 contract，还没有做 runtime 文件读取拦截。
- `MERGE_USER_EDITED_NOTE` 仍不自动执行；预算为 0，后续需要独立设计人工/重规划路径。
- 未执行真实 DeepSeek / Claude Code 调用。
- MiMo 已执行 opt-in real smoke：旧 fixture 模型名被真实 API 拒绝，`mimo-v2.5` 真实 smoke 通过；完整 organize workflow 仍未用真实 MiMo 发布 workspace。

## File Structure

- Create `src/agents/work-item-agent-runtime.ts`: shared runtime interfaces, budget defaults, loop report type, and loop artifact writer.
- Create `src/agents/work-item-runtime-boundary.ts`: runtime boundary that validates context/result/report before patch or quality artifacts become publishable.
- Modify `src/agents/note-quality-loop.ts`: emit or adapt to `work-item-agent-loop.v1`.
- Modify `src/agents/mock-note-agent.ts`: use shared loop writer and keep existing `PatchBundle` output.
- Modify `src/agents/mock-topic-index-agent.ts`: return loop report evidence for topic index generation.
- Modify `src/agents/quality-review-agent.ts`: emit structured warning findings with evidence.
- Modify `src/domain/planning/work-item.ts`: add durable attempt metadata (`failureSource`, `failureReason`, `retryable`).
- Modify `src/runtime/langgraph/nodes/execute-phase-node.ts`: delegate work item execution through the runtime boundary.
- Modify `src/runtime/langgraph/nodes/report-node.ts`: aggregate agent loop artifacts into `eval.json` and `report.md`.
- Add `tests/unit/work-item-runtime-boundary.test.ts`: invalid output, invalid report, budget exceeded, context violation.
- Add `tests/unit/work-item-agent-runtime.test.ts`: loop schema, budgets, writer behavior.
- Modify `tests/unit/llm-provider.test.ts`: note agent artifact schema migration.
- Add or modify `tests/unit/quality-review-agent.test.ts`: structured warning findings.
- Modify `tests/unit/mock-agents.test.ts`: topic index/MOC loop evidence.
- Modify `tests/integration/langgraph-workflow.test.ts`: exact loop artifact coverage and eval aggregation.
- Modify `tests/integration/provider-failure.test.ts`: invalid loop output and non-retryable loop failure.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`: delivery report after implementation.

## Task 0: Runtime Boundary And Durable Failure Contract

- [ ] **Step 1: Write RED tests for invalid loop output**

Create `tests/unit/work-item-runtime-boundary.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { runWorkItemRuntimeBoundary } from "../../src/agents/work-item-runtime-boundary.js";

const workItem = {
  id: "rewrite-tools",
  type: "REWRITE_TOPIC_NOTE",
  phase: "phase-a-notes",
  status: "PLANNED",
  sourcePaths: ["raw/tools/a.md"],
  targetPaths: ["knowledge-base/topics/tools/a.md"],
  baseShas: {},
  risk: "LOW",
  requiresApproval: false,
  reason: "test",
  attempts: []
} as const;

describe("work item runtime boundary", () => {
  it("rejects invalid loop output before writing a publishable patch", async () => {
    const result = await runWorkItemRuntimeBoundary({
      runId: "run-loop",
      workspaceRoot: "/tmp/workspace",
      workItem,
      runtime: {
        type: "REWRITE_TOPIC_NOTE",
        async buildContext() {
          return { ok: true };
        },
        async runLoop() {
          return {
            output: null,
            report: {
              schemaVersion: "work-item-agent-loop.v1",
              runId: "run-loop",
              workItemId: "rewrite-tools",
              workItemType: "REWRITE_TOPIC_NOTE",
              agentNode: "note",
              status: "SUCCEEDED",
              budget: { maxIterations: 2, maxProviderCalls: 1, timeoutMs: 30000 },
              usage: { iterations: 1, providerCalls: 1 },
              steps: [],
              repairedIssues: [],
              remainingIssues: []
            }
          };
        }
      }
    });

    expect(result.status).toBe("FAILED_EXECUTOR");
    expect(result.latestAttempt).toMatchObject({
      failureSource: "loop",
      failureReason: "LOOP_OUTPUT_SCHEMA_INVALID",
      retryable: false
    });
    expect(result.publishableArtifactWritten).toBe(false);
  });
});
```

Expected: FAIL because the boundary does not exist.

- [ ] **Step 2: Add durable attempt metadata**

Update `src/domain/planning/work-item.ts` attempt type:

```ts
failureSource?: "provider" | "loop" | "validator" | "merge" | "context";
failureReason?: string;
retryable?: boolean;
```

Existing success attempts should remain valid without metadata.

- [ ] **Step 3: Implement boundary skeleton**

Create `src/agents/work-item-runtime-boundary.ts` with `runWorkItemRuntimeBoundary`.

It must:

- call `buildContext`;
- call `runLoop`;
- validate loop report shape;
- validate output shape;
- reject invalid output before writing patch/quality artifacts;
- return latest attempt metadata.

Do not integrate into `execute-phase-node` in this task.

- [ ] **Step 4: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/work-item-runtime-boundary.test.ts
```

Expected: PASS.

## Task 1: Unified Loop Artifact Contract

- [ ] **Step 1: Write RED tests**

Add `tests/unit/work-item-agent-runtime.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { buildLoopReport, budgetForWorkItemType } from "../../src/agents/work-item-agent-runtime.js";

describe("work item agent runtime", () => {
  it("uses bounded budgets by work item type", () => {
    expect(budgetForWorkItemType("REWRITE_TOPIC_NOTE")).toEqual({
      maxIterations: 2,
      maxProviderCalls: 1,
      timeoutMs: 30000
    });
    expect(budgetForWorkItemType("MAINTAIN_TOPIC_INDEX")).toEqual({
      maxIterations: 1,
      maxProviderCalls: 0,
      timeoutMs: 5000
    });
    expect(budgetForWorkItemType("MERGE_USER_EDITED_NOTE")).toEqual({
      maxIterations: 0,
      maxProviderCalls: 0,
      timeoutMs: 0
    });
  });

  it("builds work-item-agent-loop.v1 reports", () => {
    const report = buildLoopReport({
      runId: "run-loop",
      workItemId: "rewrite-tools",
      workItemType: "REWRITE_TOPIC_NOTE",
      agentNode: "note",
      status: "SUCCEEDED",
      budget: { maxIterations: 2, maxProviderCalls: 1, timeoutMs: 30000 },
      usage: { iterations: 2, providerCalls: 1 },
      steps: [],
      repairedIssues: ["TOPIC_NOTE_WEAK_RELATIONS"],
      remainingIssues: [],
      outputRef: { kind: "patch", path: "patches/rewrite-tools.patch.json" }
    });

    expect(report.schemaVersion).toBe("work-item-agent-loop.v1");
    expect(report.repairedIssues).toEqual(["TOPIC_NOTE_WEAK_RELATIONS"]);
  });
});
```

Expected: FAIL because runtime helpers do not exist.

- [ ] **Step 2: Implement runtime types and helpers**

Create `src/agents/work-item-agent-runtime.ts` with:

- `WorkItemAgentLoopReport`;
- `budgetForWorkItemType`;
- `buildLoopReport`;
- `writeLoopReport(store, report)`;
- runtime schema guard for loop report shape.

- [ ] **Step 3: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/work-item-agent-runtime.test.ts
```

Expected: PASS.

## Task 2: Note Agent Loop Artifact Migration

- [ ] **Step 1: Write RED assertions**

Update `tests/unit/llm-provider.test.ts` to expect:

- `schemaVersion === "work-item-agent-loop.v1"`;
- `agentNode === "note"`;
- `budget.maxProviderCalls === 1`;
- `outputRef.kind === "patch"`;
- success attempts keep existing status and do not need failure metadata.

Expected current failure: artifact is `note-quality-loop.v1` and lacks budget/outputRef.

- [ ] **Step 2: Update note agent**

`runMockNoteAgent` should:

- keep using `runNoteQualityLoop`;
- wrap result into `WorkItemAgentLoopReport`;
- write `agent-loop/<workItemId>.json`;
- keep `PatchBundle` content and `contentSha` semantics unchanged.

- [ ] **Step 3: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/llm-provider.test.ts tests/unit/note-quality-loop.test.ts
```

Expected: PASS.

## Task 3: Context Contracts And Runtime Registry

- [ ] **Step 1: Write RED tests for context scope**

Add tests that assert:

- note agent context only includes `workItem.sourcePaths` and target sha;
- topic index context never includes raw paths;
- MOC context only includes topic index paths;
- `MERGE_USER_EDITED_NOTE` returns manual/replan status in this stage.

Expected current failure: no context contract exists.

- [ ] **Step 2: Implement context contracts**

Add per-work-item context contracts in `work-item-agent-runtime.ts` or a small adjacent module.

Rules:

- Note: source paths + target sha only.
- Topic index: published notes for the topic only.
- MOC: topic index paths only.
- Quality review: published notes + validation artifacts.
- No runtime writes domain artifacts except through boundary.

- [ ] **Step 3: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/work-item-agent-runtime.test.ts
```

Expected: PASS.

## Task 4: Topic Index And MOC Loop Evidence

- [ ] **Step 1: Write RED integration assertions**

Update `tests/integration/langgraph-workflow.test.ts` to assert a successful workflow writes loop artifacts for exact executed ids:

- every Phase A note work item;
- every `MAINTAIN_TOPIC_INDEX`;
- `MAINTAIN_MOC`;
- `QUALITY_REVIEW`.

Expected current failure: only note work items write loop artifacts.

- [ ] **Step 2: Add deterministic loop reports**

Update execution helpers for `MAINTAIN_TOPIC_INDEX`, `MAINTAIN_MOC`, and `QUALITY_REVIEW` to write loop reports with:

- `maxProviderCalls: 0`;
- `steps` showing collect/draft/self-check;
- `outputRef` pointing to patch or quality artifact.

- [ ] **Step 3: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 5: Structured Quality Findings

- [ ] **Step 1: Write RED tests**

Add `tests/unit/quality-review-agent.test.ts`:

```ts
expect(findings.findings[0]).toMatchObject({
  issue: "TOPIC_NOTE_WEAK_RELATIONS",
  severity: "warning",
  evidence: expect.any(String)
});
```

Expected current failure: `QualityFindings` is `issues: string[]`.

- [ ] **Step 2: Implement structured warning findings**

Update `src/agents/quality-review-agent.ts`:

```ts
interface QualityFinding {
  issue: string;
  severity: "warning";
  targetPath?: string;
  evidence: string;
}
```

Keep backward-compatible `issues` derived from findings for `reportNode`.

- [ ] **Step 3: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/quality-review-agent.test.ts tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 6: Eval And Report Aggregation

- [ ] **Step 1: Write RED integration assertions**

In workflow integration, read `eval.json` and assert:

```ts
expect(evalReport.agentLoop.total).toBe(executedWorkItemIds.length);
expect(evalReport.agentLoop.succeeded).toBe(evalReport.agentLoop.total);
expect(evalReport.agentLoop.missingLoopArtifacts).toEqual([]);
expect(evalReport.agentLoop.corruptLoopArtifacts).toEqual([]);
expect(evalReport.agentLoop.repairedIssues.TOPIC_NOTE_WEAK_RELATIONS).toBeGreaterThanOrEqual(1);
expect(evalReport.agentLoop.providerCalls).toBeGreaterThanOrEqual(1);
```

Expected current failure: no exact `agentLoop` section.

- [ ] **Step 2: Implement aggregation**

`reportNode` should read exact executed work items and `agent-loop/*.json`, deriving:

- total;
- succeeded;
- failed;
- missingLoopArtifacts;
- corruptLoopArtifacts;
- repairedIssues counts;
- remainingIssues counts;
- providerCalls.

Write summary into `eval.json` and `report.md`.

- [ ] **Step 3: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: PASS.

## Task 7: Artifact Corruption And Resume Semantics

- [ ] **Step 1: Add RED tests for missing/corrupt loop artifacts**

Add integration tests that:

- run a successful workflow;
- delete one `agent-loop/<id>.json`;
- rerun report aggregation or resume command;
- expect `eval.agentLoop.missingLoopArtifacts` to include that id.

Add a corrupt JSON fixture:

```text
.agent-runs/<runId>/agent-loop/<id>.json = "{"
```

Expect:

- `eval.agentLoop.corruptLoopArtifacts` includes id;
- corrupt artifact is not counted as success;
- publish/resume skip is still based on patch/work-item/workspace sha, not loop artifact.

- [ ] **Step 2: Add RED test for non-retryable loop failure**

Create a harness runtime that returns invalid loop output. Run same `runId` twice and assert:

- latest attempt has `failureSource: "loop"`;
- `failureReason: "LOOP_OUTPUT_SCHEMA_INVALID"`;
- `retryable: false`;
- second run does not retry the failed work item automatically.

- [ ] **Step 3: Implement report/resume behavior**

Update report aggregation and resume inspector call sites to:

- read exact executed work item ids from plan/work-items;
- detect missing/corrupt loop artifacts;
- preserve existing publish skip decisions;
- use latest attempt retryability for retry decisions.

- [ ] **Step 4: Verify**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/langgraph-workflow.test.ts tests/integration/provider-failure.test.ts
```

Expected: PASS.

## Task 8: Full Verification And Delivery

- [ ] **Step 1: Run focused suite**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/work-item-runtime-boundary.test.ts tests/unit/work-item-agent-runtime.test.ts tests/unit/note-quality-loop.test.ts tests/unit/llm-provider.test.ts tests/unit/quality-review-agent.test.ts tests/unit/mock-agents.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/provider-failure.test.ts
```

- [ ] **Step 2: Run full suite**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
```

- [ ] **Step 3: Typecheck**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
```

- [ ] **Step 4: Whitespace check**

```bash
git diff --check
```

- [ ] **Step 5: Update delivery report**

Update `docs/reports/runtime-work-item-execution-resume-delivery.md` with:

- delivered capability;
- exact test counts;
- real external call boundary;
- review focus.

## Self Review

- Spec coverage: covers runtime boundary, durable attempt metadata, unified loop artifact, budgets, context contracts, note/topic-index/MOC/quality loop evidence, structured warning findings, exact eval/report aggregation, corrupt/missing artifact behavior, and non-retryable loop failure.
- Placeholder scan: no TODO/TBD placeholders.
- Type consistency: names match the hardened spec: `WorkItemAgentLoopReport`, `work-item-agent-loop.v1`, `agentLoop`, `QualityFinding`, `failureSource`, `failureReason`, `retryable`.
