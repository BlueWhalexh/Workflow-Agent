# Change: Open Agent StateGraph Runner And Phase SOP

## Context

Phase 33 proved real MiMo can drive `OpenAgentGraph` plan, action, and synthesis. The graph now performs three provider calls and produces a grounded provider-backed answer without writing workspace targets.

The remaining architecture gap is that `OpenAgentGraph` is still implemented as a hand-written sequential runner. That was acceptable while stabilizing provider contracts, but the project architecture already states LangGraph should own runtime orchestration while Domain Core owns facts and safety.

The operating gap is that each phase still depends on the user restating SOP details: TDD cadence, real-smoke handling, redaction checks, token search, no-write checks, and report updates.

## Decision

Phase 34 will:

- migrate `OpenAgentGraph` internal orchestration to LangGraph `StateGraph`；
- keep public SDK/result contracts stable；
- keep provider dependencies outside graph state；
- keep workspace writes impossible from open agent；
- update `docs/architecture/runtime-phase-sop.md` into the default reusable phase execution protocol。

## Consequences

- Future phases can build on StateGraph-native conditional edges, checkpoint boundaries, and trace correlation.
- The open-agent runner will better match the repository's LangGraph-first / Domain-pure architecture.
- Real provider testing becomes a standard phase gate rather than a one-off prompt.
- Local development may use transient process env for real provider keys, but real secrets remain forbidden in repo files, artifacts, fixtures, snapshots, stdout/stderr, and docs.

## Affected Docs

- `docs/architecture/open-agent-stategraph-runner-and-phase-sop-spec.md`
- `docs/superpowers/plans/2026-06-13-open-agent-stategraph-runner-and-phase-sop.md`
- `docs/architecture/runtime-phase-sop.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md` after implementation

## Follow-up Phase

After Phase 34, likely next phases:

- durable open-agent checkpoint/resume semantics；
- real MiMo smoke for `DRAFT_ARTIFACT` and `CANDIDATE_PATCH` modes；
- confirmation UI/backend handoff integration for candidate patch acceptance。
