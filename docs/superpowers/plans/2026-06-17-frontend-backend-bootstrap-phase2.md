# Frontend Backend Bootstrap Phase 2 Plan

> Status: implemented and verified. This is the first frontend-to-Java-backend bootstrap slice.

## Goal

Move the React knowledge workbench from fixture-only rendering to a safe backend bootstrap path that consumes the Java public API envelope for identity and visible workspaces.

## Scope

- Add `frontend/src/shared/api/envelope.ts` for `java-backend-api.v1` unwrap and normalized frontend errors.
- Add `frontend/src/features/workspace/workspace-api.ts` for `GET /v1/me` and `GET /v1/workspaces`.
- Add `frontend/src/shared/safety/public-fields.ts` to recursively strip sensitive fields and server absolute paths before rendering.
- Add `frontend/src/app/bootstrap.ts` so `App` attempts backend bootstrap and falls back to fixture preview when the backend is unavailable.
- Update `KnowledgeWorkbench` to display `后端已连接` or `离线预览`.
- Add focused unit tests for API envelope handling, workspace bootstrap mapping, public safety filtering, and workbench bootstrap fallback.
- Add Vite dev proxy coverage for `/v1`, `/health`, and `/ready` so the frontend dev server can talk to a local Java backend.
- Keep backend-reachable empty workspace state distinct from offline fallback by showing `后端已连接` and `暂无工作区`.

## Boundaries

- No backend API schema changes.
- No auth/session/OAuth UI.
- No run creation, event polling/SSE, artifact reads, approval mutation, or provider credential UI.
- No real backend server or real external provider was called by the unit tests.
- Real local browser smoke used Spring Boot on port `18080` because port `8080` was occupied by a non-project nginx service during verification.
- `targetWorkspacePaths` remains display-only and must not be treated as write evidence.

## RED Evidence

- `npm test -- tests/unit/frontend-workbench-bootstrap.test.ts`
  - Initial RED failed because `frontend/src/app/bootstrap.js` did not exist.
- `npm test -- tests/unit/frontend-vite-proxy.test.ts`
  - Initial RED failed because `frontend/vite.config.ts` had no `server.proxy`.
- `npm test -- tests/unit/frontend-workbench-bootstrap.test.ts`
  - Follow-up RED failed because an empty backend workspace list was treated as `fixture-fallback` instead of connected empty state.

## GREEN Evidence

- `npm test -- tests/unit/frontend-workbench-bootstrap.test.ts tests/unit/frontend-vite-proxy.test.ts`
  - 2 test files / 4 tests passed.
- `npm test -- tests/unit/frontend-api-client.test.ts tests/unit/frontend-workspace-api.test.ts tests/unit/frontend-public-safety.test.ts tests/unit/frontend-workbench-bootstrap.test.ts tests/unit/frontend-vite-proxy.test.ts`
  - 5 test files / 15 tests passed.
- `npm test`
  - 49 test files / 193 tests passed.
- `npm run frontend:typecheck`
  - `tsc -p frontend/tsconfig.json --noEmit` passed.
- `npm run frontend:build`
  - Vite production build passed.
- `npm run typecheck`
  - Root `tsc --noEmit` passed.
- Browser smoke against `http://127.0.0.1:5173/`
  - Page title was `My Workflow 知识工作台`.
  - Page rendered `知识工作台`, `工作台前端控制面`, `AI 助手`, and `离线预览`.
  - Page text did not contain `apiKeySecretRef` or the test secret-ref fixture.
  - Browser console error log was empty.
- Real local backend bootstrap smoke:
  - Direct Java backend checks on `http://127.0.0.1:18080/health`, `/v1/me`, and `/v1/workspaces` returned `java-backend-api.v1`.
  - Vite proxy checks on `http://127.0.0.1:5173/health`, `/v1/me`, and `/v1/workspaces` returned the same `java-backend-api.v1` envelopes.
  - Browser rendered `后端已连接` and `暂无工作区`, did not render `离线预览`, and had no console errors.

## Static Gates

- `git diff --check`
  - Passed with no whitespace errors.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs frontend src tests --glob '!backend/target/**' --glob '!frontend/dist/**'`
  - No matches; command exited 1 as expected for no token-pattern hits.
- `rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-frontend-backend-bootstrap-phase2.md docs/superpowers/plans/2026-06-17-frontend-knowledge-workbench-phase1.md`
  - No matches; command exited 1 as expected.
- `git diff --cached --check`
  - Passed with no whitespace errors for the staged frontend bootstrap file set.
