# Frontend Assistant Run Session Phase 4 Plan

> Status: implemented and verified. This is the frontend assistant session workflow slice before wiring the React panel to live run controls.

## Goal

Compose the frontend run API adapter into a session workflow that the right-side AI panel can consume: create a run, poll status, list events, and expose a compact approval-aware session view.

## Scope

- Add `frontend/src/features/assistant/run-session.ts`.
- Add `runAssistantTask(fetcher, input)`.
- Compose:
  - `createAgentRun`.
  - `getAgentRun`.
  - `listRunEvents`.
- Stop polling when status leaves `QUEUED` / `RUNNING`.
- Treat `WAITING_APPROVAL` as a terminal UI pause.
- Map status to a panel title and progress value.
- Map run events to `{ time, label }`.
- Preserve approval boundary: `targetWorkspacePaths` are suggestions and `wroteWorkspace` is explicit.

## Boundaries

- No React UI wiring yet.
- No real Java backend or real runtime call in this slice.
- No timers, EventSource/SSE, cancellation control, artifact content reads, or approval decision API adapter.
- Test data is fake `fetch` fixture data and must not be described as real provider/runtime evidence.

## RED Evidence

- `npm test -- tests/unit/frontend-assistant-run-session.test.ts`
  - Initial RED failed because `frontend/src/features/assistant/run-session.js` did not exist.

## GREEN Evidence

- `npm test -- tests/unit/frontend-assistant-run-session.test.ts`
  - 2 tests passed.
- `npm test -- tests/unit/frontend-run-api.test.ts tests/unit/frontend-assistant-run-session.test.ts`
  - 2 test files / 5 tests passed.
- `npm test`
  - 51 test files / 198 tests passed.
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
- `rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-frontend-assistant-run-session-phase4.md`
  - No matches; command exited 1 as expected.
- `git diff --cached --check`
  - Passed with no whitespace errors for the staged assistant run session file set.
