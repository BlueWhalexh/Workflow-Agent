# Java Audit Export Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow owner-only audit metadata export endpoint so backend operators can export existing workspace audit events without exposing raw provider payload, token material, or runtime-private fields.

**Architecture:** Reuse the existing `AuditEventQuery` and `AuditQueryService.listWorkspaceAuditEvents(workspaceId, query)` path so pagination/filtering and owner-only authorization stay identical to the JSON audit list API. The export endpoint returns newline-delimited JSON (`application/x-ndjson`) where each line is the same public `AuditEventResponse` shape already exposed by the JSON endpoint.

**Tech Stack:** Java 21 target, Spring Boot MVC, Jackson `ObjectMapper`, JUnit 5, MockMvc.

---

### Task 1: RED Test For Owner-Only NDJSON Export

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`

- [x] **Step 1: Add the failing export test**

Add a test named `ownerExportsFilteredAuditEventsAsNdjsonWhileViewerIsDenied`. It should:

- create a workspace as `owner-audit`;
- grant `viewer-audit`;
- append an `ARTIFACT_READ` audit event with `runId = run-audit-export-1`;
- call `GET /v1/workspaces/{workspaceId}/audit-events/export?eventType=ARTIFACT_READ&limit=1` as owner with `Accept: application/x-ndjson`;
- assert HTTP 200 and `Content-Type` compatible with `application/x-ndjson`;
- assert the response is one NDJSON line containing `auditEventId`, `workspaceId`, `runId`, and `eventType`;
- assert the body does not contain `workspaceRoot`, `serverStorageRef`, `Authorization`, `rawProvider`, `token`, `apiKey`, or `access_token`;
- assert viewer request to the same export URL returns 403 `WORKSPACE_FORBIDDEN`;
- assert invalid `limit=0` returns 400 `VALIDATION_ERROR`.

- [x] **Step 2: Verify RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: fail for the new export test because `/v1/workspaces/{workspaceId}/audit-events/export` does not exist yet. Existing audit list tests should still compile and run.

### Task 2: Minimal Export Endpoint

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`

- [x] **Step 1: Add method-level NDJSON endpoint**

Add `GET /v1/workspaces/{workspaceId}/audit-events/export` with:

- `produces = "application/x-ndjson"`;
- query params `limit`, `offset`, `eventType`, and `runId`, matching the list endpoint defaults and validation;
- body generated from `auditQueryService.listWorkspaceAuditEvents(workspaceId, query)`;
- each event serialized as `toResponse(event)` with Jackson, one JSON object per line;
- `Content-Disposition: attachment; filename="<workspaceId>-audit-events.ndjson"`.

Implementation constraints:

- do not add a new dependency;
- do not add a DB schema migration;
- do not include raw audit payload, provider payload, internal path, token, stack trace, or runtime-private source;
- rely on the service guard rather than duplicating role logic in the controller.

- [x] **Step 2: Verify focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: all `AuditControllerTest` cases pass.

### Task 3: Docs, Report, And Validation

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Update current-truth spec**

Record Phase J18A as delivered:

- `GET /v1/workspaces/{workspaceId}/audit-events/export`;
- owner-only;
- `application/x-ndjson`;
- same query params and public fields as audit list;
- no retention, signed records, async export job, object storage, public audit write API, or raw payload export.

- [x] **Step 2: Update delivery report**

Append Phase J18A with scope, RED evidence, focused/full verification, token scan, and evidence boundaries.

- [x] **Step 3: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-export-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TS tests, and typecheck pass; scans return no matches.
