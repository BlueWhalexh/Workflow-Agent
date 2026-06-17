package com.myworkflow.agent.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import java.time.Instant;
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
        "my-workflow.backend.dev-principal.user-id=owner-audit",
        "my-workflow.backend.dev-principal.team-id=team-audit-listing",
        "my-workflow.backend.dev-principal.display-name=Owner Audit",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-audit-controller-test",
        "my-workflow.backend.audit.retention-days=180"
    }
)
@AutoConfigureMockMvc
class AuditControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuditRepository auditRepository;

  @Test
  void ownerListsWorkspaceAuditEventsWhileViewerIsDenied() throws Exception {
    String workspaceId = createWorkspaceAs("owner-audit", "Owner Audit");
    grantViewer(workspaceId);

    MvcResult ownerResult = mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data[0].auditEventId").isString())
        .andExpect(jsonPath("$.data[0].actorUserId").value("owner-audit"))
        .andExpect(jsonPath("$.data[0].teamId").value("team-audit-listing"))
        .andExpect(jsonPath("$.data[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data[0].eventType").value("WORKSPACE_CREATED"))
        .andExpect(jsonPath("$.data[0].createdAt").isString())
        .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data[0].serverStorageRef").doesNotExist())
        .andReturn();

    String body = ownerResult.getResponse().getContentAsString();
    assertThat(JsonPath.<java.util.List<String>>read(body, "$.data[*].eventType"))
        .contains("WORKSPACE_CREATED", "WORKSPACE_MEMBER_GRANTED");
    assertThat(body)
        .doesNotContain("workspaceRoot")
        .doesNotContain("serverStorageRef")
        .doesNotContain("Authorization")
        .doesNotContain("rawProvider")
        .doesNotContain("token");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("viewer-audit", "Viewer Audit")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", "ws_missing")
            .headers(devHeaders("owner-audit", "Owner Audit")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void ownerReadsReportOnlyAuditRetentionPolicyWhileViewerIsDenied() throws Exception {
    String workspaceId = createWorkspaceAs("owner-audit", "Owner Audit");
    grantViewer(workspaceId);

    MvcResult policyResult = mockMvc.perform(get(
            "/v1/workspaces/{workspaceId}/audit-events/retention-policy",
            workspaceId
        )
            .headers(devHeaders("owner-audit", "Owner Audit")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.retentionDays").value(180))
        .andExpect(jsonPath("$.data.mode").value("REPORT_ONLY"))
        .andExpect(jsonPath("$.data.destructivePurgeEnabled").value(false))
        .andExpect(jsonPath("$.data.policySource").value("backend-config"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist())
        .andReturn();

    assertThat(policyResult.getResponse().getContentAsString())
        .doesNotContain("Authorization", "rawProvider", "token", "apiKey", "access_token");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].eventType").value("WORKSPACE_CREATED"));

    mockMvc.perform(get(
            "/v1/workspaces/{workspaceId}/audit-events/retention-policy",
            workspaceId
        )
            .headers(devHeaders("viewer-audit", "Viewer Audit")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
  }

  @Test
  void ownerFiltersAndPagesWorkspaceAuditEvents() throws Exception {
    String workspaceId = createWorkspaceAs("owner-audit", "Owner Audit");
    grantViewer(workspaceId);
    auditRepository.append(
        "owner-audit",
        "team-audit-listing",
        workspaceId,
        "run-audit-page-1",
        "ARTIFACT_READ",
        "Artifact read",
        Instant.parse("2030-01-01T00:00:00Z")
    );
    auditRepository.append(
        "owner-audit",
        "team-audit-listing",
        workspaceId,
        "run-audit-page-2",
        "APPROVAL_DECIDED",
        "Approval decided",
        Instant.parse("2030-01-01T00:00:01Z")
    );

    MvcResult filtered = mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .queryParam("eventType", "ARTIFACT_READ")
            .queryParam("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].eventType").value("ARTIFACT_READ"))
        .andExpect(jsonPath("$.data[0].runId").value("run-audit-page-1"))
        .andExpect(jsonPath("$.data[0].recordDigest").isString())
        .andExpect(jsonPath("$.data[0].previousRecordDigest").isString())
        .andExpect(jsonPath("$.data[0].chainDigest").isString())
        .andExpect(jsonPath("$.data[0].signatureKind").value("sha256-chain-v1"))
        .andExpect(jsonPath("$.data[0].signatureValue").isString())
        .andReturn();
    String filteredBody = filtered.getResponse().getContentAsString();
    String digest = JsonPath.read(filteredBody, "$.data[0].recordDigest");
    String chainDigest = JsonPath.read(filteredBody, "$.data[0].chainDigest");
    String signatureValue = JsonPath.read(filteredBody, "$.data[0].signatureValue");
    assertThat(digest).matches("sha256:[0-9a-f]{64}");
    assertThat(chainDigest).matches("sha256:[0-9a-f]{64}");
    assertThat(signatureValue).isEqualTo(chainDigest);
    assertThat(filteredBody)
        .doesNotContain("workspaceRoot")
        .doesNotContain("serverStorageRef")
        .doesNotContain("Authorization")
        .doesNotContain("rawProvider")
        .doesNotContain("token");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .queryParam("runId", "run-audit-page-2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].eventType").value("APPROVAL_DECIDED"));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .queryParam("limit", "2")
            .queryParam("offset", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].eventType").value("WORKSPACE_MEMBER_GRANTED"));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .queryParam("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
            .headers(devHeaders("viewer-audit", "Viewer Audit"))
            .queryParam("eventType", "ARTIFACT_READ"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
  }

  @Test
  void ownerExportsFilteredAuditEventsAsNdjsonWhileViewerIsDenied() throws Exception {
    String workspaceId = createWorkspaceAs("owner-audit", "Owner Audit");
    grantViewer(workspaceId);
    auditRepository.append(
        "owner-audit",
        "team-audit-listing",
        workspaceId,
        "run-audit-export-1",
        "ARTIFACT_READ",
        "Artifact read for export",
        Instant.parse("2030-01-02T00:00:00Z")
    );

    MvcResult exportResult = mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events/export", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .accept("application/x-ndjson")
            .queryParam("eventType", "ARTIFACT_READ")
            .queryParam("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/x-ndjson"))
        .andReturn();

    String body = exportResult.getResponse().getContentAsString();
    String[] lines = body.strip().split("\\R");
    assertThat(lines).hasSize(1);
    assertThat(lines[0])
        .contains("\"auditEventId\"")
        .contains("\"workspaceId\":\"%s\"".formatted(workspaceId))
        .contains("\"runId\":\"run-audit-export-1\"")
        .contains("\"eventType\":\"ARTIFACT_READ\"")
        .contains("\"recordDigest\":\"sha256:")
        .contains("\"previousRecordDigest\":\"sha256:")
        .contains("\"chainDigest\":\"sha256:")
        .contains("\"signatureKind\":\"sha256-chain-v1\"")
        .contains("\"signatureValue\":\"sha256:");
    assertThat(body)
        .doesNotContain("workspaceRoot")
        .doesNotContain("serverStorageRef")
        .doesNotContain("Authorization")
        .doesNotContain("rawProvider")
        .doesNotContain("token")
        .doesNotContain("apiKey")
        .doesNotContain("access_token");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events/export", workspaceId)
            .headers(devHeaders("viewer-audit", "Viewer Audit"))
            .accept("application/x-ndjson")
            .queryParam("eventType", "ARTIFACT_READ"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events/export", workspaceId)
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .accept("application/x-ndjson")
            .queryParam("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
  }

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Audit Listing Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private void grantViewer(String workspaceId) throws Exception {
    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, "viewer-audit")
            .headers(devHeaders("owner-audit", "Owner Audit"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-audit-listing",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk());
  }

  private static HttpHeaders devHeaders(String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-audit-listing");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
