# Phase 0 Completion Audit

Phase: 0
Task: P0-T1, P0-T2, P0-T3, P0-T4, P0-T5, P0-T6, P0-T7, P0-T8
Evidence label: static + unit/mock + typecheck + manual/source-audit
Constitution: P1, P2, P3, P5, P6, P7, P8, P9, P10, P12

This audit checks Phase 0 against `docs/plans/mvp-release.md` and the Phase 0 launch prompt. It is intentionally conservative: reviewer approval and future phase gates are not claimed as complete.

## Requirement Matrix

| Requirement | Evidence | Status |
| --- | --- | --- |
| P0-T1 domain baseline: `Workspace`, `Vault`, `Note`, `Ruleset`, `Revision`, `KnowledgeChangeset`; runtime names include `native-loop` and `claude-agent-sdk` | `src/runtime/core/types.ts`; `tests/unit/runtime-phase0-contract.test.ts` | Done |
| P0-T2 `AgentRunRequest` removes legacy `cwd` and requires `workspaceId` / `workspaceRevision`; engine/model/evidence/toolProfile/message/budget modeled | `src/runtime/core/types.ts`; `tests/unit/runtime-phase0-contract.test.ts` rejects legacy shape | Done |
| P0-T3 `AgentEventEnvelope` schema with closed event type set including `context_compacted` | `src/runtime/events/envelope.ts`; `tests/unit/runtime-phase0-contract.test.ts` | Done |
| P0-T4 `WorkspaceService` resolves `workspaceId` to private path while public metadata does not expose paths | `src/runtime/core/workspace-service.ts`; `tests/unit/runtime-phase0-contract.test.ts` | Done |
| P0-T5 config-driven default engine and per-run override via `AgentRuntimeAdapter` | `src/runtime/core/dispatcher.ts`; `src/runtime/core/types.ts`; `tests/unit/runtime-phase0-contract.test.ts` | Done |
| P0-T6 SDK extraction audit with file line ranges, target locations, adaptation notes, and test migration paths | `docs/evidence/sdk-extract-checklist.md` | Ready for reviewer |
| P0-T7 Demo Vault in repo with `Inbox/` 12 notes, `Projects/`, `_rules/ruleset.json`, `_golden/{assignment.json,moc.md,link-graph.json,entropy.json}`, and `README.md` | `fixtures/demo-vault/`; `tests/unit/demo-vault-fixture.test.ts`; baseline commit `efba9f361f4216bba523cd07f06ab6f87fe5ab1b` | Done |
| P0-T8 thresholds schema lock: 28 metric entries; non-engine metrics calibrated; engine-dependent metrics deferred to `TBD@P6` | `docs/evidence/mvp-release-evaluation-thresholds.yaml`; `docs/evidence/baseline-calibration-phase0-2026-06-24.md`; `tests/unit/evaluation-thresholds-phase0.test.ts` | Done |
| Phase 0 code lands under `src/runtime/`; no new business logic in `src/sdk/agent-runtime/`, `backend/`, or `docs/superpowers/` | PR diff is 34 files under allowed Phase 0 paths; PR review focus calls out frozen-path check | Done locally; reviewer must confirm |
| Phase 0 verification commands pass | `docs/evidence/phase0-evidence-manifest.json`; local run results in PR body | Done locally |
| Every task immediately has PR and waits for reviewer PASS | PR #1 exists as draft aggregate Phase 0 PR | Partially done: aggregate PR exists; per-task PR split and reviewer PASS are not complete |

## Verification Commands

Run on clean PR branch `codex/mvp-release-phase-0`:

```sh
npx vitest run tests/unit/demo-vault-fixture.test.ts tests/unit/runtime-phase0-contract.test.ts tests/unit/evaluation-thresholds-phase0.test.ts
npx tsc --noEmit
python3 <adaptive-dev-workflow>/scripts/validate_workflow_manifest.py workflow_manifest.json
python3 <adaptive-dev-workflow>/scripts/validate_artifact_graph.py workflow_manifest.json
python3 <delivery-verification>/scripts/validate_evidence_manifest.py docs/evidence/phase0-evidence-manifest.json
rg forbidden-public-boundary-patterns <phase0 touched paths>
```

Observed result before this audit was added:

- Unit tests: 9 passed
- Typecheck: passed
- Workflow manifest validator: passed
- Artifact graph validator: passed
- Evidence manifest validator: passed
- P5 pattern search: no matches

## Remaining Gates

- PR #1 is still draft.
- No GitHub review has approved `docs/evidence/sdk-extract-checklist.md`.
- No GitHub review has approved the overall Phase 0 PR.
- There are no CI status checks on the PR head commit.

## Claim Boundary

`docs/evidence/phase0-evidence-manifest.json` requests `dev_done` only. It does not claim `integration_done`, `handoff_done`, MVP Done, reviewer PASS, live provider behavior, browser E2E, or Phase 1 readiness.
