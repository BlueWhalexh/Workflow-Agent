# Java Team Member Listing Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow `GET /v1/teams/{teamId}/members` API that lists backend-known members for the current principal's team without exposing a global user/team directory.

**Architecture:** Keep team membership as metadata separate from workspace membership. Workspace creation records the owner as `TEAM_ADMIN`; workspace member grants record the target user as `TEAM_MEMBER`; workspace member removal only revokes workspace access and does not delete team membership. `IdentityController` exposes team member listing through a small `TeamDirectoryService` that forbids cross-team requests.

**Tech Stack:** Spring Boot MVC, Java records/enums, Spring JDBC, MockMvc, Testcontainers repository contract tests.

---

### Task 1: HTTP Team Member Listing

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/identity/TeamMemberControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamDirectoryService.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamForbiddenException.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`

- [x] **Step 1: Write the failing HTTP test**

Create `TeamMemberControllerTest`:

```java
package com.myworkflow.agent.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "my-workflow.backend.dev-principal.team-id=team-directory",
        "my-workflow.backend.dev-principal.display-name=Owner User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-team-members-test"
    }
)
@AutoConfigureMockMvc
class TeamMemberControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void currentTeamMembersListsBackendKnownMembersWithoutWorkspaceInternals() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");
    grantViewer(workspaceId);

    MvcResult membersResult = mockMvc.perform(get("/v1/teams/{teamId}/members", "team-directory")
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data[0].serverStorageRef").doesNotExist())
        .andExpect(jsonPath("$.data[0].token").doesNotExist())
        .andReturn();

    List<String> userIds = JsonPath.read(membersResult.getResponse().getContentAsString(), "$.data[*].userId");
    List<String> roles = JsonPath.read(membersResult.getResponse().getContentAsString(), "$.data[*].role");
    assertThat(userIds).containsExactlyInAnyOrder("owner-user", "viewer-user");
    assertThat(roles).containsExactlyInAnyOrder("TEAM_ADMIN", "TEAM_MEMBER");

    mockMvc.perform(get("/v1/teams/{teamId}/members", "other-team")
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("TEAM_FORBIDDEN"));

    mockMvc.perform(delete("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "viewer-user")
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk());

    MvcResult membersAfterWorkspaceRemoval = mockMvc.perform(get("/v1/teams/{teamId}/members", "team-directory")
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andReturn();
    List<String> remainingTeamUserIds = JsonPath.read(
        membersAfterWorkspaceRemoval.getResponse().getContentAsString(),
        "$.data[*].userId"
    );
    assertThat(remainingTeamUserIds).containsExactlyInAnyOrder("owner-user", "viewer-user");
  }

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Team Directory Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private void grantViewer(String workspaceId) throws Exception {
    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "viewer-user")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-directory",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk());
  }

  private static HttpHeaders devHeaders(String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-directory");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
```

- [x] **Step 2: Run focused HTTP RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=TeamMemberControllerTest test
```

Expected: FAIL with 404 `NOT_FOUND` for `GET /v1/teams/{teamId}/members`, after workspace create and member grant fixtures succeed.

- [x] **Step 3: Implement minimal HTTP path**

Add `TeamDirectoryService`:

```java
@Service
public class TeamDirectoryService {
  private final PrincipalProvider principalProvider;
  private final WorkspaceRepository workspaceRepository;

  public TeamDirectoryService(PrincipalProvider principalProvider, WorkspaceRepository workspaceRepository) {
    this.principalProvider = principalProvider;
    this.workspaceRepository = workspaceRepository;
  }

  public List<TeamMemberRecord> listCurrentTeamMembers(String teamId) {
    BackendPrincipal principal = principalProvider.currentPrincipal();
    String normalizedTeamId = normalizeTeamId(teamId);
    if (!principal.teamId().equals(normalizedTeamId)) {
      throw new TeamForbiddenException(normalizedTeamId);
    }
    List<TeamMemberRecord> members = new ArrayList<>(workspaceRepository.listKnownTeamMembers(normalizedTeamId));
    boolean hasCurrentPrincipal = members.stream()
        .anyMatch((member) -> member.userId().equals(principal.userId()));
    if (!hasCurrentPrincipal) {
      members.add(new TeamMemberRecord(normalizedTeamId, principal.userId(), TeamRole.TEAM_ADMIN));
    }
    return members.stream()
        .sorted(Comparator.comparing(TeamMemberRecord::userId))
        .toList();
  }
}
```

Wire `IdentityController` with `TeamDirectoryService`, add `@GetMapping("/teams/{teamId}/members")`, and add `TeamMemberResponse`.

Add `TeamForbiddenException` and map it in `GlobalApiExceptionHandler` to 403 `TEAM_FORBIDDEN`.

### Task 2: Team Membership Repository Contract

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamMemberRecord.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/TeamRole.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`

- [x] **Step 1: Extend repository contract test first**

In `WorkspaceRepositoryContract`, after saving owner and granting viewer, assert:

```java
assertThat(repository.listKnownTeamMembers(teamId))
    .extracting(
        TeamMemberRecord::teamId,
        TeamMemberRecord::userId,
        TeamMemberRecord::role
    )
    .contains(
        tuple(teamId, ownerUserId, TeamRole.TEAM_ADMIN),
        tuple(teamId, viewerUserId, TeamRole.TEAM_MEMBER)
    );
```

After `repository.revokeAccess(...)`, assert the viewer still exists in team membership:

```java
assertThat(repository.listKnownTeamMembers(teamId))
    .extracting(TeamMemberRecord::userId)
    .contains(viewerUserId);
```

- [x] **Step 2: Run repository RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test
```

Expected: compile FAIL because `TeamMemberRecord`, `TeamRole`, and `WorkspaceRepository.listKnownTeamMembers(...)` do not exist yet.

- [x] **Step 3: Implement repository methods**

Add:

```java
public record TeamMemberRecord(String teamId, String userId, TeamRole role) {}

public enum TeamRole {
  TEAM_ADMIN,
  TEAM_MEMBER
}
```

Add `List<TeamMemberRecord> listKnownTeamMembers(String teamId)` to `WorkspaceRepository`.

In in-memory repository, add a separate `teamMemberships` map. `save(...)` records owner as `TEAM_ADMIN`; `grantAccess(...)` records target as `TEAM_MEMBER`; `revokeAccess(...)` does not delete team membership.

In JDBC repository, update `upsertTeamMembership` to accept `TeamRole`. `save(...)` passes `TEAM_ADMIN`; `grantAccess(...)` passes `TEAM_MEMBER` without demoting existing admins. Add `listKnownTeamMembers(...)` querying `team_memberships`.

- [x] **Step 4: Run repository GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test
```

Expected: PASS for in-memory and Testcontainers MySQL repository implementations.

### Task 3: Documentation And Verification

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-team-member-listing-baseline.md`

- [x] **Step 1: Update architecture spec**

Record J14A as delivered: `GET /v1/teams/{teamId}/members` lists backend-known members for the current team; workspace removal does not delete team membership; full user/team directory CRUD, invitations, owner transfer, OIDC/OAuth, and cross-team discovery remain future work.

- [x] **Step 2: Update delivery report**

Add RED/GREEN/full validation evidence and state that no real provider call was made.

- [x] **Step 3: Run validation**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-team-member-listing-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TypeScript tests, and typecheck pass; diff check passes; whitespace/conflict/token scans return no matches.

### Self-Review

- Spec coverage: covers team member listing and separates team membership from workspace membership. Team CRUD, invitations, user profile directory, cross-team discovery, OIDC/OAuth, owner transfer, audit pagination/export, SSE/WebSocket, credential DB/secret manager, and production remote runner remain out of scope.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: endpoint, service, repository, and tests use `TeamMemberRecord`, `TeamRole`, and `listKnownTeamMembers`.
