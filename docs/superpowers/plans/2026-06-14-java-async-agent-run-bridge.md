# Java Async Agent Run Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development for implementation and verification. This plan is Phase J3A only; do not expand into approval APIs, artifact registry, cancel/retry policy, or remote runner.

**Goal:** Implement the first async Java backend run bridge: DB-backed `AgentRun` / `AgentJob` state, `POST /v1/workspaces/{workspaceId}/agent-runs`, `GET /v1/agent-runs/{runId}`, in-process async execution, and a local TS worker adapter that consumes only `agent-backend-response.v1`.

**Architecture:** Java remains the control plane. It resolves `workspaceId` to an internal server-hosted workspace root, creates run/job records, returns queued metadata immediately, then executes a worker through an `AgentWorker` port. The local TS adapter is one implementation of that port and invokes `src/cli/backend-agent-worker.ts`, which calls `runBackendAgent()`. Java maps only the stable top-level backend response fields into its own run state.

**Tech Stack:** Spring Boot MVC, Spring JDBC/Flyway/MySQL, Testcontainers MySQL, Jackson, Java `ProcessBuilder`, JUnit 5, AssertJ, MockMvc, existing TS `tsx` runtime.

---

## Scope Check

In scope:

- Add `V3__agent_run_job_baseline.sql`.
- Add Java run/job domain records and status enums.
- Add `AgentRunRepository` with in-memory and JDBC implementations.
- Add `AgentWorker` port and local TS process adapter.
- Add `src/cli/backend-agent-worker.ts` as the thin TS stdin/stdout worker entry.
- Add `AgentRunService` and controller endpoints:
  - `POST /v1/workspaces/{workspaceId}/agent-runs`
  - `GET /v1/agent-runs/{runId}`
- Preserve workspace permission checks through `WorkspaceService`.
- Return queued metadata before worker completion.
- Map backend response statuses into Java run statuses.
- Treat `requiresApproval=true` as `WAITING_APPROVAL` and `requiresConfirmation=true` as `WAITING_CONFIRMATION`.
- Persist artifact refs, target workspace paths, display text, write evidence, and output kind.
- Prove candidate patch returns approval-required and does not write workspace.
- Prove Java response parsing ignores the TS adapter `source` field.

Out of scope:

- Spring Security/OIDC/RBAC.
- Approval decision APIs.
- Artifact registry/read APIs.
- Cancel/retry policy.
- Workspace write locks.
- Provider credential references and real provider execution.
- Remote runner.
- SSE/WebSocket run updates.

## File Structure

Modify:

- `backend/src/main/resources/application.yml`
  - Add local worker defaults.
- `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`
  - Add worker/run error mappings if needed.
- `docs/architecture/java-team-backend-platform-spec.md`
  - Update J3A status after verification.
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Append J3A delivery evidence.

Create:

- `src/cli/backend-agent-worker.ts`
- `backend/src/main/resources/db/migration/V3__agent_run_job_baseline.sql`
- `backend/src/main/java/com/myworkflow/agent/backend/run/*`
- `backend/src/test/java/com/myworkflow/agent/backend/run/*`

## Task 1: RED API And Schema

- [x] **Step 1: Write API RED**

Add `AgentRunControllerTest` expecting:

- `POST /v1/workspaces/{workspaceId}/agent-runs` returns `QUEUED` without waiting for a blocked fake worker.
- `GET /v1/agent-runs/{runId}` later returns `SUCCEEDED` with answer display text after the fake worker is released.
- Unknown `workspaceRoot` in request body is rejected.
- Inaccessible/missing workspace is rejected through existing workspace guard.

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunControllerTest test
```

Expected: fail because run API does not exist.

- [x] **Step 2: Write JDBC schema/repository RED**

Add `JdbcAgentRunRepositoryTest` using Testcontainers MySQL and Flyway. It should verify V2+V3 migrations, create a workspace, create a run/job, mark running, complete with backend response fields, and reload persisted run state.

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcAgentRunRepositoryTest test
```

Expected: fail because V3 schema and repository do not exist.

## Task 2: TS Worker Entry RED/GREEN

- [x] **Step 1: Write TS CLI test**

Add focused Vitest coverage for `src/cli/backend-agent-worker.ts` or a direct child-process smoke that sends a deterministic-open-agent answer request over stdin and reads `agent-backend-response.v1` from stdout.

Run:

```bash
npm test -- tests/integration/backend-agent-worker-cli.test.ts
```

Expected RED: CLI entry does not exist.

- [x] **Step 2: Implement minimal TS worker CLI**

Read stdin JSON, call `runBackendAgent()`, write one JSON object to stdout, and avoid logging secrets or raw provider payload.

