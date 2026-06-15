# Java Workspace Owner Transfer Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow owner-only workspace ownership transfer API that promotes an existing workspace member to `WORKSPACE_OWNER`, demotes the previous owner, and records audit metadata without exposing internal storage or token material.

**Architecture:** Keep owner transfer separate from `PUT /members/{userId}` so public member grants still cannot assign `WORKSPACE_OWNER`. `WorkspaceService` performs RBAC and validation, `WorkspaceRepository.transferOwnership(...)` performs the membership role swap for in-memory and JDBC backends, and `WorkspaceController` returns the same public `WorkspaceMemberResponse` used by existing membership APIs.

**Tech Stack:** Java 17 bytecode target, Spring Boot MVC, Spring JDBC, JUnit 5, AssertJ, MockMvc, Testcontainers MySQL.

---

## File Structure

- Create `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceOwnerTransferControllerTest.java`
  - MockMvc RED/GREEN coverage for the new public API, RBAC, response secrecy, old-owner demotion, new-owner permission, invalid targets, and audit event.
- Modify `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`
  - Add repository-level contract coverage for in-memory and JDBC role swap semantics.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceController.java`
  - Add `POST /v1/workspaces/{workspaceId}/owner-transfer` and request DTO.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceService.java`
  - Add validation, owner-only guard, target membership checks, old owner demotion, audit record, and response record construction.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
  - Add `transferOwnership(workspaceId, currentOwnerUserId, newOwnerUserId, newOwnerTeamId)`.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
  - Implement the role swap without changing team membership.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
  - Implement the role swap with two `UPDATE workspace_members` statements.
- Modify `docs/architecture/java-team-backend-platform-spec.md`
  - Archive J15A status, API path, security behavior, and remaining gaps.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Append RED/GREEN/full verification evidence and boundaries.

