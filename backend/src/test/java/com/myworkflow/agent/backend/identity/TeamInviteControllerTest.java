package com.myworkflow.agent.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
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
        "my-workflow.backend.dev-principal.user-id=invite-owner",
        "my-workflow.backend.dev-principal.team-id=team-invite",
        "my-workflow.backend.dev-principal.display-name=Invite Owner",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-team-invite-test"
    }
)
@AutoConfigureMockMvc
class TeamInviteControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void teamAdminCreatesListsAndInviteeAcceptsInviteWithoutSecretFields() throws Exception {
    String teamId = "team-invite-happy";
    createWorkspace(teamId, "Team Invite Workspace");

    MvcResult createResult = mockMvc.perform(post("/v1/teams/{teamId}/invites", teamId)
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteeUserId": "invited-user",
                  "displayName": "Invited User",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.id").isString())
        .andExpect(jsonPath("$.data.teamId").value(teamId))
        .andExpect(jsonPath("$.data.inviteeUserId").value("invited-user"))
        .andExpect(jsonPath("$.data.displayName").value("Invited User"))
        .andExpect(jsonPath("$.data.role").value("TEAM_MEMBER"))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.createdByUserId").value("invite-owner"))
        .andExpect(jsonPath("$.data.createdAt").isString())
        .andExpect(jsonPath("$.data.updatedAt").isString())
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.data.secret").doesNotExist())
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist())
        .andReturn();

    String createBody = createResult.getResponse().getContentAsString();
    assertThat(createBody).doesNotContain("token", "secret", "workspaceRoot", "serverStorageRef");
    String inviteId = JsonPath.read(createBody, "$.data.id");

    MvcResult listResult = mockMvc.perform(get("/v1/teams/{teamId}/invites", teamId)
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(inviteId))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        .andReturn();
    assertThat(listResult.getResponse().getContentAsString())
        .doesNotContain("token", "secret", "workspaceRoot", "serverStorageRef");

    mockMvc.perform(post("/v1/teams/{teamId}/invites/{inviteId}/accept", teamId, inviteId)
            .headers(devHeaders(teamId, "invited-user", "Invited User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(inviteId))
        .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

    MvcResult membersResult = mockMvc.perform(get("/v1/teams/{teamId}/members", teamId)
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner")))
        .andExpect(status().isOk())
        .andReturn();
    List<String> userIds = JsonPath.read(membersResult.getResponse().getContentAsString(), "$.data[*].userId");
    List<String> inviteeStatuses = JsonPath.read(
        membersResult.getResponse().getContentAsString(),
        "$.data[?(@.userId == 'invited-user')].status"
    );
    assertThat(userIds).contains("invited-user");
    assertThat(inviteeStatuses).containsExactly("ACTIVE");
  }

  @Test
  void nonAdminCannotCreateInvite() throws Exception {
    String teamId = "team-invite-forbidden";
    String workspaceId = createWorkspace(teamId, "Team Invite Forbidden Workspace");
    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "member-user")
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "%s",
                  "role": "WORKSPACE_VIEWER"
                }
                """.formatted(teamId)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/v1/teams/{teamId}/invites", teamId)
            .headers(devHeaders(teamId, "member-user", "Member User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteeUserId": "blocked-invitee",
                  "displayName": "Blocked Invitee",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("TEAM_FORBIDDEN"));
  }

  @Test
  void revokedInviteCannotBeAccepted() throws Exception {
    String teamId = "team-invite-revoke";
    createWorkspace(teamId, "Team Invite Revoke Workspace");

    MvcResult createResult = mockMvc.perform(post("/v1/teams/{teamId}/invites", teamId)
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteeUserId": "revoked-invitee",
                  "displayName": "Revoked Invitee",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String inviteId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

    mockMvc.perform(post("/v1/teams/{teamId}/invites/{inviteId}/revoke", teamId, inviteId)
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("REVOKED"));

    mockMvc.perform(post("/v1/teams/{teamId}/invites/{inviteId}/accept", teamId, inviteId)
            .headers(devHeaders(teamId, "revoked-invitee", "Revoked Invitee")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  private String createWorkspace(String teamId, String name) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(teamId, "invite-owner", "Invite Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "%s",
                  "defaultBranch": "main"
                }
                """.formatted(name)))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private static HttpHeaders devHeaders(String teamId, String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", teamId);
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
