# Change: SDK And Tool Surface

> Date: 2026-06-13
> Status: implemented in Phase 27
> Scope: public SDK API, CLI wrapper boundary, internal tool metadata

## Context

The workflow could already run through LangGraph and CLI, but backend integration still had two bad options:

- import `runtime/langgraph/*` directly and bind to internal state;
- shell out to CLI and parse an implementation-shaped `GraphState`.

After methodology became part of the workflow contract, backend callers also need a stable request/result shape that preserves `methodologyId`.

## Decision

Introduce a public SDK facade:

- `createKnowledgeWorkflowAgent()`;
- `runOrganize(request)`;
- `inspectRun(request)`.

The SDK is exported from `src/index.ts`. CLI `organize` now calls SDK `runOrganize`; CLI `resume` calls SDK `inspectRun`.

Also introduce internal tool metadata:

- tools are classified by risk;
- direct workspace write tools such as `patch.publish` are `INTERNAL_ONLY`;
- the registry is metadata only in this phase and is not exposed to free-form LLM execution.

## Consequences

- Backend integration should import the public SDK instead of LangGraph nodes or CLI scripts.
- SDK response includes `artifactRoot`, `methodologyId`, `planPath`, `reportPath`, and `lastError`.
- `inspectRun` is artifact-only and does not execute workflow.
- Future backend API controllers can remain thin adapters over SDK commands.

## Affected Docs

- `docs/architecture/sdk-tool-surface-spec.md`
- `docs/superpowers/plans/2026-06-12-sdk-tool-surface.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- `docs/architecture/README.md`
