# Java Run Event SSE Replay Cursor Baseline Implementation Plan

> **For agentic workers:** Follow `adaptive-dev-workflow` with TDD. This slice started within five files, then expanded after reviewer found a JDBC durable-ordering blocker; the final scope includes the controller, JDBC run event repository/migration, focused tests, current-truth spec, plan, and delivery report.

**Goal:** Add a thin replay cursor baseline to the existing run event SSE endpoint so reconnecting clients can pass `Last-Event-ID` and receive only later backend-owned durable run lifecycle events.

**Architecture:** Keep `RunEventRecord` as the only event source. Use a durable JDBC append sequence for event ordering so cursor semantics do not depend on timestamp/id ordering. Do not add WebSocket, broker fanout, new dependency, remote runner live channel, or runtime-private payload parsing. Authorization remains request-open run/workspace authorization through existing service guards.

**Tech Stack:** Java 21, Spring Boot MVC `SseEmitter`, JUnit 5, MockMvc.

---

### Task 1: RED Test For Last-Event-ID Reconnect

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RunEventControllerTest.java`

- [x] **Step 1: Add a failing MockMvc test**

Add `streamsOnlyEventsAfterLastEventIdWhenClientReconnects`:

- create a workspace and a blocking run with `userMessage = "stream event"`;
- list durable events and capture the `RUN_QUEUED` event id;
- open `GET /v1/agent-runs/{runId}/events/stream` with `Accept: text/event-stream` and `Last-Event-ID` set to the consumed event id;
- release the worker and complete the run;
- assert the stream omits the consumed event id and `event:RUN_QUEUED`, emits `RUNNING` and `COMPLETED`, and still excludes workspace internal paths, raw provider data, and Authorization.

- [x] **Step 2: Verify RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test
```

Expected: fail because the current SSE stream ignores `Last-Event-ID` and replays `RUN_QUEUED`.

### Task 2: Minimal Cursor Implementation

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/RunEventController.java`

- [x] **Step 1: Add optional Last-Event-ID support**

Implementation rules:

- accept optional `Last-Event-ID` header;
- trim blank header values to `null`;
- if the event id exists in the run event list, skip events through and including that id;
- if the event id is unknown, fall back to full replay rather than dropping events;
- mark skipped events as processed so later poll cycles do not re-emit them;
- preserve terminal-event stream completion semantics.

- [x] **Step 2: Verify focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test
```

Expected: all `RunEventControllerTest` cases pass.

### Task 3: Reviewer Follow-Up For Durable Ordering

**Files:**
- Modify: `backend/src/main/resources/db/migration/V6__run_event_baseline.sql`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/JdbcRunEventRepository.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRunEventRepositoryTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RunEventControllerTest.java`

- [x] **Step 1: Add JDBC RED for timestamp/id reorder**

Add a MySQL/Testcontainers regression test that inserts two events with the same `created_at` and ids that sort opposite append order.

Expected RED:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcRunEventRepositoryTest test
```

The test fails because current JDBC ordering returns `evt_a_second` before `evt_z_first`.

- [x] **Step 2: Persist append order**

Add `run_events.event_sequence BIGINT AUTO_INCREMENT`, index it by run, and order JDBC `findByRunId` by `event_sequence`.

- [x] **Step 3: Add cursor edge coverage**

Add MockMvc coverage for:

- unknown `Last-Event-ID` falls back to full replay;
- completed-run reconnect with cursor at `RUNNING` emits only later `COMPLETED`.

- [x] **Step 4: Verify reviewer follow-up**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcRunEventRepositoryTest,RunEventControllerTest test
```

Expected: focused JDBC ordering and SSE cursor tests pass.

### Task 4: Docs, Report, And Validation

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Update current-truth spec**

Record J22A as delivered and clarify:

- `Last-Event-ID` is a reconnect cursor over backend-owned durable run lifecycle events;
- payload remains public `RunEventResponse`;
- unknown cursor falls back to full replay;
- this is not WebSocket, broker fanout, multi-node stream routing, remote runner live channel, or production backpressure/heartbeat.

- [x] **Step 2: Update delivery report**

Append J22A with scope, RED evidence, debugging note, focused/full verification, scans, and evidence boundaries.

- [x] **Step 3: Run full verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-run-event-sse-replay-cursor-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TS tests, and typecheck pass; scans return no matches.
