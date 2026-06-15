# Knowledge-scoped Open Agent Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a minimal open agent runtime that can plan, gather knowledge workspace context, produce answer or draft artifacts, record agent-readable reports, and preserve the no-direct-publish safety boundary.

**Architecture:** Add a deterministic `OpenAgentRuntime` behind the existing SDK/router. `OPEN_AGENT_TASK` commands run this runtime and write `.agent-runs/open-agent/<taskId>.json`; workspace writes still require confirmation or fixed workflow.

**Tech Stack:** TypeScript, Vitest, existing workspace inventory scanner, existing methodology registry, existing SDK command router.

---

## File Structure

- Create `src/sdk/open-agent-runtime.ts`: runtime types, policy guard, deterministic execution, artifact writing.
- Modify `src/sdk/command-router.ts`: return `openAgent` result for `OPEN_AGENT_TASK`.
- Modify `src/sdk/knowledge-workflow-agent.ts`: expose `runOpenAgentTask`.
- Modify `src/index.ts`: export runtime API/types.
- Add `tests/unit/open-agent-runtime.test.ts`: runtime answer/draft/policy artifact behavior.
- Modify `tests/unit/command-router.test.ts`: `handleCommand` now executes open runtime and preserves confirmation path.
- Modify `tests/unit/sdk.test.ts`: SDK agent exposes `runOpenAgentTask`.
- Modify docs:
  - `docs/architecture/README.md`
  - `docs/changes/2026-06-13-knowledge-scoped-open-agent-runtime.md`
  - `docs/reports/runtime-work-item-execution-resume-delivery.md`

## Task 1: Open Runtime Contract

- [ ] Write RED tests in `tests/unit/open-agent-runtime.test.ts`:
  - read-only request returns answer and writes artifact;
  - draft-only request returns draft artifact and writes artifact;
  - report has `PLAN`, `GATHER_CONTEXT`, `PRODUCE_OUTPUT`, `SELF_CHECK`;
  - artifact context includes methodology and workspace paths;
  - `patch.publish` in allowed tools returns `FAILED_POLICY`.
- [ ] Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/open-agent-runtime.test.ts
```

Expected: FAIL because runtime module does not exist.

- [ ] Implement `src/sdk/open-agent-runtime.ts`.
- [ ] Re-run focused test and expect pass.

## Task 2: SDK And Router Integration

- [ ] Add RED tests:
  - `handleCommand` read request returns `openAgent.answer`;
  - `handleCommand` draft request returns `openAgent.draftArtifact`;
  - confirmation write request still does not run open runtime;
  - `createKnowledgeWorkflowAgent().runOpenAgentTask` exists.
- [ ] Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/command-router.test.ts tests/unit/sdk.test.ts
```

- [ ] Wire runtime into `command-router.ts`.
- [ ] Wire runtime into `knowledge-workflow-agent.ts` and `src/index.ts`.
- [ ] Re-run focused tests and expect pass.

## Task 3: Tool Policy And Write Boundary

- [ ] Add tests proving:
  - open runtime allowed tools contain read tools only for read requests;
  - `patch.publish` is blocked;
  - no file under `knowledge-base/` is written by open runtime.
- [ ] Implement any missing guards.
- [ ] Re-run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/open-agent-runtime.test.ts tests/unit/command-router.test.ts
```

## Task 4: Docs And Verification

- [ ] Add Phase 29 change doc.
- [ ] Update architecture README.
- [ ] Update delivery report with Phase 29 result.
- [ ] Run focused tests:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/open-agent-runtime.test.ts tests/unit/command-router.test.ts tests/unit/sdk.test.ts tests/unit/internal-tool-registry.test.ts
```

- [ ] Run full verification:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

## Self Review

- Spec coverage: covers plan/context/answer/draft/artifact/policy/router/SDK/docs/verification.
- Placeholder scan: no unresolved placeholders.
- Type consistency: `RunOpenAgentTaskRequest`, `RunOpenAgentTaskResult`, `OpenAgentRunReport`, and `openAgent` match the spec.
