# Java Team Directory Lifecycle Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow Java backend team directory lifecycle baseline: team admins can upsert and disable team members, team member listings expose stable directory metadata, and disabled team members cannot retain or be granted workspace access.

**Architecture:** Keep the directory data in the existing identity/workspace storage boundary because `team_memberships` already owns team membership facts. Add a membership `status` column, extend repository contracts for explicit team member upsert/disable, and enforce disabled membership checks before workspace grants. Do not add auth providers, external directory sync, UI, DB tenancy changes, or production secret-manager work in this slice.

**Tech Stack:** Spring Boot, MockMvc, JDBC `JdbcTemplate`, Flyway MySQL migrations, JUnit 5, AssertJ, Testcontainers MySQL.

---

### Task 1: RED Controller Test

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/identity/TeamDirectoryLifecycleControllerTest.java`

- [x] **Step 1: Write the failing test**

Create a MockMvc test that proves the desired API before implementation:

```java
package com.myworkflow.agent.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
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
        "my-workflow.backend.dev-principal.user-id=owner-user",
        "my-workflow.backend.dev-principal.team-id=team-directory-lifecycle",
        "my-workflow.backend.dev-principal.display-name=Owner User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-team-directory-lifecycle-test"
    }
)
@AutoConfigureMockMvc
class TeamDirectoryLifecycleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void teamAdminCanUpsertAndDisableMemberAndDisabledMemberCannotBeGrantedWorkspace() throws Exception {
    String workspaceId = createWorkspace();

    MvcResult upsertResult = mockMvc.perform(put(
            "/v1/teams/{teamId}/members/{userId}",
            "team-directory-lifecycle",
            "editor-user"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "Editor User",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.teamId").value("team-directory-lifecycle"))
        .andExpect(jsonPath("$.data.userId").value("editor-user"))
        .andExpect(jsonPath("$.data.displayName").value("Editor User"))
        .andExpect(jsonPath("$.data.role").value("TEAM_MEMBER"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andReturn();

    String body = upsertResult.getResponse().getContentAsString();
    assertThat(body).doesNotContain("workspaceRoot", "serverStorageRef", "secret", "token");

    MvcResult disableResult = mockMvc.perform(post(
            "/v1/teams/{teamId}/members/{userId}/disable",
            "team-directory-lifecycle",
            "editor-user"
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISABLED"))
        .andReturn();

    assertThat(disableResult.getResponse().getContentAsString())
        .doesNotContain("workspaceRoot", "serverStorageRef", "secret", "token");

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "editor-user")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-directory-lifecycle",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  @Test
  void teamMemberCannotManageDirectory() throws Exception {
    String workspaceId = createWorkspace();

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "member-user")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-directory-lifecycle",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(put(
            "/v1/teams/{teamId}/members/{userId}",
            "team-directory-lifecycle",
            "other-user"
        )
            .headers(devHeaders("member-user", "Member User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "Other User",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("TEAM_FORBIDDEN"));
  }

  private String createWorkspace() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Team Directory Lifecycle Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private static HttpHeaders devHeaders(String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-directory-lifecycle");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=TeamDirectoryLifecycleControllerTest
```

Expected: FAIL because `PUT /v1/teams/{teamId}/members/{userId}` is not implemented and returns `METHOD_NOT_ALLOWED`/`NOT_FOUND`, not because of fixture setup.

### Task 2: Repository Contract RED

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`

- [x] **Step 1: Add disabled membership contract**

Extend the contract to call:

```java
TeamMemberRecord disabled = repository.disableTeamMember(teamId, viewerUserId);
assertThat(disabled.status()).isEqualTo(TeamMemberStatus.DISABLED);
assertThatThrownBy(() -> repository.grantAccess(
    workspaceId,
    viewerUserId,
    teamId,
    WorkspaceRole.WORKSPACE_VIEWER
))
    .isInstanceOf(IllegalArgumentException.class);
```

- [x] **Step 2: Run in-memory contract to verify compile failure**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=InMemoryWorkspaceRepositoryContractTest
```

Expected: FAIL to compile because `TeamMemberStatus` and `disableTeamMember` do not exist yet.

### Task 3: Minimal Implementation

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamMemberStatus.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamMemberRecord.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamDirectoryService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
- Create: `backend/src/main/resources/db/migration/V12__team_member_lifecycle_baseline.sql`

- [x] **Step 1: Add stable team member status**

Add `ACTIVE` and `DISABLED` statuses. Extend `TeamMemberRecord` to carry `displayName` and `status`.

- [x] **Step 2: Add explicit repository methods**

Add `upsertTeamMember`, `disableTeamMember`, and `findTeamMember` to `WorkspaceRepository`; implement both in-memory and JDBC versions.

- [x] **Step 3: Add API methods**

Add:

```text
PUT /v1/teams/{teamId}/members/{userId}
POST /v1/teams/{teamId}/members/{userId}/disable
```

Return only stable directory fields: `teamId`, `userId`, `displayName`, `role`, `status`.

- [x] **Step 4: Enforce disabled access/grant boundary**

Before `grantAccess` creates or updates a workspace member, fail if an existing team membership for the target user is `DISABLED`. Access lookup must also treat disabled team membership as no workspace role, so disabled members cannot keep old workspace access.

- [x] **Step 5: Add migration**

Add `status` and `updated_at` to `team_memberships` with default `ACTIVE`. Existing membership rows remain active.

### Task 4: Documentation And Acceptance

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`

- [x] **Step 1: Update delivery report**

Record J34A as a local/fake-test backend capability slice. Do not describe it as full production directory sync, OIDC, or external IAM.

- [x] **Step 2: Update phase-one audit**

Move team directory lifecycle from “not implemented” to “partial baseline”: local team admin lifecycle API exists; external directory/OIDC sync remains open.

- [x] **Step 3: Run focused and full verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=TeamDirectoryLifecycleControllerTest,InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
git diff --stat
git diff --check
```

Expected: focused/full/typecheck pass, token scan does not expose real credentials, diff remains in the planned backend/docs files, and docs distinguish local tests from real external provider evidence.
