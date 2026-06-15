# Java Stale Lock Recovery Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J6C only; do not implement scheduler wiring, delayed retry queues, progress streaming, remote runner, RBAC, or audit.

**Goal:** Add a deterministic stale-lock recovery baseline for async agent runs: a running job whose lock has expired can be failed with durable run/job/attempt evidence, and late worker completion cannot overwrite the recovered terminal state.

**Architecture:** `AgentRunRepository` owns stale lock recovery because the decisive evidence lives in `agent_jobs.locked_until`, `agent_runs.status`, and open `run_attempts`. J6C exposes a repository-level operation that future schedulers or ops services can call. The current local executor remains unchanged.

**Tech Stack:** Spring JDBC, existing Flyway schema, Testcontainers MySQL, JUnit 5.

---

## Scope Check

In scope:

- Add repository operation to fail stale running jobs.
- Use existing `agent_jobs.locked_until` without schema changes.
- Mark stale run/job as `FAILED` with a stable error code.
- Close open run attempts as `FAILED` with the same error code.
- Preserve terminal guard: stale recovery must not mutate `CANCELED`, `SUCCEEDED`, or already `FAILED` runs.
- Add focused tests for:
  - stale in-memory run recovery;
  - non-stale in-memory run left unchanged;
  - Testcontainers MySQL stale recovery.

Out of scope:

- Scheduler or `@Scheduled` wiring.
- Public ops/admin endpoint.
- Backoff or delayed retry queues.
- Progress events or SSE/WebSocket.
- RBAC/audit.
- Remote runner heartbeat/lease protocol.

## Tasks

- [x] Write RED in-memory stale recovery tests.
- [x] Write RED JDBC stale recovery test.
- [x] Implement repository stale recovery contract.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, and scans.
- [x] Archive delivery evidence.

## Completion Boundary

J6C is complete only if:

- stale `RUNNING` jobs with expired `lockedUntil` are deterministically failed;
- non-stale running jobs are not changed;
- terminal runs are not revived or overwritten;
- open attempts are closed with the stale-lock error code;
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.

## Execution Status

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - Failed first at Java test compile because `failStaleRunningJobs(Instant, String, Instant)` did not exist on the in-memory/JDBC repository contract.

Focused GREEN:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunRepositoryReliabilityTest,JdbcAgentRunRepositoryTest test`
  - 9 tests passed.
  - Covers in-memory stale recovery, fresh lock no-op, cancel/retry terminal guards, and Testcontainers MySQL stale recovery.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 44 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, and V5 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6C archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6C archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6C archive updates.

Implementation notes:

- `AgentRunRepository.failStaleRunningJobs(staleBefore, errorCode, now)` is implemented by both in-memory and JDBC repositories.
- Recovery only fails jobs in `RUNNING` with `lockedUntil <= staleBefore`.
- Recovery marks run/job/open attempt as `FAILED` with the supplied stale-lock error code.
- No scheduler, endpoint, or schema migration was added.

Evidence boundaries:

- J6C tests use local repository objects and local Testcontainers MySQL.
- J6C did not execute a real external provider call.
- J6C does not prove scheduler wiring, timed recovery cadence, progress events, audit, RBAC, or remote runner heartbeat/lease behavior.
