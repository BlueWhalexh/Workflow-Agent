package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.audit.AuditEventRecord;
import com.myworkflow.agent.backend.audit.AuditRepository;
import com.myworkflow.agent.backend.run.AgentWorker;
import com.myworkflow.agent.backend.run.AgentWorkerResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
        BackendApplication.class,
        WorkspaceRoleAuthorizationTest.WorkspaceRoleAuthorizationTestConfig.class
    },
    properties = {
        "my-workflow.backend.dev-principal.user-id=owner-user",
        "my-workflow.backend.dev-principal.team-id=team-rbac",
        "my-workflow.backend.dev-principal.display-name=Owner User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-rbac-controller-test"
    }
)
@AutoConfigureMockMvc
class WorkspaceRoleAuthorizationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private WorkspaceRepository workspaceRepository;

  @Autowired
  private AuditRepository auditRepository;

  @Test
  void workspaceRolesControlReadRunApprovalAndAuditSensitiveActions() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");
    workspaceRepository.grantAccess(workspaceId, "viewer-user", "team-rbac", WorkspaceRole.WORKSPACE_VIEWER);
    workspaceRepository.grantAccess(workspaceId, "editor-user", "team-rbac", WorkspaceRole.WORKSPACE_EDITOR);

    MvcResult runResult = startCandidateRunAs("owner-user", "Owner User", workspaceId);
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");
    pollRunAs("owner-user", "Owner User", runId, "WAITING_APPROVAL");

    MvcResult approvalsResult = mockMvc.perform(get("/v1/agent-runs/{runId}/approvals", runId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        .andReturn();
    String approvalId = JsonPath.read(approvalsResult.getResponse().getContentAsString(), "$.data[0].approvalId");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspaceId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId));

    mockMvc.perform(get("/v1/agent-runs/{runId}", runId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.runId").value(runId));

    mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .headers(devHeaders("viewer-user", "Viewer User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "viewer must not start",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(post("/v1/agent-runs/{runId}/approvals", runId)
            .headers(devHeaders("viewer-user", "Viewer User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "approvalId": "%s",
                  "decision": "REJECTED"
                }
                """.formatted(approvalId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    MvcResult artifactList = mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", runId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactId").isString())
        .andReturn();
    String artifactId = JsonPath.read(artifactList.getResponse().getContentAsString(), "$.data[0].artifactId");
    mockMvc.perform(get("/v1/artifacts/{artifactId}", artifactId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("{\"ok\":true}"));

    mockMvc.perform(post("/v1/agent-runs/{runId}/approvals", runId)
            .headers(devHeaders("editor-user", "Editor User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "approvalId": "%s",
                  "decision": "REJECTED"
                }
                """.formatted(approvalId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decidedByUserId").value("editor-user"))
        .andExpect(jsonPath("$.data.decision").value("REJECTED"));

    List<AuditEventRecord> auditEvents = auditRepository.findByRunId(runId);
    assertThat(auditEvents)
        .extracting(AuditEventRecord::eventType)
        .contains("AGENT_RUN_REQUESTED", "ARTIFACT_READ", "APPROVAL_DECIDED");
    assertThat(auditEvents)
        .filteredOn((event) -> event.eventType().equals("ARTIFACT_READ"))
        .extracting(AuditEventRecord::actorUserId)
        .contains("viewer-user");
    assertThat(auditEvents)
        .filteredOn((event) -> event.eventType().equals("APPROVAL_DECIDED"))
        .extracting(AuditEventRecord::actorUserId)
        .contains("editor-user");
    assertThat(auditEvents)
        .allSatisfy((event) -> {
          assertThat(event.teamId()).isEqualTo("team-rbac");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
        });
  }

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "RBAC Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private MvcResult startCandidateRunAs(String userId, String displayName, String workspaceId) throws Exception {
    return mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "prepare approval",
                  "mode": "llm-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
  }

  private MvcResult pollRunAs(String userId, String displayName, String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId)
              .headers(devHeaders(userId, displayName)))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(50);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }

  private static org.springframework.http.HttpHeaders devHeaders(String userId, String displayName) {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-rbac");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }

  @TestConfiguration
  static class WorkspaceRoleAuthorizationTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return (request) -> {
        try {
          Path artifact = Path.of(request.workspaceRoot(), ".agent-runs", request.runId(), "artifact.json");
          Files.createDirectories(artifact.getParent());
          Files.writeString(artifact, "{\"ok\":true}");
        } catch (Exception exception) {
          throw new IllegalStateException("failed to write fake artifact", exception);
        }
        return new AgentWorkerResponse(
            "agent-backend-response.v1",
            request.runId(),
            "WAITING_APPROVAL",
            "candidate-patch",
            "Candidate patch needs approval.",
            false,
            true,
            List.of(".agent-runs/%s/artifact.json".formatted(request.runId())),
            false,
            List.of("knowledge-base/drafts/%s.md".formatted(request.runId()))
        );
      };
    }
  }
}
