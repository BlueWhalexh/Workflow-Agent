# Java Backend Local Runtime Smoke Phase 5

Date: 2026-06-17

## Goal

Verify the current Java backend can serve the frontend/runtime handoff path through real local HTTP APIs and the default local TypeScript worker process, then close any small contract issues found by the smoke.

## Scope

- Start the Spring Boot backend on a non-conflicting local port.
- Create a workspace through `POST /v1/workspaces`.
- Seed only local fixture content into the backend-managed workspace content directory.
- Create an agent run through `POST /v1/workspaces/{workspaceId}/agent-runs`.
- Poll `GET /v1/agent-runs/{runId}` and `GET /v1/agent-runs/{runId}/events`.
- Validate the response remains the public backend response envelope and does not require frontend consumers to understand SDK/runtime internals.
- Fix the discovered duplicated `artifactRefs` boundary in the thin SDK backend adapter.

## Out Of Scope

- No HTTP server or auth redesign.
- No frontend UI wiring.
- No real external provider call.
- No production secret manager rollout.
- No remote runner multi-node dispatch.
- No workspace write execution beyond existing local fixture smoke behavior.

## Execution Evidence

- Backend command:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.jvmArguments='-Dserver.port=18080'`
- Health:
  - `GET http://127.0.0.1:18080/health` returned `java-backend-api.v1` with `ok: true`.
- Principal:
  - `GET http://127.0.0.1:18080/v1/me` returned `dev-user/dev-team`.
- Workspace:
  - `POST http://127.0.0.1:18080/v1/workspaces` created `ws_8808fd76648a4584b306e0e1ea3505af`.
- Fixture seed:
  - Copied `tests/fixtures/workspaces/basic-raw-mirror` into the backend-managed local content directory for that workspace.
- Initial run:
  - `POST /v1/workspaces/ws_8808fd76648a4584b306e0e1ea3505af/agent-runs` created `run_f264a16308f2498b9988f1edba9b74d5`.
  - Poll returned `SUCCEEDED`, `outputKind: answer`, `wroteWorkspace: false`.
  - Events returned `RUN_QUEUED -> RUNNING -> COMPLETED`.
  - Found duplicated `.agent-runs/open-agent/...json` in `artifactRefs`.
- RED:
  - `npm test -- tests/integration/agent-sdk-backend-adapter.test.ts`
  - Failed because `adapter-answer` returned duplicated `artifactRefs`.
- Fix:
  - `src/sdk/backend-adapter.ts` now performs stable ordered de-duplication when projecting SDK artifact refs into the backend response.
- Focused GREEN:
  - `npm test -- tests/integration/agent-sdk-backend-adapter.test.ts`
  - 2 tests passed.
- Post-fix real local HTTP run:
  - `run_50e51b4cce8a4df292a0c5dd8c055b53` returned `SUCCEEDED`, `outputKind: answer`, `wroteWorkspace: false`.
  - `artifactRefs` returned exactly one `.agent-runs/open-agent/run_50e51b4cce8a4df292a0c5dd8c055b53.json`.
  - Events returned `RUN_QUEUED -> RUNNING -> COMPLETED`.

## Final Verification

- `npm test -- tests/unit/agent-sdk-run.test.ts tests/integration/agent-sdk-backend-sim.test.ts tests/integration/agent-sdk-backend-adapter.test.ts`
  - 3 test files / 16 tests passed.
- `npm test`
  - 51 test files / 198 tests passed.
- `npm run typecheck`
  - Root `tsc --noEmit` passed.
- `npm run frontend:typecheck`
  - Frontend `tsc -p frontend/tsconfig.json --noEmit` passed.
- `npm run frontend:build`
  - Vite production build passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 161 Java backend tests passed.
- `git diff --check`
  - Passed with no whitespace errors.
- Strict token scan:
  - `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}" src tests docs backend frontend --glob '!backend/target/**' --glob '!frontend/dist/**'`
  - No matches; command exited 1 as expected for no real token-pattern hits.
- Broad fake-value scan:
  - Matches existing `test-api-key` fixture values in tests/docs only; no real token was recorded.

## Boundaries

- This is a real local Java backend HTTP smoke and a real local TypeScript worker process invocation.
- This is not a real external provider E2E.
- This is not a browser/frontend UI smoke.
- The smoke used local fixture workspace content and did not write user workspace files.
- Remaining production gaps stay open: OAuth/session token introspection, external directory sync, production secret manager rollout, remote runner artifact upload/fanout, frontend live run controls, approval mutation UI, and real provider/runtime E2E.

## Review Focus

- `artifactRefs` de-duplication must remain a backend adapter projection concern and must not change `agent-sdk-run.v1`.
- Frontend consumers should continue to rely only on `agent-backend-response.v1`.
- The local smoke evidence must not be described as external provider or production runner readiness.
