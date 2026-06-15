# Open Agent Source Materialization Hardening Plan

> Status: implemented. Delivery evidence is archived in `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Goal

Harden provider-backed synthesis so final answer, draft, and candidate patch artifacts remain self-contained and auditable even when the provider uses numbered citations like `[1]` instead of exact path strings.

## Scope

- Update `synthesize-node.ts` only for deterministic source materialization.
- Extend `open-agent-graph-nodes.test.ts` with RED tests for draft and candidate outputs.
- Update provider-backed synthesis architecture spec and delivery report.
- Do not change `OpenAgentSynthesisOutput`, public SDK contracts, backend adapter contracts, provider schemas, or workspace write rules.

## Execution Steps

1. RED tests:
   - Add draft provider output with `Draft only` and numbered citation but no exact source path.
   - Add candidate provider output with numbered citation but no exact source path.
   - Expected RED: final content is missing exact refs or self-check fails.
2. Minimal implementation:
   - Reuse deterministic source materialization for draft and candidate outputs.
   - Keep strict self-check unchanged.
3. Focused verification:
   - `npm test -- tests/unit/open-agent-graph-nodes.test.ts`
   - `npm test -- tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts tests/integration/agent-sdk-backend-adapter.test.ts`
4. Full verification:
   - `npm test`
   - `npm run typecheck`
   - `git diff --check`
   - token pattern scan.
5. Archive:
   - Mark this plan complete with verification evidence.
   - Update delivery report with fake/injected/real evidence boundaries.

## Acceptance

- Provider answer, draft, and candidate outputs with numbered citations produce final content containing exact source refs.
- Candidate patch remains non-publishable and does not write target workspace files.
- Real provider evidence remains clearly separated from fake/injected tests.

## Archive

Implemented in Phase 38.

Verification:

- RED: `npm test -- tests/unit/open-agent-graph-nodes.test.ts`
  - failed for draft/candidate numbered citation cases because final content used `Sources:` instead of Markdown `## Sources`。
- Focused GREEN: `npm test -- tests/unit/open-agent-graph-nodes.test.ts`
  - 1 test file / 13 tests passed。
- Focused regression: `npm test -- tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts tests/integration/agent-sdk-backend-adapter.test.ts`
  - 3 test files / 19 tests passed。

Full verification is recorded in the delivery report.