## Task 3: Java Run/Job GREEN

- [x] **Step 1: Implement run domain and repository port**

Add status enums, records, and `AgentRunRepository`.

- [x] **Step 2: Implement in-memory and JDBC repositories**

JDBC stores list fields as JSON text/JSON columns and does not store raw worker `source` payload.

- [x] **Step 3: Implement service/controller**

Use `WorkspaceService` to resolve workspace root. Do not accept public `workspaceRoot`.

- [x] **Step 4: Implement async executor**

Return queued run metadata before worker completion. Store attempts and final run status after worker completes.

## Task 4: Local TS Worker Adapter

- [x] **Step 1: Implement Java local process adapter**

Use `ProcessBuilder` with configurable repo root/node command. Send `AgentWorkerRequest` JSON to stdin and parse only top-level `agent-backend-response.v1` fields. Ignore unknown `source`.

- [x] **Step 2: Add focused adapter test**

Use the real TS CLI against a fixture workspace with fake/deterministic provider only. Do not execute a real provider call.

## Task 5: Verification And Archive

- [x] **Step 1: Focused Java tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AgentRunControllerTest,JdbcAgentRunRepositoryTest,LocalTsAgentWorkerTest test
```

- [x] **Step 2: Full Java tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 3: TS tests and typecheck**

Run:

```bash
npm test
npm run typecheck
```

- [x] **Step 4: Scans**

Run:

```bash
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

- [x] **Step 5: Archive**

Update this plan, the Java platform spec, and delivery report with RED/GREEN evidence and explicit fake-provider/local-worker boundaries.

## Completion Boundary

J3A is complete only if:

- async POST returns queued metadata before worker completion;
- polling returns completed Java run state;
- MySQL/Testcontainers applies V2+V3 and persists run/job/attempt state;
- local TS worker adapter returns `agent-backend-response.v1`;
- candidate patch maps to `WAITING_APPROVAL` and `wroteWorkspace=false`;
- Java code does not parse TS runtime-private fields or raw `source`;
- Java full suite, TS full suite, typecheck, diff scan, whitespace/merge-marker scan, and token scan pass.

## Self-Review

- Public API risk: new `/v1/.../agent-runs` endpoints and run response contract need review.
- State machine risk: status mapping must stay conservative and backend-owned.
- Side-effect risk: candidate patch is approval-required and must not be treated as a write.
- Secret risk: J3A uses fake/default provider only and must not accept token values in API.
- Runtime boundary: Java parses only `agent-backend-response.v1` top-level fields; the TS adapter `source` payload is ignored by the Java response model.
- Known DB warning: Flyway still reports MySQL 8.4 is newer than its explicitly tested MySQL 8.1 support range; V2+V3 migrations apply successfully in Testcontainers.

## Execution Status

Status: completed for J3A on 2026-06-14.

RED evidence:

- `npm test -- tests/integration/backend-agent-worker-cli.test.ts`
  - Failed first with exit code `1` because `src/cli/backend-agent-worker.ts` did not exist.
- `mvn -f backend/pom.xml -Dtest=AgentRunControllerTest,JdbcAgentRunRepositoryTest,LocalTsAgentWorkerTest test`
  - Failed first at test compile because `AgentWorker`, `AgentWorkerRequest`, `AgentWorkerResponse`, run/job records, JDBC repository, and local worker adapter did not exist.
- First GREEN attempt exposed a real wiring bug:
  - `AgentRunControllerTest` Spring context failed because `LocalTsAgentWorker` had two constructors and no explicit `@Autowired`.
  - Root cause fixed by annotating the production constructor.

GREEN evidence:

- `npm test -- tests/integration/backend-agent-worker-cli.test.ts`
  - PASS; 1 test.
- `mvn -f backend/pom.xml -Dtest=AgentRunControllerTest,AgentRunLocalTsControllerTest,JdbcAgentRunRepositoryTest,LocalTsAgentWorkerTest test`
  - PASS; 6 Java tests.
  - Includes Testcontainers MySQL, Flyway V2+V3, async API, candidate patch approval mapping, local TS worker adapter, and Java API -> local TS worker smoke.
- `mvn -f backend/pom.xml test`
  - PASS; 27 Java tests.
- `npm test`
  - PASS; 44 Vitest files / 178 tests.
- `npm run typecheck`
  - PASS.
- `mvn -f backend/pom.xml -Dtest=LocalTsAgentWorkerTest test`
  - PASS after removing a stale import.
- `git diff --check`
  - PASS after archive updates.
- J3 whitespace / merge-marker scan
  - no matches after archive updates.
- token redaction scan for `tp-*` / `Bearer tp-*` / `MIMO_API_KEY=tp-*`
  - no matches after archive updates.
