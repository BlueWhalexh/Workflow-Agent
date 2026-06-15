# Change: Hybrid Agent Command Router

> Date: 2026-06-13
> Status: implemented in Phase 28
> Scope: SDK command routing, open agent task envelope, workspace write confirmation policy

## Context

The product should support more than fixed workflows. User examples such as summarizing knowledge, generating question lists, drafting materials, or depositing generated content are only examples, not a closed set of intents.

If the system hard-codes every future scenario into a router, it will become brittle and difficult to extend. At the same time, workspace writes must remain controlled by fixed workflows, confirmation, and validator boundaries.

## Decision

Introduce a hybrid command router in the public SDK:

- fixed workflow lane for high-confidence standardized workflows;
- open agent task lane for read-only or draft-only requests;
- confirmation-required lane for ambiguous workspace write requests.

The router returns a command envelope with lane, risk, capability id, allowed tools, blocked tools, and optional workflow result. Open agent tasks do not execute LLMs or write workspace in this phase.

## Consequences

- Backend can accept natural-language commands without importing LangGraph internals.
- New user scenarios can enter `OPEN_AGENT_TASK` without adding a scenario-specific intent.
- Workspace writes still require fixed workflow execution or confirmation.
- Future LLM-based classifiers can replace the deterministic classifier without changing the SDK contract.

## Affected Docs

- `docs/architecture/hybrid-agent-command-router-spec.md`
- `docs/superpowers/plans/2026-06-13-hybrid-agent-command-router.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- `docs/architecture/README.md`
