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
    String membersBody = membersResult.getResponse().getContentAsString();
    assertThat(JsonPath.<List<String>>read(membersBody, "$.data[?(@.userId=='owner-transfer-old')].role"))
        .containsExactly("WORKSPACE_EDITOR");
    assertThat(JsonPath.<List<String>>read(membersBody, "$.data[?(@.userId=='owner-transfer-new')].role"))
        .containsExactly("WORKSPACE_OWNER");
    assertThat(membersBody)
        .doesNotContain("workspaceRoot")
        .doesNotContain("serverStorageRef")
        .doesNotContain("Authorization")
        .doesNotContain("rawProvider")
        .doesNotContain("token");

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

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Owner Transfer Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private void grantMember(String workspaceId, String userId, WorkspaceRole role) throws Exception {
    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, userId)
            .headers(devHeaders("owner-transfer-old", "Old Owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-owner-transfer",
                  "role": "%s"
                }
                """.formatted(role.name())))
        .andExpect(status().isOk());
  }

  private static HttpHeaders devHeaders(String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-owner-transfer");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
