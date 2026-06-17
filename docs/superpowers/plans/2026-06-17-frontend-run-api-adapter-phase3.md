# Frontend Run API Adapter Phase 3 Plan

> Status: implemented and verified. This is the frontend runtime API adapter slice before wiring the UI to real run creation.

## Goal

Add a frontend API adapter for Java backend agent-run endpoints so the UI can safely create runs, poll run status, cancel runs, and list run lifecycle events without rendering runtime-private fields.

## Scope

- Add `frontend/src/features/runs/run-api.ts`.
- Cover:
  - `POST /v1/workspaces/{workspaceId}/agent-runs`.
  - `GET /v1/agent-runs/{runId}`.
  - `POST /v1/agent-runs/{runId}/cancel`.
  - `GET /v1/agent-runs/{runId}/events`.
- Map backend `AgentRunResponse` to a frontend `AgentRunView`.
- Map backend `RunEventResponse` to a frontend `RunEventView`.
- Reuse `java-backend-api.v1` envelope unwrap.
- Reuse public UI safety filtering before exposing view objects to components.

## Boundaries

- No UI wiring yet.
- No real Java backend or real runtime call in this slice.
- No SSE/EventSource client yet.
- No artifact content reads or approval decision API adapter yet.
- `targetWorkspacePaths` remains suggestion-only and is not write evidence.

## RED Evidence

- `npm test -- tests/unit/frontend-run-api.test.ts`
  - Initial RED failed because `frontend/src/features/runs/run-api.js` did not exist.

## GREEN Evidence

- `npm test -- tests/unit/frontend-run-api.test.ts`
  - 3 tests passed.
- `npm test -- tests/unit/frontend-api-client.test.ts tests/unit/frontend-workspace-api.test.ts tests/unit/frontend-public-safety.test.ts tests/unit/frontend-workbench-bootstrap.test.ts tests/unit/frontend-vite-proxy.test.ts tests/unit/frontend-run-api.test.ts`
  - 6 test files / 18 tests passed.
- `npm test`
  - 50 test files / 196 tests passed.
- `npm run typecheck`
  - Root `tsc --noEmit` passed.
- `npm run frontend:typecheck`
  - `tsc -p frontend/tsconfig.json --noEmit` passed.
- `npm run frontend:build`
  - Vite production build passed.

## Static Gates

- `git diff --check`
  - Passed with no whitespace errors.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs frontend src tests --glob '!backend/target/**' --glob '!frontend/dist/**'`
  - No matches; command exited 1 as expected for no token-pattern hits.
- `rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-frontend-run-api-adapter-phase3.md`
  - No matches; command exited 1 as expected.
- `git diff --cached --check`
  - Passed with no whitespace errors for the staged run API adapter file set.
