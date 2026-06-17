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
        "my-workflow.backend.dev-principal.user-id=directory-sync-owner",
        "my-workflow.backend.dev-principal.team-id=team-directory-sync",
        "my-workflow.backend.dev-principal.display-name=Directory Sync Owner",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-team-directory-sync-test"
    }
)
@AutoConfigureMockMvc
class TeamDirectorySyncControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void teamAdminImportsExternalDirectorySnapshotWithoutRawPayloadLeakage() throws Exception {
    String teamId = "team-directory-sync-import";
    createWorkspace(teamId, "Directory Sync Import Workspace");

    MvcResult result = mockMvc.perform(post("/v1/teams/{teamId}/directory-sync", teamId)
            .headers(devHeaders(teamId, "directory-sync-owner", "Directory Sync Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "source": "okta-directory",
                  "disableMissing": false,
                  "members": [
                    {
                      "userId": "directory-sync-owner",
                      "displayName": "Directory Sync Owner",
                      "role": "TEAM_ADMIN"
                    },
                    {
                      "userId": "docs-editor",
                      "displayName": "Docs Editor",
                      "role": "TEAM_MEMBER"
                    }
                  ]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.teamId").value(teamId))
        .andExpect(jsonPath("$.data.source").value("okta-directory"))
        .andExpect(jsonPath("$.data.importedCount").value(2))
        .andExpect(jsonPath("$.data.disabledCount").value(0))
        .andExpect(jsonPath("$.data.members[?(@.userId == 'directory-sync-owner')].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.members[?(@.userId == 'directory-sync-owner')].role").value("TEAM_ADMIN"))
        .andExpect(jsonPath("$.data.members[?(@.userId == 'docs-editor')].displayName").value("Docs Editor"))
        .andExpect(jsonPath("$.data.members[?(@.userId == 'docs-editor')].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.rawPayload").doesNotExist())
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.data.secret").doesNotExist())
        .andExpect(jsonPath("$.data.authorization").doesNotExist())
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("rawPayload", "token", "secret", "Authorization", "workspaceRoot", "serverStorageRef");
  }

  @Test
  void teamMemberCannotSyncExternalDirectory() throws Exception {
    String teamId = "team-directory-sync-forbidden";
    String workspaceId = createWorkspace(teamId, "Directory Sync Forbidden Workspace");
    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "directory-viewer")
            .headers(devHeaders(teamId, "directory-sync-owner", "Directory Sync Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "%s",
                  "role": "WORKSPACE_VIEWER"
                }
                """.formatted(teamId)))
        .andExpect(status().isOk());

    mockMvc.perform(post("/v1/teams/{teamId}/directory-sync", teamId)
            .headers(devHeaders(teamId, "directory-viewer", "Directory Viewer"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "source": "okta-directory",
                  "members": [
                    {
                      "userId": "blocked-user",
                      "displayName": "Blocked User",
                      "role": "TEAM_MEMBER"
                    }
                  ]
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("TEAM_FORBIDDEN"));
  }

  @Test
  void rawDirectoryPayloadAliasesAreRejectedAndNotEchoed() throws Exception {
    String teamId = "team-directory-sync-raw-rejected";
    createWorkspace(teamId, "Directory Sync Raw Rejected Workspace");

    MvcResult result = mockMvc.perform(post("/v1/teams/{teamId}/directory-sync", teamId)
            .headers(devHeaders(teamId, "directory-sync-owner", "Directory Sync Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "source": "okta-directory",
                  "token": "raw-directory-token",
                  "authorization": "Bearer raw-directory-token",
                  "Authorization": "Bearer raw-directory-token",
                  "secret": "raw-directory-secret",
                  "rawPayload": "{\\"access_token\\":\\"raw-directory-token\\"}",
                  "members": [
                    {
                      "userId": "raw-user",
                      "displayName": "Raw User",
                      "role": "TEAM_MEMBER"
                    }
                  ]
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("raw-directory-token", "raw-directory-secret", "access_token");
  }

  @Test
  void disableMissingDisablesAbsentMembersButKeepsCurrentAdminActive() throws Exception {
    String teamId = "team-directory-sync-disable-missing";
    createWorkspace(teamId, "Directory Sync Disable Missing Workspace");

    sync(teamId, false, """
        [
          {
            "userId": "directory-sync-owner",
            "displayName": "Directory Sync Owner",
            "role": "TEAM_ADMIN"
          },
          {
            "userId": "missing-member",
            "displayName": "Missing Member",
            "role": "TEAM_MEMBER"
          },
          {
            "userId": "retained-member",
            "displayName": "Retained Member",
            "role": "TEAM_MEMBER"
          }
        ]
        """);

    MvcResult result = sync(teamId, true, """
        [
          {
            "userId": "retained-member",
            "displayName": "Retained Member",
            "role": "TEAM_MEMBER"
          }
        ]
        """);

    String response = result.getResponse().getContentAsString();
    List<String> disabledStatuses = JsonPath.read(
        response,
        "$.data.members[?(@.userId == 'missing-member')].status"
    );
    List<String> ownerStatuses = JsonPath.read(
        response,
        "$.data.members[?(@.userId == 'directory-sync-owner')].status"
    );

    assertThat(JsonPath.<Integer>read(response, "$.data.disabledCount")).isEqualTo(1);
    assertThat(disabledStatuses).containsExactly("DISABLED");
    assertThat(ownerStatuses).containsExactly("ACTIVE");
  }

  private MvcResult sync(String teamId, boolean disableMissing, String membersJson) throws Exception {
    return mockMvc.perform(post("/v1/teams/{teamId}/directory-sync", teamId)
            .headers(devHeaders(teamId, "directory-sync-owner", "Directory Sync Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "source": "okta-directory",
                  "disableMissing": %s,
                  "members": %s
                }
                """.formatted(disableMissing, membersJson)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String createWorkspace(String teamId, String name) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(teamId, "directory-sync-owner", "Directory Sync Owner"))
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
