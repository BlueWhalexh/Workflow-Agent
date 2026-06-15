# Change: Knowledge-scoped Open Agent Runtime

> Date: 2026-06-13
> Status: implemented in Phase 29
> Scope: open agent runtime, SDK command execution, agent-readable artifacts, write safety boundary

## Context

The hybrid command router separated fixed workflows, open tasks, and confirmation-required writes. It still only returned an envelope for open tasks, which was not mature enough for a Claude Code-like general agent direction.

The product needs a knowledge-scoped general agent baseline:

- open requests should produce answers or draft artifacts;
- the runtime should gather workspace/knowledge context;
- execution should be auditable through artifacts;
- open tasks must not directly publish workspace writes.

## Decision

Introduce `OpenAgentRuntime`:

- `runOpenAgentTask` is exported from the public SDK;
- `handleCommand` now executes open tasks and returns `openAgent`;
- open tasks write `.agent-runs/open-agent/<taskId>.json`;
- the artifact records plan/context/output/self-check steps;
- direct `patch.publish` in allowed tools causes `FAILED_POLICY`.

The first runtime is deterministic. It does not call a real LLM and does not add dependencies.

## Consequences

- The system now has a minimal general-agent loop baseline, not just a router.
- Future LLM-based open agents can replace deterministic output while preserving the artifact contract.
- Workspace writes remain protected: ambiguous write requests still return confirmation, and open runtime cannot publish.
- Fixed organize workflow remains the confirmed write path.

## Affected Docs

- `docs/architecture/knowledge-scoped-open-agent-runtime-spec.md`
- `docs/superpowers/plans/2026-06-13-knowledge-scoped-open-agent-runtime.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- `docs/architecture/README.md`
