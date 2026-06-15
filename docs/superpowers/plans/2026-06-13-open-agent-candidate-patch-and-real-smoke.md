# Phase 30 Plan: Open Agent Candidate Patch And Real Smoke

**Goal:** Extend the knowledge-scoped open agent runtime so it can propose candidate patches without publishing, expose a fixed workflow handoff for confirmed writes, and provide a secret-safe MiMo real-call smoke/eval path.

**Architecture:** Keep open agent as a policy-gated SDK runtime. It may write only `.agent-runs/open-agent` audit artifacts. Workspace writes remain delegated to fixed workflows and `PatchBundle -> MergeGuard -> Validator -> Publisher`.

## Tasks

1. Document Phase 30 spec/change/report updates.
2. Add failing tests:
   - candidate patch proposal is generated but not written to `knowledge-base`;
   - report records tool calls and grounding refs;
   - `handleCommand` confirmation includes fixed workflow handoff;
   - SDK exports open-agent MiMo smoke helper;
   - CLI reads MiMo API key from stdin without printing it and skips without `--execute-real`;
   - injected-fetch MiMo smoke passes and produces open-agent artifact.
3. Implement `CANDIDATE_PATCH` in `OpenAgentRuntime`.
4. Add `FixedWorkflowHandoff` to command confirmation.
5. Add `runOpenAgentRealSmoke` helper and `src/cli/open-agent-smoke.ts`.
6. Export new public types from SDK/index.
7. Run focused tests, then full verification.

## Validation

```text
npm test -- tests/unit/open-agent-runtime.test.ts tests/unit/command-router.test.ts tests/unit/sdk.test.ts tests/unit/open-agent-real-smoke.test.ts
npm test
npm run typecheck
git diff --check
```

## Review Focus

- Correctness: candidate proposal content and artifact contract.
- Safety: no direct workspace publish, no token leakage.
- Boundary: confirmation handoff remains a workflow handoff, not an implicit write.
- Extensibility: future LLM-backed open agent can replace deterministic output without changing policy shape.
