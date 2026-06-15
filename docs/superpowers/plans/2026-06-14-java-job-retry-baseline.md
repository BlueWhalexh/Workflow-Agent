# Java Job Retry Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J6B only; do not implement delayed scheduling, stale lock recovery, progress streaming, remote runner, or audit.

**Goal:** Add a minimal retry policy for async agent runs: transient worker failures can retry within the existing in-process executor, successful retry completes the run, and exhausted attempts produce one final failed run with attempt evidence.

**Architecture:** `AgentRunService` owns the retry loop for the current local executor. `AgentRunRepository` owns per-attempt persistence: a failed attempt can be closed and the job re-queued unless the run is already terminal. This keeps retry evidence in `run_attempts` while avoiding a queue scheduler in J6B.

**Tech Stack:** Spring MVC, Spring JDBC, existing Flyway schema, Testcontainers MySQL, JUnit 5, MockMvc.

---

## Scope Check

In scope:

- Retry worker execution up to `AgentJob.maxAttempts`.
- Persist each failed attempt before retrying.
- Keep successful retry as `SUCCEEDED` with the final worker response.
- Keep exhausted retries as `FAILED` with `errorCode=WORKER_FAILED`.
- Preserve J6A cancel terminal guard: retries must not revive `CANCELED`.
- Add focused tests for:
  - transient failure then success;
  - exhausted failures record max attempts and final failed state;
  - JDBC retry persistence closes failed attempt and re-queues job.

Out of scope:

- Delayed retry/backoff scheduling.
- External queue or worker lease polling.
- Stale lock recovery.
- Timeout policy.
- Progress events or SSE/WebSocket.
- Audit events.

## Tasks

- [x] Write RED controller test for transient failure then successful retry.
- [x] Write RED repository test that retry closes the current attempt and re-queues the job.
- [x] Write RED exhausted failure test for max attempts.
- [x] Implement repository retry operation and terminal guard.
- [x] Implement in-process retry loop in `AgentRunService`.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, and scans.
- [x] Archive delivery evidence.

## Completion Boundary

J6B is complete only if:

- a worker that fails once and then succeeds produces a `SUCCEEDED` run;
- a worker that keeps failing produces exactly `maxAttempts` failed attempts and one final `FAILED` run;
- retry does not overwrite `CANCELED`;
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.

## Execution Status

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRetryControllerTest,AgentRunFailureControllerTest,AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - Failed first at Java test compile because `retry(String, String, String, Instant)` did not exist on the in-memory/JDBC repository contract.

Focused GREEN:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRetryControllerTest,AgentRunFailureControllerTest,AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - 8 tests passed.
  - Covers transient worker failure retrying to success, exhausted failures producing 3 failed attempts and final `FAILED`, in-memory retry/cancel guard, and Testcontainers MySQL JDBC retry persistence.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 41 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6B archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6B archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6B archive updates.

Implementation notes:

- `AgentRunService` retries worker exceptions immediately inside the current local executor loop.
- `AgentRunRepository.retry(runId, jobId, errorCode, now)` is implemented by both in-memory and JDBC repositories.
- Retry only transitions from `RUNNING` back to `QUEUED`; it does not revive `CANCELED` or other terminal states.
- `AgentJob.maxAttempts` remains the attempt limit; no migration was needed.

Evidence boundaries:

- J6B tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J6B did not execute a real external provider call.
- J6B does not prove delayed retry scheduling, backoff, stale lock recovery, timeout policy, progress events, audit, RBAC, or remote runner behavior.
