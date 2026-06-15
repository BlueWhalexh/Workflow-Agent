# Real Provider Quality Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable, secret-safe real provider workflow smoke harness with a deterministic promotion gate.

**Architecture:** Keep real provider execution opt-in and outside default tests. Reuse `runOrganizeWorkflow` against a disposable fixture workspace, then aggregate existing `.agent-runs` artifacts into a stable `real-provider-workflow-smoke.v1` summary and quality gate.

**Tech Stack:** TypeScript, Node/tsx CLI, Vitest, existing LangGraph workflow, existing `.agent-runs` filesystem artifacts.

---

## File Structure

- Create `src/runtime/provider/real-workflow-smoke.ts`: reusable real workflow smoke runner and summary builder.
- Create `src/cli/real-workflow-smoke.ts`: CLI wrapper with `--api-key-stdin`, `--execute-real`, `--provider`, `--base-url`, `--model`.
- Add `tests/unit/real-workflow-smoke.test.ts`: fake-fetch/fake-provider tests for summary and gate behavior.
- Modify `tests/integration/cli-smoke.test.ts`: verify CLI blocks real execution unless explicitly allowed and does not print API key.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`: append Phase 23 result after implementation.

## Task 1: Summary Contract And Gate

**Files:**
- Create: `src/runtime/provider/real-workflow-smoke.ts`
- Test: `tests/unit/real-workflow-smoke.test.ts`

- [ ] **Step 1: Write RED tests for summary gate**

Create tests that call `evaluateRealWorkflowSmokeSummary` with a passed eval:

```ts
expect(result.qualityGate.allowed).toBe(true);
expect(result.qualityGate.blockers).toEqual([]);
```

And with missing loop artifacts:

```ts
expect(result.qualityGate.allowed).toBe(false);
expect(result.qualityGate.blockers).toContain("AGENT_LOOP_ARTIFACTS_MISSING");
```

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/real-workflow-smoke.test.ts
```

Expected: FAIL because module does not exist.

- [ ] **Step 2: Implement summary types and gate**

Implement:

```ts
export function evaluateRealWorkflowSmokeSummary(summary: RealProviderWorkflowSmokeSummary): RealProviderWorkflowSmokeSummary
```

`RealProviderWorkflowSmokeSummary` must match `docs/architecture/real-provider-quality-harness-spec.md` section 5 exactly.

Gate blockers:

- `WORKFLOW_FAILED`
- `RAW_COVERAGE_INCOMPLETE`
- `NO_PROVIDER_CALLS`
- `AGENT_LOOP_ARTIFACTS_MISSING`
- `AGENT_LOOP_ARTIFACTS_CORRUPT`
- `AGENT_LOOP_BUDGET_EXCEEDED`
- `REMAINING_QUALITY_ISSUES`
- `UNSUPPORTED_REPAIR_ISSUE`

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/real-workflow-smoke.test.ts
```

Expected: PASS.

## Task 2: Disposable Workflow Runner

**Files:**
- Modify: `src/runtime/provider/real-workflow-smoke.ts`
- Test: `tests/unit/real-workflow-smoke.test.ts`

- [ ] **Step 1: Write RED test for temp fixture workflow**

Use injected env/fetch so no real network happens:

```ts
const fakeChatCompletion = {
  model: "mimo-v2.5",
  choices: [
    {
      finish_reason: "stop",
      message: {
        role: "assistant",
        content: "# Smoke\n\n## 摘要\n\nok\n\n## 来源追踪\n\n- raw/smoke.md\n\n## 关键概念\n\n- ok\n\n## 相关链接\n\n暂无相关链接。\n"
      }
    }
  ],
  usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 }
};

const summary = await runRealWorkflowSmoke({
  provider: "mimo-real",
  model: "mimo-v2.5",
  baseUrl: "https://token-plan-cn.xiaomimimo.com/v1",
  apiKey: "test-api-key",
  executeReal: true,
  fetch: async () => new Response(JSON.stringify(fakeChatCompletion), { status: 200 })
});

expect(summary.schemaVersion).toBe("real-provider-workflow-smoke.v1");
expect(summary.workspaceMode).toBe("TEMP_FIXTURE");
expect(summary.realExternalCall).toBe(true);
expect(summary.qualityGate.allowed).toBe(true);
```

Expected: FAIL because runner does not exist.

- [ ] **Step 2: Implement runner**

Runner must:

- create temp workspace;
- copy `tests/fixtures/workspaces/basic-raw-mirror`;
- call `runOrganizeWorkflow`;
- read `eval.json`, `work-items`, `validation`, `agent-loop`;
- build summary;
- never include API key in returned object.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/real-workflow-smoke.test.ts
```

Expected: PASS.

## Task 3: CLI Wrapper

**Files:**
- Create: `src/cli/real-workflow-smoke.ts`
- Modify: `tests/integration/cli-smoke.test.ts`

- [ ] **Step 1: Write RED CLI tests**

Assertions:

- without `--execute-real`, CLI returns `SKIPPED` or exits before real call;
- without `--api-key-stdin`, real provider is blocked;
- stdin API key is not printed to stdout/stderr;
- CLI JSON has `schemaVersion: "real-provider-workflow-smoke.v1"`.

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/cli-smoke.test.ts
```

Expected: FAIL because CLI does not exist.

- [ ] **Step 2: Implement CLI**

Supported flags:

```text
--provider mimo-real|deepseek-real
--execute-real
--api-key-stdin
--base-url <url>
--model <model>
```

Rules:

- missing `--execute-real` returns skipped summary with `realExternalCall=false`;
- missing API key blocks before workflow;
- stdout is JSON only;
- stderr may contain usage errors but never token.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/integration/cli-smoke.test.ts
```

Expected: PASS.

## Task 4: Opt-In Real MiMo Verification

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Run real command manually**

Command:

```bash
printf '%s' "$MIMO_API_KEY" | \
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/node --import tsx src/cli/real-workflow-smoke.ts \
  --provider mimo-real \
  --execute-real \
  --api-key-stdin \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5
```

Expected:

- `status: "PASSED"`
- `realExternalCall: true`
- `qualityGate.allowed: true`
- `providerCalls > 0`

- [ ] **Step 2: Update delivery report**

Record:

- provider/model;
- status;
- provider calls;
- gate result;
- no token recorded.

## Task 5: Full Verification

- [ ] **Step 1: Focused tests**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/real-workflow-smoke.test.ts tests/integration/cli-smoke.test.ts
```

- [ ] **Step 2: Full tests**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
```

- [ ] **Step 3: Typecheck**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
```

- [ ] **Step 4: Diff check**

```bash
git diff --check
```

## Self Review

- Spec coverage: covers reusable real workflow smoke CLI, secret handling, summary contract, quality gate, promotion boundary, and default no-network tests.
- Placeholder scan: no TODO/TBD placeholders.
- Type consistency: names match the spec: `RealProviderWorkflowSmokeSummary`, `real-provider-workflow-smoke.v1`, `qualityGate`, `runRealWorkflowSmoke`.
