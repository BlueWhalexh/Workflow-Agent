# Java Audit Listing API Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow public audit listing API so workspace owners can inspect backend-owned audit metadata without exposing runtime-private payloads or secrets.

**Architecture:** Keep append behavior in `AuditService` and add a separate `AuditQueryService` for read-side permission checks. `AuditController` exposes stable `java-backend-api.v1` responses and delegates all authorization to service code. The existing `AuditRepository.findByWorkspaceId(...)` remains the storage boundary.

**Tech Stack:** Java 21, Spring Boot, MockMvc, JUnit 5, AssertJ, existing in-memory/JDBC audit repositories.

---

## Scope

In scope:
- Add `GET /v1/workspaces/{workspaceId}/audit-events`.
- Require `WORKSPACE_OWNER` for audit listing.
- Return `auditEventId`, `actorUserId`, `teamId`, `workspaceId`, `runId`, `eventType`, `message`, and `createdAt`.
- Preserve existing audit append behavior.
- Keep response free of `workspaceRoot`, `serverStorageRef`, local absolute paths, provider payloads, Authorization headers, and token values.
- Cover missing workspace and viewer denial behavior.

Out of scope:
- Audit pagination, filtering, export/download, public run-specific audit API, audit event creation API, full team admin model, OIDC/OAuth, audit retention, signed audit records, DB schema changes, and real external provider calls.

## Files

- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditQueryService.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan file

## Tasks

### Task 1: RED test for owner-only audit listing

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`

- [x] **Step 1: Write the failing test**

Create a MockMvc test that:
- creates a workspace as `owner-audit`;
- grants `viewer-audit` as `WORKSPACE_VIEWER`;
- lists `GET /v1/workspaces/{workspaceId}/audit-events` as owner and sees `WORKSPACE_CREATED` and `WORKSPACE_MEMBER_GRANTED`;
- verifies response fields are audit metadata only;
- verifies response does not contain `workspaceRoot`, `serverStorageRef`, `Authorization`, `rawProvider`, or `token`;
- lists as viewer and receives `403 WORKSPACE_FORBIDDEN`;
- requests a missing workspace and receives `404 WORKSPACE_NOT_FOUND`.

- [x] **Step 2: Run RED**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: fail because `GET /v1/workspaces/{workspaceId}/audit-events` does not exist yet.

### Task 2: Minimal implementation

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditQueryService.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`

- [x] **Step 1: Add query service**

`AuditQueryService.listWorkspaceAuditEvents(workspaceId)` calls `WorkspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER)` before returning `auditRepository.findByWorkspaceId(workspaceId)`.

- [x] **Step 2: Add controller**

`AuditController` maps `GET /v1/workspaces/{workspaceId}/audit-events` to `AuditQueryService`, returning a DTO with only audit metadata fields.

- [x] **Step 3: Run focused GREEN**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: pass.

### Task 3: Verification and archive

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan file

- [x] **Step 1: Run Java full suite**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 2: Run TS acceptance checks**

```bash
npm test
npm run typecheck
```

- [x] **Step 3: Run diff and secret hygiene**

```bash
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/superpowers/plans/2026-06-14-java-audit-listing-api-baseline.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

- [x] **Step 4: Update docs**

Record J11A as delivered, including RED/focused/full evidence and remaining Phase 1 gaps: full OIDC/OAuth, public user/team directory APIs, member removal/owner transfer, audit pagination/filtering/export, credential DB/secret manager, SSE/WebSocket streaming, and production remote runner platform.

## Review Points

- Correctness: audit listing is owner-only and missing workspace stays 404.
- Boundary: response contains audit metadata only and no runtime/private/provider payload.
- Security: viewer denial is tested; event messages are stable short text and token scans stay clean.
- Scope: no schema migration, no audit write API, no new production dependency.
