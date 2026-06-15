# Java Run Event Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J6D only; do not implement SSE/WebSocket streaming, audit, RBAC, remote runner protocol, or provider payload capture.

**Goal:** Add a minimal run progress event history so backend clients can inspect durable lifecycle events for a run without parsing worker/runtime internals.

**Architecture:** `AgentRunService` appends backend-owned lifecycle events around run creation, execution attempts, retry, completion, failure, and cancellation. `RunEventService` lists events after reusing `AgentRunService.getRun(runId)` for the existing workspace guard. Events are metadata only and do not store raw provider payloads.

**Tech Stack:** Spring MVC, Spring JDBC, Flyway-backed existing schema, Testcontainers MySQL, JUnit 5, MockMvc.

---

## Scope Check

In scope:

- Add `run_events` schema.
- Add in-memory and JDBC run event repositories.
- Add `GET /v1/agent-runs/{runId}/events`.
- Append lifecycle events for:
  - run queued;
  - worker attempt running;
  - retry queued;
  - completed/waiting terminal SDK response;
  - final failure;
  - cancellation.
- Keep public response free of server absolute paths, raw provider payload, and TS runtime-private fields.

Out of scope:

- SSE/WebSocket streaming.
- Audit/security event log.
- RBAC.
- Remote runner protocol and heartbeat events.
- Provider token or raw payload capture.

## Tasks

- [x] Write RED controller test for run lifecycle event listing.
- [x] Write RED JDBC repository test for event persistence.
- [x] Implement run event model/repositories/service/controller.
- [x] Integrate event append calls in `AgentRunService`.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, and scans.
- [x] Archive delivery evidence.

## Completion Boundary

J6D is complete only if:

- backend clients can list run events by `runId`;
- event list uses existing run/workspace guard;
- events are ordered and include stable type/status values;
- event public response contains no absolute paths, provider tokens, raw provider payload, or runtime-private source;
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.

## Execution Status

Status: implemented for J6D. Durable run event history baseline is in place; SSE/WebSocket streaming, audit/RBAC event coverage, scheduler-driven recovery events, and remote runner heartbeat/lease events remain future phases.

Implemented:

- Added Flyway `V6__run_event_baseline.sql` with `run_events`.
- Added run event model, in-memory repository, JDBC repository, service, and controller.
- Added `GET /v1/agent-runs/{runId}/events`.
- `RunEventService` reuses `AgentRunService.getRun(runId)` for the existing run/workspace guard before listing events.
- `AgentRunService` appends backend-owned lifecycle events:
  - `RUN_QUEUED`;
  - `RUNNING`;
  - `RETRY_QUEUED`;
  - `COMPLETED`;
  - `FAILED`;
  - `CANCELED`.
- Public event response exposes stable event metadata only: event id, run id, event type, status, message, and created time.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest,JdbcRunEventRepositoryTest test`
  - Failed first at Java test compile because `JdbcRunEventRepository` and `RunEventRecord` did not exist. This confirmed the tests were exercising the missing J6D API/repository surface rather than a broken fixture.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test`
  - Failed after the first implementation because a run recovered as `FAILED(STALE_LOCK)` could still receive a late worker success and incorrectly append `COMPLETED/SUCCEEDED` to event history.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest,JdbcRunEventRepositoryTest test`
  - 3 tests passed.
  - Covers HTTP event listing for a completed run, response redaction boundaries, ordered lifecycle events, no false `COMPLETED` event after stale recovery wins the terminal state, and Testcontainers MySQL event persistence.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 47 Java tests passed.
  - Testcontainers MySQL applied V2, V3, V4, V5, and V6 migrations during full suite.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J6D archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J6D archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J6D archive updates.

Evidence boundaries:

- J6D tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J6D did not execute a real external provider call.
- J6D does not prove SSE/WebSocket streaming, audit/RBAC coverage, scheduler-driven recovery events, or remote runner heartbeat/lease behavior.
