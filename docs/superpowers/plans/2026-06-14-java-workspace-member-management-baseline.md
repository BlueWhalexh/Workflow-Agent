# Java Workspace Member Management Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow public workspace member management baseline for the Java backend so one central service can grant and list workspace members without introducing a full identity directory.

**Architecture:** Keep workspace role decisions in `WorkspaceService`, expose only stable API envelopes from `WorkspaceController`, and make the repository contract cover both in-memory and JDBC member behavior. Audit member grants as workspace-level events so they can be validated without a run.

**Tech Stack:** Java 21, Spring Boot, MockMvc, JdbcTemplate, Flyway-managed MySQL schema, AssertJ, Testcontainers.

---

## Scope

In scope:
- `GET /v1/workspaces/{workspaceId}/members` lists members visible to a workspace viewer or higher.
- `PUT /v1/workspaces/{workspaceId}/members/{userId}` grants `WORKSPACE_VIEWER` or `WORKSPACE_EDITOR` and is owner-only.
- Member responses contain `userId`, `teamId`, and `role`.
- Member responses do not expose `workspaceRoot`, `serverStorageRef`, provider payloads, tokens, or secret references.
- Member grants write a `WORKSPACE_MEMBER_GRANTED` audit event with workspace scope and no run id.
- In-memory and JDBC workspace repositories share the same member-list contract.

Out of scope:
- Full OIDC/OAuth, SCIM, user directory CRUD, team CRUD, invitations, member removal, owner transfer, audit listing HTTP API, credential DB, secret manager, and production remote runner platform.
- Granting `WORKSPACE_OWNER` through the public member API. Initial owner creation still happens when the workspace is created.

## Files

- Create: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceMemberRecord.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceController.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/JdbcAuditRepository.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceMemberControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/JdbcAuditRepositoryTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

## Tasks

### Task 1: RED tests for member API and repository contract

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceMemberControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/JdbcAuditRepositoryTest.java`

- [x] **Step 1: Write the failing controller test**

Create `WorkspaceMemberControllerTest` with MockMvc scenarios:
- owner creates a workspace;
- owner grants `viewer-user` as `WORKSPACE_VIEWER`;
- owner lists members and sees owner plus viewer;
- viewer can read the workspace and list members;
- viewer cannot grant another member;
- owner cannot grant `WORKSPACE_OWNER` through the public member API;
- `WORKSPACE_MEMBER_GRANTED` audit event is queryable by workspace id.

- [x] **Step 2: Extend repository contract**

Add assertions that `listMembers(workspaceId)` returns the owner and a later viewer grant, that the viewer grant is visible only for the same team, and that repository-level grants reject team ids that do not match the workspace team.

- [x] **Step 3: Extend JDBC audit test**

Add a workspace-level audit event with `runId = null` and assert `findByWorkspaceId(workspaceId)` returns it in creation order without token leakage.

- [x] **Step 4: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest,JdbcWorkspaceRepositoryTest,JdbcAuditRepositoryTest test
```

Expected: fail because the member endpoint/repository methods do not exist yet, not because fixtures or JSON are malformed.

### Task 2: Minimal member implementation

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceMemberRecord.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceController.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/JdbcAuditRepository.java`

- [x] **Step 1: Add repository records and methods**

Add `WorkspaceMemberRecord(String workspaceId, String userId, String teamId, WorkspaceRole role)`, `WorkspaceRepository.listMembers(String workspaceId)`, and `AuditRepository.findByWorkspaceId(String workspaceId)`.

- [x] **Step 2: Implement in-memory storage**

Return stable ordered member records and workspace-filtered audit events.

- [x] **Step 3: Implement JDBC storage**

Map `workspace_members` to `WorkspaceMemberRecord` by joining `workspaces` for `team_id`, and query `audit_events` with `WHERE workspace_id = ? ORDER BY created_at ASC, id ASC`.

- [x] **Step 4: Add service rules**

`WorkspaceService.listMembers` requires `WORKSPACE_VIEWER`. `WorkspaceService.grantMember` requires `WORKSPACE_OWNER`, rejects blank `userId`, blank `teamId`, null `role`, and `WORKSPACE_OWNER`, then calls `grantAccess` and writes `WORKSPACE_MEMBER_GRANTED`.

- [x] **Step 5: Add controller endpoints**

Expose `GET /v1/workspaces/{workspaceId}/members` and `PUT /v1/workspaces/{workspaceId}/members/{userId}` with strict JSON request body and stable response DTOs.

- [x] **Step 6: Run focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest,JdbcWorkspaceRepositoryTest,JdbcAuditRepositoryTest test
```

Expected: pass.

### Task 3: Verification and archive

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan file

- [x] **Step 1: Run full Java suite**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 2: Run existing TS acceptance checks**

```bash
npm test
npm run typecheck
```

- [x] **Step 3: Run diff and secret hygiene**

```bash
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs/architecture/java-team-backend-platform-spec.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/superpowers/plans/2026-06-14-java-workspace-member-management-baseline.md
```

- [x] **Step 4: Update docs**

Record J10A as delivered, distinguish fake/unit/integration evidence from real external calls, and list remaining Phase 1 gaps: full OIDC/OAuth, public user/team directory APIs, member removal/owner transfer, audit listing HTTP API, credential DB/secret manager, SSE/WebSocket streaming, and production remote runner platform.

## Review Points

- Correctness: `PUT` is owner-only; `GET` is viewer+; public API cannot grant owner.
- Boundary: public responses do not expose server filesystem paths or raw provider data.
- Security: audit records contain actor/team/workspace/event metadata only; no token or provider payload.
- Data: in-memory and JDBC implementations satisfy the same repository contract.
- Scope: no HTTP auth productization, no schema migration, no new production dependency.
