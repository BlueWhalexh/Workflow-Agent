# Change: Open Agent Checkpoint Resume Boundary

## Context

Phase 34 moved `OpenAgentGraph` onto LangGraph `StateGraph`. The graph now has native node/edge orchestration, but it does not yet expose a checkpoint boundary like the fixed workflow runner does.

Fixed workflow already has `RuntimeCheckpointStore` and `createMemoryCheckpointStore()` using LangGraph `MemorySaver`. Open-agent should reuse that runtime boundary before adding more provider modes or confirmation handoff flows.

Phase 34 real MiMo `ANSWER_ONLY` smoke is still pending in the current environment because transient MiMo env/token is unavailable. That remains a required evidence gate and must not be replaced by fake/injected smoke.

## Decision

Phase 35 will:

- add optional checkpoint support to `runOpenAgentGraph()`；
- compile `OpenAgentGraph` with the optional LangGraph checkpointer；
- invoke graph with `taskId` as `thread_id`；
- write checkpoint audit metadata into open-agent report；
- keep provider object, provider dependencies, tokens, and raw provider payload outside graph state/report/checkpoint metadata；
- document that `MemorySaver` proves same-process checkpoint integration only, not durable cross-process resume。

## Consequences

- Future confirmation/resume flows can build on a stable StateGraph checkpoint boundary.
- The open-agent runner aligns with fixed workflow runtime infrastructure.
- Public request type gains an optional field, so implementation requires explicit review of SDK surface impact.
- Durable storage remains a later adapter phase.

## Affected Docs

- `docs/architecture/open-agent-checkpoint-resume-spec.md`
- `docs/superpowers/plans/2026-06-13-open-agent-checkpoint-resume.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md` after implementation
- `docs/architecture/runtime-phase-sop.md` after implementation

## Follow-up Phase

After Phase 35:

- durable file/SQLite checkpointer adapter；
- real MiMo smoke for `DRAFT_ARTIFACT` and `CANDIDATE_PATCH` modes；
- confirmation UI/backend handoff integration for candidate patch acceptance。
