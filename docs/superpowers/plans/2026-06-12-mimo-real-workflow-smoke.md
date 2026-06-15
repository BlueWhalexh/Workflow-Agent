# MiMo Real Workflow Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run and document an opt-in real MiMo organize workflow smoke on a disposable fixture workspace.

**Architecture:** Reuse existing `runOrganizeWorkflow` and inject provider runtime dependencies in memory. Keep token handling outside artifacts and avoid changes to default fake/fixture test paths.

**Tech Stack:** TypeScript, Node/tsx, existing LangGraph workflow, existing `.agent-runs` artifacts.

---

## Tasks

- [ ] Create `mimo-real-workflow-smoke-spec.md` defining scenario, safety, acceptance, and boundaries.
- [ ] Execute a real workflow smoke using:
  - temp copy of `tests/fixtures/workspaces/basic-raw-mirror`;
  - provider `mimo-real`;
  - base URL `https://token-plan-cn.xiaomimimo.com/v1`;
  - model `mimo-v2.5`;
  - API key from stdin only.
- [ ] Inspect workflow result, `eval.json`, `report.md`, and one published note target.
- [ ] If real output fails Validator, record the exact blocker and decide whether provider prompt/schema needs tightening.
- [ ] Update delivery report with real workflow smoke result and boundary.
- [ ] Run `npm test`, `npm run typecheck`, and `git diff --check`.

## Status

Completed.

Result:

- Initial real workflow smoke failed at `PATCH_BLOCKED`, exposing non-canonical real-provider headings.
- Deterministic note quality loop now normalizes common Chinese and English heading variants and removes placeholder related links.
- Final real workflow smoke passed:
  - status: `SUCCEEDED_WITH_WARNINGS`;
  - provider: `mimo-real`;
  - model: `mimo-v2.5`;
  - provider calls: `3`;
  - agent loop reports: `8/8`;
  - missing/corrupt/budgetExceeded: `[]`;
  - all planned note/index/MOC work items published;
  - quality review succeeded.

Token handling:

- API key was provided through stdin only.
- No API key/token value was written to code, docs, tests, artifacts, or shell args.
