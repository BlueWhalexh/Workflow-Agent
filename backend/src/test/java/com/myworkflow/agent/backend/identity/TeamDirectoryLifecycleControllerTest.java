package com.myworkflow.agent.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
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

    assertThat(upsertResult.getResponse().getContentAsString())
        .doesNotContain("workspaceRoot", "serverStorageRef", "secret", "token");

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

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "manager-member-user")
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
            .headers(devHeaders("manager-member-user", "Manager Member User"))
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

  @Test
  void disabledTeamMemberCannotKeepExistingWorkspaceAccess() throws Exception {
    String workspaceId = createWorkspace();

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "disabled-member-user")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-directory-lifecycle",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(post(
            "/v1/teams/{teamId}/members/{userId}/disable",
            "team-directory-lifecycle",
            "disabled-member-user"
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk());

    mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspaceId)
            .headers(devHeaders("disabled-member-user", "Disabled Member User")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
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
