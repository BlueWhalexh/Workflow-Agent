# Hybrid Agent Command Router Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a public SDK command router that separates fixed workflows from open-ended agent tasks without hard-coding every future business scenario.

**Architecture:** Add `handleCommand` above the existing SDK facade. The router emits execution lanes, risk policy, and capability envelopes; only high-confidence fixed workflow commands can execute `runOrganize`, while open tasks return read/draft envelopes and workspace writes require confirmation.

**Tech Stack:** TypeScript, Vitest, existing SDK facade, existing internal tool metadata registry.

---

## File Structure

- Create `src/sdk/command-router.ts`: command route types, deterministic classifier, `handleCommand`.
- Modify `src/sdk/knowledge-workflow-agent.ts`: expose `handleCommand` on `KnowledgeWorkflowAgent`.
- Modify `src/index.ts`: export command router API and types.
- Add `tests/unit/command-router.test.ts`: route behavior and execution gate tests.
- Modify `docs/architecture/README.md`: add spec to current truth order.
- Add `docs/changes/2026-06-13-hybrid-agent-command-router.md`: architecture change record.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`: append Phase 28.

## Task 1: Command Router Contract

- [ ] Write RED tests in `tests/unit/command-router.test.ts`:
  - `handleCommand` exported from `src/index.ts`;
  - `createKnowledgeWorkflowAgent().handleCommand` exists;
  - open read request returns `OPEN_AGENT_TASK` / `READ_ONLY`;
  - draft request returns `OPEN_AGENT_TASK` / `DRAFT_ONLY`;
  - write-ish ambiguous request returns `CONFIRMATION_REQUIRED` / `WORKSPACE_WRITE`.
- [ ] Run focused test and expect export/module failures:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/command-router.test.ts
```

- [ ] Implement `src/sdk/command-router.ts` with `CommandRoute`, `OpenAgentTaskEnvelope`, `HandleCommandRequest`, `HandleCommandResult`, and deterministic routing.
- [ ] Export the API from `src/index.ts`.
- [ ] Re-run focused test and expect pass.

## Task 2: Fixed Workflow Execution Gate

- [ ] Add tests:
  - fixed workflow route with `execute=false` returns route but no `workflow`;
  - fixed workflow route with `execute=true` calls organize and returns `workflow.status`;
  - unknown methodology throws before routing result.
- [ ] Implement `execute=true` path by calling existing `runOrganize`.
- [ ] Re-run focused test and expect pass.

## Task 3: Tool Policy Guard

- [ ] Add tests that open agent envelopes:
  - include read tools such as `workspace.scan` and `artifact.readEval`;
  - never include `patch.publish`;
  - list `patch.publish` in blocked tools.
- [ ] Implement tool filtering from `internalTools`.
- [ ] Re-run focused test and expect pass.

## Task 4: Agent SDK Integration

- [ ] Add tests in `tests/unit/sdk.test.ts` or `command-router.test.ts`:
  - agent object supports `handleCommand`;
  - default config `defaultMethodologyId` is honored.
- [ ] Modify `createKnowledgeWorkflowAgent` to bind `handleCommand`.
- [ ] Re-run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/command-router.test.ts tests/unit/sdk.test.ts
```

## Task 5: Docs And Verification

- [ ] Update architecture README and change docs.
- [ ] Update delivery report with Phase 28.
- [ ] Run focused tests:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/command-router.test.ts tests/unit/sdk.test.ts tests/unit/internal-tool-registry.test.ts
```

- [ ] Run full verification:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

## Self Review

- Spec coverage: covers fixed workflow, open agent task, confirmation path, tool policy, SDK export, execution gate, docs, and verification.
- Placeholder scan: no unresolved placeholders.
- Type consistency: `ExecutionLane`, `CommandRisk`, `HandleCommandRequest`, `HandleCommandResult`, and `OpenAgentTaskEnvelope` match the spec.
