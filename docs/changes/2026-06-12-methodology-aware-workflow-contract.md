# Change: Methodology-aware Workflow Contract

> Date: 2026-06-12
> Status: implemented in Phase 26
> Scope: workflow runtime contract, validator rules, CLI gate, report/eval evidence

## Context

Phase 25 introduced `KnowledgeMethodology Registry`, but only the note quality loop consumed the default `lmwiki-v1` profile. The workflow artifacts still hid which methodology was used.

That created a traceability gap:

- plan artifacts did not record methodology;
- work items did not identify methodology;
- validator still embedded LMWiki rules directly;
- eval/report could not prove which rule set was applied;
- CLI had no controlled selection point.

## Decision

Promote `methodologyId` to workflow contract:

- `GraphState` carries `methodologyId`;
- `OrganizePlan` records `methodologyId` and `methodologyVersion`;
- work item artifacts record `methodologyId`;
- `validateBundle` resolves a methodology profile and reads layout, required sections, and placeholder blockers from it;
- `eval.json` and `report.md` emit methodology evidence;
- CLI accepts `--methodology lmwiki-v1` and rejects unknown methodologies before workflow execution.

## Consequences

- Default organize behavior remains `lmwiki-v1`.
- Unknown methodology ids fail before workspace writes.
- Later methodology profiles can be introduced by expanding the registry and tests instead of editing planner/validator/report rules in multiple hidden places.
- `methodologyId` is now part of the public runtime contract and should be preserved by future SDK/backend adapters.

## Affected Docs

- `docs/architecture/methodology-aware-workflow-contract-spec.md`
- `docs/superpowers/plans/2026-06-12-methodology-aware-workflow-contract.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- `docs/architecture/README.md`
