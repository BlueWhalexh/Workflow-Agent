# Java Run Event SSE Streaming Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a thin server-sent events baseline for Java run lifecycle events so backend clients can observe async run progress without parsing worker/runtime internals.

**Architecture:** Reuse the existing durable `RunEventRecord` source and the existing run/workspace permission check. The stream endpoint authorizes the run on request open, polls backend-owned run events, emits only the same public `RunEventResponse` fields as SSE payloads, completes after a terminal lifecycle event when one is observed, and may also close at the bounded stream window without implying run terminality.

**Tech Stack:** Java 21, Spring Boot MVC `SseEmitter`, JUnit 5, MockMvc.

---

### Task 1: RED Test For SSE Stream Endpoint

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RunEventControllerTest.java`

- [x] **Step 1: Add a failing MockMvc test**

Add a test named `streamsRunLifecycleEventsAsServerSentEventsWithoutRuntimePrivateFields`. It should:

- create a workspace;
- create an agent run with `userMessage = "stream event"`;
- wait until the fake worker starts;
- open `GET /v1/agent-runs/{runId}/events/stream` with `Accept: text/event-stream`;
- release the fake worker;
- async-dispatch the stream response;
- assert `text/event-stream`, lifecycle event names, the run id, and absence of `workspaceRoot`, `rawProvider`, `runtime`, and `Authorization`.

- [x] **Step 2: Verify RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test
```

Expected: fail with 404/406/async-not-started because the SSE endpoint does not exist yet, while existing JSON event tests remain valid.

### Task 2: Minimal SSE Implementation

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/RunEventController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RunEventControllerTest.java`

- [x] **Step 1: Add the stream endpoint**

Add `GET /v1/agent-runs/{runId}/events/stream` producing `MediaType.TEXT_EVENT_STREAM_VALUE`.

Implementation rules:

- authorize through the existing run/workspace guard before creating the stream;
- emit `SseEmitter.event().id(event.eventId()).name(event.eventType()).data(toResponse(event))`;
- poll durable events for a bounded time and deduplicate by event id;
- complete after an emitted event has a terminal `AgentRunStatus`; if the bounded stream window expires first, EOF means the client should reconnect or poll rather than assume the run is terminal;
- do not include worker raw output, provider payload, workspace internal paths, or runtime-private source fields.

- [x] **Step 2: Extend the test worker**

Make `StaleRecoveryWorker` block for both `"stale event"` and `"stream event"` messages so the SSE request can attach while the run is `RUNNING`.

- [x] **Step 3: Verify focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RunEventControllerTest test
```

Expected: all `RunEventControllerTest` cases pass.

### Task 3: Docs, Report, And Validation

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Update current-truth spec**

Record Phase J17A as delivered and describe the SSE baseline limits:

- durable run event stream only;
- request-open authorization using existing run/workspace guard;
- public payload equals `RunEventResponse`;
- no WebSocket, broker fanout, remote runner stream, replay cursor, or production multi-node delivery.

- [x] **Step 2: Update delivery report**

Append Phase J17A with scope, RED evidence, focused/full verification, security scan, and evidence boundaries.

- [x] **Step 3: Run full verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-run-event-sse-streaming-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TS tests, and typecheck pass; scans return no matches.
