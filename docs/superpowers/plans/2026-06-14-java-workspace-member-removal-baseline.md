# Java Workspace Member Removal Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow owner-only workspace member removal API so the Java backend can revoke a non-owner member's workspace access and record auditable evidence.

**Architecture:** Keep membership lifecycle logic in `WorkspaceService`; `WorkspaceController` only maps HTTP to service calls. Extend `WorkspaceRepository` with a small delete primitive implemented by both in-memory and JDBC repositories, preserving the current team-bound workspace access model.

**Tech Stack:** Spring Boot MVC, Java records, JdbcTemplate, MySQL-compatible schema, MockMvc, Testcontainers repository contract tests.

---

### Task 1: Controller And Service Behavior

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceMemberControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceController.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceService.java`

- [x] **Step 1: Write the failing HTTP behavior test**

Add assertions to `ownerGrantsAndListsMembersWhileViewerCannotMutateMembership`:

```java
mockMvc.perform(delete("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "viewer-user")
        .headers(devHeaders("viewer-user", "Viewer User")))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

mockMvc.perform(delete("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "owner-user")
        .headers(devHeaders("owner-user", "Owner User")))
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

mockMvc.perform(delete("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "viewer-user")
        .headers(devHeaders("owner-user", "Owner User")))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
    .andExpect(jsonPath("$.ok").value(true))
    .andExpect(jsonPath("$.data.userId").value("viewer-user"))
    .andExpect(jsonPath("$.data.teamId").value("team-members"))
    .andExpect(jsonPath("$.data.role").value("WORKSPACE_VIEWER"))
    .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
    .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist());

mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspaceId)
        .headers(devHeaders("viewer-user", "Viewer User")))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
```

- [x] **Step 2: Run focused RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceMemberControllerTest test
```

Expected: FAIL because `DELETE /v1/workspaces/{workspaceId}/members/{userId}` is not mapped or not implemented. Existing setup and grant/list assertions must pass before the failing delete assertion.

- [x] **Step 3: Add minimal controller and service API**

Add `@DeleteMapping("/{workspaceId}/members/{userId}")` to `WorkspaceController`, returning the existing `WorkspaceMemberResponse`. Add `WorkspaceService.removeMember(workspaceId, userId)` that:

```java
WorkspaceRecord workspace = requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
String normalizedUserId = normalizeMemberValue(userId, "Workspace member user id is required");
WorkspaceMemberRecord member = repository.findMember(workspace.workspaceId(), normalizedUserId)
    .orElseThrow(() -> new IllegalArgumentException("Workspace member not found"));
if (member.role() == WorkspaceRole.WORKSPACE_OWNER) {
  throw new IllegalArgumentException("Workspace owner removal is not supported through this API");
}
repository.revokeAccess(workspace.workspaceId(), normalizedUserId, member.teamId());
auditService.record(workspace.workspaceId(), null, "WORKSPACE_MEMBER_REMOVED", "Workspace member removed");
return member;
```

- [x] **Step 4: Run focused GREEN**

Run the same focused Maven command. Expected: PASS.

### Task 2: Repository Contract

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`

- [x] **Step 1: Extend the repository contract test first**

After granting the viewer in `WorkspaceRepositoryContract`, assert:

```java
assertThat(repository.findMember(workspaceId, "viewer_contract_" + unique))
    .contains(new WorkspaceMemberRecord(
        workspaceId,
        "viewer_contract_" + unique,
        teamId,
        WorkspaceRole.WORKSPACE_VIEWER
    ));
assertThat(repository.revokeAccess(workspaceId, "viewer_contract_" + unique, teamId)).isTrue();
assertThat(repository.findMember(workspaceId, "viewer_contract_" + unique)).isEmpty();
assertThat(repository.canAccess(workspaceId, "viewer_contract_" + unique, teamId)).isFalse();
assertThat(repository.findVisibleTo("viewer_contract_" + unique, teamId)).isEmpty();
assertThat(repository.revokeAccess(workspaceId, "viewer_contract_" + unique, teamId)).isFalse();
```

- [x] **Step 2: Run repository RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test
```

Expected: compile FAIL because `findMember` / `revokeAccess` do not exist yet.

- [x] **Step 3: Implement repository methods**

Add to `WorkspaceRepository`:

```java
Optional<WorkspaceMemberRecord> findMember(String workspaceId, String userId);

boolean revokeAccess(String workspaceId, String userId, String teamId);
```

For in-memory, filter the immutable membership list by `userId` and `teamId`. For JDBC, query/delete from `workspace_members` while joining `workspaces` on `team_id` to prevent cross-team deletion.

- [x] **Step 4: Run repository GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test
```

Expected: PASS for both repository implementations.

### Task 3: Documentation And Verification

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-workspace-member-removal-baseline.md`

- [x] **Step 1: Update architecture spec**

Record J12A as delivered: owner-only removal of non-owner workspace members, no owner transfer, no team/user directory CRUD.

- [x] **Step 2: Update delivery report**

Add RED/GREEN/full validation evidence and state that no real provider call was made.

- [x] **Step 3: Run full validation**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-member-removal-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TypeScript tests, and typecheck pass; diff check passes; whitespace/conflict/token scans return no matches.

### Self-Review

- Spec coverage: covers one remaining backend Phase 1 gap, workspace member removal. Owner transfer, public user/team directory API, audit pagination/export, OIDC/OAuth, SSE/WebSocket, credential DB/secret manager, and production remote runner remain out of scope.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: controller/service/repository names are `removeMember`, `findMember`, and `revokeAccess`; tests reference the same API names.