## Task 1: RED HTTP Owner Transfer API Test

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceOwnerTransferControllerTest.java`

- [x] **Step 1: Write the failing MockMvc test**

Add this test class:

```java
package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.audit.AuditEventRecord;
import com.myworkflow.agent.backend.audit.AuditRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "my-workflow.backend.dev-principal.user-id=owner-transfer-old",
        "my-workflow.backend.dev-principal.team-id=team-owner-transfer",
        "my-workflow.backend.dev-principal.display-name=Old Owner",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-owner-transfer-test"
    }
)
@AutoConfigureMockMvc
class WorkspaceOwnerTransferControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuditRepository auditRepository;

  @Test
  void ownerTransfersOwnershipToExistingMemberAndOldOwnerLosesOwnerPowers() throws Exception {
    String workspaceId = createWorkspaceAs("owner-transfer-old", "Old Owner");
    grantMember(workspaceId, "owner-transfer-new", WorkspaceRole.WORKSPACE_EDITOR);

    mockMvc.perform(post("/v1/workspaces/{workspaceId}/owner-transfer", workspaceId)
            .headers(devHeaders("owner-transfer-old", "Old Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "newOwnerUserId": "owner-transfer-new"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.userId").value("owner-transfer-new"))
        .andExpect(jsonPath("$.data.teamId").value("team-owner-transfer"))
        .andExpect(jsonPath("$.data.role").value("WORKSPACE_OWNER"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist());

    MvcResult membersResult = mockMvc.perform(get("/v1/workspaces/{workspaceId}/members", workspaceId)
            .headers(devHeaders("owner-transfer-new", "New Owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andReturn();
    assertThat(JsonPath.<List<String>>read(membersResult.getResponse().getContentAsString(), "$.data[?(@.userId=='owner-transfer-old')].role"))
        .containsExactly("WORKSPACE_EDITOR");
    assertThat(JsonPath.<List<String>>read(membersResult.getResponse().getContentAsString(), "$.data[?(@.userId=='owner-transfer-new')].role"))
        .containsExactly("WORKSPACE_OWNER");

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "post-transfer-viewer")
            .headers(devHeaders("owner-transfer-old", "Old Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-owner-transfer",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "post-transfer-viewer")
            .headers(devHeaders("owner-transfer-new", "New Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-owner-transfer",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("post-transfer-viewer"))
        .andExpect(jsonPath("$.data.role").value("WORKSPACE_VIEWER"));

    List<AuditEventRecord> auditEvents = auditRepository.findByWorkspaceId(workspaceId);
    assertThat(auditEvents)
        .extracting(AuditEventRecord::eventType)
        .contains("WORKSPACE_CREATED", "WORKSPACE_MEMBER_GRANTED", "WORKSPACE_OWNER_TRANSFERRED");
    assertThat(auditEvents)
        .filteredOn((event) -> event.eventType().equals("WORKSPACE_OWNER_TRANSFERRED"))
        .singleElement()
        .satisfies((event) -> {
          assertThat(event.actorUserId()).isEqualTo("owner-transfer-old");
          assertThat(event.teamId()).isEqualTo("team-owner-transfer");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
          assertThat(event.runId()).isNull();
          assertThat(event.message()).contains("owner-transfer-new");
          assertThat(event.message()).doesNotContain("token");
        });
  }

  @Test
  void rejectsViewerSelfMissingAndAlreadyOwnerTransferRequests() throws Exception {
    String workspaceId = createWorkspaceAs("owner-transfer-old", "Old Owner");
    grantMember(workspaceId, "owner-transfer-viewer", WorkspaceRole.WORKSPACE_VIEWER);

    mockMvc.perform(post("/v1/workspaces/{workspaceId}/owner-transfer", workspaceId)
            .headers(devHeaders("owner-transfer-viewer", "Viewer"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "newOwnerUserId": "owner-transfer-viewer"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(post("/v1/workspaces/{workspaceId}/owner-transfer", workspaceId)
            .headers(devHeaders("owner-transfer-old", "Old Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "newOwnerUserId": "owner-transfer-old"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(post("/v1/workspaces/{workspaceId}/owner-transfer", workspaceId)
            .headers(devHeaders("owner-transfer-old", "Old Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "newOwnerUserId": "missing-transfer-user"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }
}
```

- [x] **Step 2: Run RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceOwnerTransferControllerTest test
```

Expected: FAIL with 404 `NOT_FOUND` for `POST /v1/workspaces/{workspaceId}/owner-transfer` after workspace create/grant fixtures succeed.

## Task 2: RED Repository Contract

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`

- [x] **Step 1: Add repository ownership transfer expectations**

Insert after the viewer grant assertions and before viewer revoke:

```java
repository.transferOwnership(workspaceId, ownerUserId, viewerUserId, teamId);
assertThat(repository.findRole(workspaceId, ownerUserId, teamId))
    .contains(WorkspaceRole.WORKSPACE_EDITOR);
assertThat(repository.findRole(workspaceId, viewerUserId, teamId))
    .contains(WorkspaceRole.WORKSPACE_OWNER);
assertThat(repository.findVisibleTo(ownerUserId, teamId))
    .extracting(WorkspaceRecord::workspaceId)
    .contains(workspaceId);
assertThat(repository.findVisibleTo(viewerUserId, teamId))
    .extracting(WorkspaceRecord::workspaceId)
    .contains(workspaceId);
```

- [x] **Step 2: Run RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test
```

Expected: FAIL at Java compilation because `WorkspaceRepository.transferOwnership(...)` does not exist.

## Task 3: Minimal Implementation

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceController.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`

- [x] **Step 1: Add controller endpoint and request DTO**

Add a `POST /{workspaceId}/owner-transfer` method that calls `workspaceService.transferOwnership(...)` and returns `WorkspaceMemberResponse`.

```java
@PostMapping(path = "/{workspaceId}/owner-transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
public ApiEnvelope<WorkspaceMemberResponse> transferOwnership(
    @PathVariable String workspaceId,
    @Valid @RequestBody TransferWorkspaceOwnerRequest request
) {
  return ApiEnvelope.ok(toMemberResponse(workspaceService.transferOwnership(
      workspaceId,
      request.newOwnerUserId()
  )));
}

public record TransferWorkspaceOwnerRequest(
    @NotBlank String newOwnerUserId
) {
}
```

- [x] **Step 2: Add service method**

Add `transferOwnership` that requires current principal owner role, rejects self-transfer, requires target existing in the same workspace/team, rejects already-owner target, calls repository, records `WORKSPACE_OWNER_TRANSFERRED`, and returns the promoted member.

```java
public WorkspaceMemberRecord transferOwnership(String workspaceId, String newOwnerUserId) {
  BackendPrincipal principal = principalProvider.currentPrincipal();
  WorkspaceRecord workspace = requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
  String normalizedNewOwnerUserId = normalizeMemberValue(
      newOwnerUserId,
      "New workspace owner user id is required"
  );
  if (principal.userId().equals(normalizedNewOwnerUserId)) {
    throw new IllegalArgumentException("Workspace owner transfer target must be a different user");
  }

  WorkspaceMemberRecord newOwnerMember = repository.findMember(workspace.workspaceId(), normalizedNewOwnerUserId)
      .orElseThrow(() -> new IllegalArgumentException("Workspace owner transfer target must be an existing member"));
  if (!workspace.teamId().equals(newOwnerMember.teamId())) {
    throw new IllegalArgumentException("Workspace owner transfer target must belong to the workspace team");
  }
  if (newOwnerMember.role() == WorkspaceRole.WORKSPACE_OWNER) {
    throw new IllegalArgumentException("Workspace owner transfer target is already an owner");
  }

  repository.transferOwnership(
      workspace.workspaceId(),
      principal.userId(),
      normalizedNewOwnerUserId,
      workspace.teamId()
  );
  auditService.record(
      workspace.workspaceId(),
      null,
      "WORKSPACE_OWNER_TRANSFERRED",
      "Workspace ownership transferred to %s".formatted(normalizedNewOwnerUserId)
  );
  return new WorkspaceMemberRecord(
      workspace.workspaceId(),
      normalizedNewOwnerUserId,
      workspace.teamId(),
      WorkspaceRole.WORKSPACE_OWNER
  );
}
```

- [x] **Step 3: Add repository interface method**

```java
void transferOwnership(String workspaceId, String currentOwnerUserId, String newOwnerUserId, String teamId);
```

- [x] **Step 4: Implement in-memory role swap**

```java
@Override
public void transferOwnership(
    String workspaceId,
    String currentOwnerUserId,
    String newOwnerUserId,
    String teamId
) {
  WorkspaceRecord workspace = workspaces.get(workspaceId);
  if (workspace == null) {
    throw new WorkspaceNotFoundException(workspaceId);
  }
  if (!workspace.teamId().equals(teamId)) {
    throw new IllegalArgumentException("Workspace owner transfer team id must match the workspace team");
  }

  memberships.compute(workspaceId, (ignored, existing) -> {
    List<WorkspaceMembership> current = existing == null ? List.of() : existing;
    List<WorkspaceMembership> updated = new ArrayList<>();
    for (WorkspaceMembership membership : current) {
      if (membership.userId().equals(currentOwnerUserId) && membership.teamId().equals(teamId)) {
        updated.add(new WorkspaceMembership(currentOwnerUserId, teamId, WorkspaceRole.WORKSPACE_EDITOR));
      } else if (membership.userId().equals(newOwnerUserId) && membership.teamId().equals(teamId)) {
        updated.add(new WorkspaceMembership(newOwnerUserId, teamId, WorkspaceRole.WORKSPACE_OWNER));
      } else {
        updated.add(membership);
      }
    }
    return List.copyOf(updated);
  });
}
```

- [x] **Step 5: Implement JDBC role swap**

```java
@Override
public void transferOwnership(
    String workspaceId,
    String currentOwnerUserId,
    String newOwnerUserId,
    String teamId
) {
  WorkspaceRecord workspace = findById(workspaceId)
      .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
  if (!workspace.teamId().equals(teamId)) {
    throw new IllegalArgumentException("Workspace owner transfer team id must match the workspace team");
  }
  jdbcTemplate.update(
      """
          UPDATE workspace_members
          SET role = ?
          WHERE workspace_id = ?
            AND user_id = ?
          """,
      WorkspaceRole.WORKSPACE_EDITOR.name(),
      workspaceId,
      currentOwnerUserId
  );
  jdbcTemplate.update(
      """
          UPDATE workspace_members
          SET role = ?
          WHERE workspace_id = ?
            AND user_id = ?
          """,
      WorkspaceRole.WORKSPACE_OWNER.name(),
      workspaceId,
      newOwnerUserId
  );
}
```

- [x] **Step 6: Run focused GREEN commands**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceOwnerTransferControllerTest test
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test
```

Expected: controller test and both repository contract implementations pass.

## Task 4: Documentation And Delivery Report

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-workspace-owner-transfer-baseline.md`

- [x] **Step 1: Update platform spec**

Add J15A to the top status line, add `POST /v1/workspaces/{workspaceId}/owner-transfer` to the API list, add a security-model paragraph, and append `### Phase J15: Workspace Owner Transfer`.

- [x] **Step 2: Update delivery report**

Append `## Phase J15A - Java Workspace Owner Transfer Baseline` with scope, RED evidence, focused verification, full verification, and boundaries. Explicitly state no real external provider call was executed.

- [x] **Step 3: Check off completed plan steps**

Update this plan’s checkboxes as each task is completed.

## Task 5: Full Verification And Self-Review

**Files:**
- Inspect all changed files.

- [x] **Step 1: Run full Java suite**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

Expected: all Java tests pass.

- [x] **Step 2: Run TypeScript regression suite**

```bash
npm test
npm run typecheck
```

Expected: existing TypeScript tests and typecheck pass.

- [x] **Step 3: Run diff, whitespace/conflict, and token scans**

```bash
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-owner-transfer-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: `git diff --check` exits 0; both `rg` scans return no matches.

- [x] **Step 4: Self-review**

Review these points before final response:

- Correctness: owner transfer only accepts an existing workspace member in the same team.
- Permission boundary: only current `WORKSPACE_OWNER` can transfer ownership; old owner is demoted.
- Public contract: member grant still rejects `WORKSPACE_OWNER`, and owner transfer is a separate API.
- Security: responses/audit do not expose `workspaceRoot`, `serverStorageRef`, provider payload, Authorization header, or token material.
- Persistence: in-memory and JDBC repository contracts agree.
- Scope: no HTTP server/UI/real provider/credential DB/SSE/production remote runner was added.
