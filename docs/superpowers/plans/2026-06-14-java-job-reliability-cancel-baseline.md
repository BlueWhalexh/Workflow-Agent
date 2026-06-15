# Java Job Reliability Cancel Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J6A only; do not implement retry scheduling, stale lock recovery, SSE/WebSocket progress streaming, remote runner, or audit.

**Goal:** Add a cancel-safe baseline for async agent runs: users can cancel a queued/running run, cancellation is persisted, and a late worker result cannot overwrite `CANCELED`.

**Architecture:** `AgentRunRepository` owns terminal state transitions. `AgentRunService.cancelRun()` checks workspace access through existing run lookup and calls repository cancel. Executor completion goes through repository `complete()` which must no-op if the run is already canceled. This keeps the first reliability guard close to DB state rather than relying on thread cancellation.

**Tech Stack:** Spring MVC, Spring JDBC, Flyway-backed existing schema, Testcontainers MySQL, JUnit 5, MockMvc.

---

## Scope Check

In scope:

- Add `POST /v1/agent-runs/{runId}/cancel`.
- Add repository `cancel(runId, now)` operation for in-memory and JDBC implementations.
- Make `complete()` and `fail()` preserve `CANCELED` as terminal.
- Mark latest open attempt and job as `CANCELED` when cancellation happens.
- Add focused tests for:
  - cancel returns `CANCELED`;
  - late worker success does not overwrite canceled run;
  - worker failure records failed run/attempt.

Out of scope:

- Interrupting or killing in-flight local worker process.
- Retry scheduling.
- Stale lock recovery.
- Progress events or SSE/WebSocket.
- Audit events.

## Tasks

- [x] Write RED controller test for canceling a running run while worker is blocked.
- [x] Write RED repository test that `complete()` does not overwrite canceled state.
- [x] Write RED test for worker failure recording.
- [x] Implement repository cancel and terminal guard.
- [x] Add cancel endpoint/service method.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, and scans.
- [x] Archive delivery evidence.

## Completion Boundary

J6A is complete only if:

- canceled run remains `CANCELED` even if worker later returns success;
- cancel endpoint uses existing run/workspace guard;
- job and open attempt are marked `CANCELED` where possible;
- worker failures still persist `FAILED` and attempt metadata;
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.

## Execution Status

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunCancelControllerTest,AgentRunRepositoryReliabilityTest,AgentRunFailureControllerTest test`
  - Failed first at Java test compile because `InMemoryAgentRunRepository.cancel(String, Instant)` did not exist.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunCancelControllerTest,AgentRunRepositoryReliabilityTest,AgentRunFailureControllerTest,JdbcAgentRunRepositoryTest test`
  - Failed at Java test compile because both the in-memory and JDBC repository contracts lacked `cancel(String, Instant)`.

Focused GREEN:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunCancelControllerTest,AgentRunRepositoryReliabilityTest,AgentRunFailureControllerTest,JdbcAgentRunRepositoryTest test`
  - 5 tests passed.
  - Covers cancel endpoint, late worker success preserving `CANCELED`, worker failure to `FAILED`, in-memory terminal guard, and Testcontainers MySQL JDBC terminal guard.

Implementation notes:

- `AgentRunRepository.cancel(runId, now)` is implemented by both in-memory and JDBC repositories.
- Cancel is scoped to queued/running run/job state; it does not interrupt an already executing local worker process.
- `complete()` and `fail()` no-op when the run is already terminal, including `CANCELED`.
- `AgentRunService` checks canceled state before registering late worker artifacts or approval requests.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 37 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6A archive updates.

Evidence boundaries:

- J6A tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J6A did not execute a real external provider call.
- J6A does not prove process interruption, retry scheduling, stale lock recovery, progress events, audit, RBAC, or remote runner behavior.
