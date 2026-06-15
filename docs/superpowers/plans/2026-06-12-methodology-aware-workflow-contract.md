# Methodology-aware Workflow Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make methodology selection visible and enforceable across plan, work items, validator, eval, report, and CLI.

**Architecture:** Thread a default `lmwiki-v1` methodology id through `GraphState` and `OrganizePlan`. Keep runtime behavior unchanged for default runs, but make validation and reporting consume the methodology registry instead of hidden hard-coded rules.

**Tech Stack:** TypeScript, Vitest, LangGraph state annotations, existing `AgentRunsStore`, existing CLI smoke tests.

---

## File Structure

- Modify `src/domain/planning/plan.ts`: add methodology fields to `OrganizePlan`.
- Modify `src/domain/planning/work-item.ts`: add optional `methodologyId` to work item artifacts.
- Modify `src/domain/planning/organize-planner.ts`: accept `methodologyId`, fail fast through registry, populate plan/work items, derive paths/publish policy from profile.
- Modify `src/runtime/langgraph/state.ts`: add `methodologyId`.
- Modify `src/runtime/langgraph/graph.ts`: accept optional workflow methodology, default to `lmwiki-v1`.
- Modify `src/runtime/langgraph/nodes/plan-node.ts`: pass state methodology to planner and reject mismatched existing plan.
- Modify `src/runtime/langgraph/nodes/execute-phase-node.ts`: pass plan methodology into validator.
- Modify `src/runtime/langgraph/nodes/report-node.ts`: write methodology to eval/report and failed report.
- Modify `src/domain/validation/validator.ts`: read layout, required sections, and blockers from methodology profile.
- Modify `src/cli/organize.ts`: add `--methodology`.
- Modify tests:
  - `tests/unit/planner.test.ts`
  - `tests/unit/validation.test.ts`
  - `tests/integration/langgraph-workflow.test.ts`
  - `tests/integration/cli-smoke.test.ts`
- Modify docs:
  - `docs/architecture/README.md`
  - `docs/reports/runtime-work-item-execution-resume-delivery.md`

## Task 1: Planner Contract

- [ ] Write failing planner assertions:
  - `plan.methodologyId === "lmwiki-v1"`;
  - `plan.methodologyVersion === "1"`;
  - every work item has `methodologyId === "lmwiki-v1"`;
  - unknown methodology throws before plan is returned.
- [ ] Run `npm test -- tests/unit/planner.test.ts`; expected failure on missing fields/API.
- [ ] Add methodology fields to `OrganizePlan` and `WorkItem`.
- [ ] Update `createOrganizePlan` to accept optional `methodologyId`, resolve the profile, and use profile layout/policy for target paths and MOC.
- [ ] Re-run `npm test -- tests/unit/planner.test.ts`; expected pass.

## Task 2: Validator Uses Methodology

- [ ] Write failing validator assertions:
  - placeholder blocker `TODO` is blocked through profile;
  - unknown methodology throws `Unknown knowledge methodology: unknown`;
  - default organized note remains allowed.
- [ ] Run `npm test -- tests/unit/validation.test.ts`; expected failure on `TODO` and unknown id behavior.
- [ ] Refactor `validateBundle` to accept optional `methodologyId`, resolve profile, and derive raw/rules/topic paths plus required sections from profile.
- [ ] Re-run `npm test -- tests/unit/validation.test.ts`; expected pass.

## Task 3: Workflow Artifacts

- [ ] Write failing integration assertions in `langgraph-workflow.test.ts`:
  - `plan.json.methodologyId` and `methodologyVersion`;
  - `eval.json.methodology`;
  - `report.md` contains `Methodology: lmwiki-v1@1`.
- [ ] Run `npm test -- tests/integration/langgraph-workflow.test.ts`; expected failure on missing artifact fields.
- [ ] Add `methodologyId` to `GraphState` and `runOrganizeWorkflow` input.
- [ ] Pass methodology from graph to planner.
- [ ] Pass plan methodology to validator during execution.
- [ ] Add methodology summary to eval/report.
- [ ] Re-run `npm test -- tests/integration/langgraph-workflow.test.ts`; expected pass.

## Task 4: CLI Methodology Gate

- [ ] Write failing CLI smoke assertion:
  - `--methodology unknown` fails with unknown methodology message.
- [ ] Run `npm test -- tests/integration/cli-smoke.test.ts`; expected failure because CLI ignores the flag.
- [ ] Add `--methodology` parsing and call registry before `runOrganizeWorkflow`.
- [ ] Pass methodology to workflow input.
- [ ] Re-run `npm test -- tests/integration/cli-smoke.test.ts`; expected pass.

## Task 5: Phase Verification And Docs

- [ ] Run focused regression:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/planner.test.ts tests/unit/validation.test.ts tests/unit/methodology.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts
```

- [ ] Run full verification:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
git diff --check
```

- [ ] Update delivery report with Phase 26:
  - methodology visible in plan/eval/report;
  - validator consumes profile;
  - CLI unknown methodology blocked before workflow;
  - test counts and boundary notes.

## Self Review

- Spec coverage: tasks cover planner, work item artifact, graph state, validator, report/eval, CLI, tests, and docs.
- Placeholder scan: no unresolved implementation placeholders.
- Type consistency: `methodologyId`, `methodologyVersion`, `methodology`, `KnowledgeMethodology`, and `lmwiki-v1` are used consistently.
