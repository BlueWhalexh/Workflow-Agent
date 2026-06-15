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
