# Java Remote Runner Contract Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J8A only; do not implement runner registration, heartbeat, distributed job lease, artifact upload service, real signature verification, runner-scoped secret access, or a production remote runner platform.

**Goal:** Add a minimal configurable remote worker spike so the Java backend can choose between the existing local TS worker and a remote HTTP worker that still returns the same `agent-backend-response.v1` result contract.

**Architecture:** Keep `AgentWorker` as the backend port. Use Spring configuration to select either `local-ts` or `remote-http`. The remote HTTP worker is a transport adapter only: it POSTs the existing `AgentWorkerRequest`, validates a thin remote result envelope, and returns the nested `AgentWorkerResponse` to the existing run state machine.

**Tech Stack:** Spring Boot conditional beans, Java 17 `HttpClient`, Jackson, JUnit 5, MockMvc, JDK `HttpServer`.

---

## Scope Check

In scope:

- Add config key `my-workflow.backend.agent-worker.kind`:
  - default `local-ts`;
  - opt-in `remote-http`.
- Keep the existing local TS worker behavior as the default.
- Add `RemoteHttpAgentWorker` using JDK `HttpClient`, no new production dependency.
- Define a minimal remote transport envelope:
  - `schemaVersion: "agent-remote-runner-result.v1"`;
  - `workerKind: "REMOTE_RUNNER"`;
  - `signatureKind: "unsigned-local-spike"`;
  - `result: AgentWorkerResponse`.
- Validate remote envelope schema, worker kind, signature kind, nested response schema, and nested run id.
- Store run attempts with `workerKind="REMOTE_RUNNER"` when remote HTTP worker is selected.
- Prove Java still maps only the nested `agent-backend-response.v1` fields into run state and ignores runtime-private payload.
- Document that this is not a production remote runner.

Out of scope:

- Runner registration, heartbeat, lease renewal, capability advertisement, queue ownership, or artifact upload.
- Real cryptographic signed result envelope.
- Remote cancellation callback.
- Remote workspace mount model.
- Provider secret distribution or runner-scoped credential access.
- Public API changes.
- DB schema changes.
- Real external provider calls.

## Files

Production:

- Modify `backend/src/main/java/com/myworkflow/agent/backend/run/AgentWorker.java`
  - Add a default `workerKind()` returning `LOCAL_TS_WORKER`.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/run/LocalTsAgentWorker.java`
  - Add conditional bean guard for default `local-ts`.
  - Override `workerKind()` with `LOCAL_TS_WORKER`.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java`
  - Replace the hardcoded worker kind with `worker.workerKind()`.
- Add `backend/src/main/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorker.java`
  - Conditional bean for `remote-http`.
  - POST `AgentWorkerRequest` to configured endpoint URL.
  - Validate remote transport envelope.

Tests:

- Add `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorkerTest.java`
  - Local JDK HTTP server returns remote envelope.
  - Test request mapping, response mapping, runtime-private payload ignoring, and envelope rejection.
- Add `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorkerControllerTest.java`
  - Spring config selects `remote-http`.
  - API creates run, remote worker completes it, and run attempt worker kind is `REMOTE_RUNNER`.

Docs:

- Update `docs/architecture/java-team-backend-platform-spec.md`.
- Update this plan after execution.
- Update `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Tasks

- [x] Write RED `RemoteHttpAgentWorkerTest` proving the class/API does not exist yet.
- [x] Write RED `RemoteHttpAgentWorkerControllerTest` proving config cannot yet select a remote worker and attempt kind cannot become `REMOTE_RUNNER`.
- [x] Run focused RED:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test`
- [x] Implement `AgentWorker.workerKind()`.
- [x] Implement conditional local worker selection.
- [x] Implement `RemoteHttpAgentWorker`.
- [x] Replace `AgentRunService` hardcoded worker kind with `worker.workerKind()`.
- [x] Run focused GREEN:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test`
- [x] Run Java full suite:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
- [x] Run TS suite and typecheck:
  - `npm test`
  - `npm run typecheck`
- [x] Run static gates:
  - `git diff --check`
  - whitespace/merge-marker scan over backend and touched docs.
  - token scan over backend/docs/src/tests.
- [x] Archive execution evidence and boundaries.

## Execution Status

Implemented for Phase J8A.

Delivered:

- Added `AgentWorker.workerKind()` with `LOCAL_TS_WORKER` as the default.
- Kept `LocalTsAgentWorker` as the default worker through `my-workflow.backend.agent-worker.kind=local-ts` with `matchIfMissing=true`.
- Added opt-in `RemoteHttpAgentWorker` for `my-workflow.backend.agent-worker.kind=remote-http`.
- Added the local spike remote transport envelope:
  - `schemaVersion="agent-remote-runner-result.v1"`;
  - `workerKind="REMOTE_RUNNER"`;
  - `signatureKind="unsigned-local-spike"`;
  - nested `result` as `agent-backend-response.v1`.
- `AgentRunService` now records attempts using the selected worker kind.
- Remote worker tests prove Java maps the nested backend response and ignores runtime-private `source` payload.

RED evidence:

- First focused run exposed a test fixture error: Java `Map.of(...)` cannot build maps with more than 10 key/value pairs.
- After fixing the fixture, focused RED failed at Java test compile because `RemoteHttpAgentWorker` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test`
  - 3 tests passed.
  - Covers remote request mapping, envelope validation, runtime-private payload ignoring, Spring config selecting `remote-http`, and `RunAttempt.workerKind=REMOTE_RUNNER`.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 54 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J8A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-remote-runner-contract-spike.md docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J8A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J8A archive updates.

Boundaries:

- J8A uses a local JDK `HttpServer` stub in tests; it is not a real remote runner service.
- `signatureKind=unsigned-local-spike` is an explicit non-cryptographic placeholder, not production signature verification.
- J8A does not implement runner registration, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, or runner-scoped secret access.
- J8A does not change public API, DB schema, or provider secret behavior.
- J8A did not execute a real external provider call.

## Completion Boundary

J8A is complete only if:

- Default backend worker remains local TS.
- Setting `my-workflow.backend.agent-worker.kind=remote-http` selects the remote HTTP worker.
- Remote worker receives the existing `AgentWorkerRequest` shape.
- Remote worker accepts only the `agent-remote-runner-result.v1` transport envelope with `workerKind=REMOTE_RUNNER` and `signatureKind=unsigned-local-spike`.
- The nested result must be `agent-backend-response.v1` and must match the run id.
- Java run state mapping still uses `AgentWorkerResponse`, not runtime-private `source` or remote transport internals.
- Run attempts record `REMOTE_RUNNER` for the remote worker path.
- No production dependency, DB schema, public API, or provider secret behavior changes are introduced.
- Verification and docs clearly state this is fake/local HTTP spike evidence, not a real remote runner platform.
