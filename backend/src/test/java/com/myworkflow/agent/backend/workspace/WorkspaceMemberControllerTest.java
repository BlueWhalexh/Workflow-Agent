package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        "my-workflow.backend.dev-principal.user-id=owner-user",
        "my-workflow.backend.dev-principal.team-id=team-members",
        "my-workflow.backend.dev-principal.display-name=Owner User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-members-test"
    }
)
@AutoConfigureMockMvc
class WorkspaceMemberControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuditRepository auditRepository;

  @Test
  void ownerGrantsAndListsMembersWhileViewerCannotMutateMembership() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "viewer-user")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-members",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.userId").value("viewer-user"))
        .andExpect(jsonPath("$.data.teamId").value("team-members"))
        .andExpect(jsonPath("$.data.role").value("WORKSPACE_VIEWER"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist());

    MvcResult membersResult = mockMvc.perform(get("/v1/workspaces/{workspaceId}/members", workspaceId)
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data[0].serverStorageRef").doesNotExist())
        .andReturn();

    List<String> userIds = JsonPath.read(membersResult.getResponse().getContentAsString(), "$.data[*].userId");
    assertThat(userIds).containsExactlyInAnyOrder("owner-user", "viewer-user");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspaceId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/members", workspaceId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "editor-user")
            .headers(devHeaders("viewer-user", "Viewer User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-members",
                  "role": "WORKSPACE_EDITOR"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "second-owner")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-members",
                  "role": "WORKSPACE_OWNER"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "other-team-user")
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "other-team",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

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

    MvcResult membersAfterRemoval = mockMvc.perform(get("/v1/workspaces/{workspaceId}/members", workspaceId)
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andReturn();
    List<String> remainingUserIds = JsonPath.read(
        membersAfterRemoval.getResponse().getContentAsString(),
        "$.data[*].userId"
    );
    assertThat(remainingUserIds).containsExactly("owner-user");

    List<AuditEventRecord> auditEvents = auditRepository.findByWorkspaceId(workspaceId);
    assertThat(auditEvents)
        .extracting(AuditEventRecord::eventType)
        .contains("WORKSPACE_CREATED", "WORKSPACE_MEMBER_GRANTED", "WORKSPACE_MEMBER_REMOVED");
    assertThat(auditEvents)
        .filteredOn((event) -> event.eventType().equals("WORKSPACE_MEMBER_GRANTED"))
        .singleElement()
        .satisfies((event) -> {
          assertThat(event.actorUserId()).isEqualTo("owner-user");
          assertThat(event.teamId()).isEqualTo("team-members");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
          assertThat(event.runId()).isNull();
          assertThat(event.message()).doesNotContain("token");
        });
    assertThat(auditEvents)
        .filteredOn((event) -> event.eventType().equals("WORKSPACE_MEMBER_REMOVED"))
        .singleElement()
        .satisfies((event) -> {
          assertThat(event.actorUserId()).isEqualTo("owner-user");
          assertThat(event.teamId()).isEqualTo("team-members");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
          assertThat(event.runId()).isNull();
          assertThat(event.message()).doesNotContain("token");
        });
  }

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Member Workspace",
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
    headers.add("X-Dev-Team-Id", "team-members");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
