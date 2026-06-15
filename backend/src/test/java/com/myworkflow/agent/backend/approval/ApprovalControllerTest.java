package com.myworkflow.agent.backend.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.run.AgentWorker;
import com.myworkflow.agent.backend.run.AgentWorkerResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
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
        ApprovalControllerTest.ApprovalControllerTestConfig.class
    },
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-approval-controller-test"
    }
)
@AutoConfigureMockMvc
class ApprovalControllerTest {

  private static final AtomicReference<String> WORKER_KIND = new AtomicReference<>("candidate-patch");

  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void resetWorker() {
    WORKER_KIND.set("candidate-patch");
  }

  @Test
  void candidatePatchCreatesPendingApprovalAndRejectDoesNotWriteWorkspace() throws Exception {
    String workspaceId = createWorkspace();
    MvcResult runResult = startRun(workspaceId, "准备候选补丁");
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "WAITING_APPROVAL");

    MvcResult approvalsResult = mockMvc.perform(get("/v1/agent-runs/{runId}/approvals", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].approvalId").isString())
        .andExpect(jsonPath("$.data[0].runId").value(runId))
        .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        .andExpect(jsonPath("$.data[0].artifactRef").value(".agent-runs/%s/artifact.json".formatted(runId)))
        .andExpect(jsonPath("$.data[0].targetWorkspacePaths[0]").value("knowledge-base/drafts/%s.md".formatted(runId)))
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist())
        .andReturn();
    String approvalId = JsonPath.read(approvalsResult.getResponse().getContentAsString(), "$.data[0].approvalId");

    mockMvc.perform(post("/v1/agent-runs/{runId}/approvals", runId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "approvalId": "%s",
                  "decision": "REJECTED"
                }
                """.formatted(approvalId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DECIDED"))
        .andExpect(jsonPath("$.data.decision").value("REJECTED"));

    mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("WAITING_APPROVAL"))
        .andExpect(jsonPath("$.data.wroteWorkspace").value(false));

    Path forbiddenWrite = Path.of(
        System.getProperty("java.io.tmpdir"),
        "my-workflow-agent-approval-controller-test",
        "teams",
        "dev-team",
        "workspaces",
        workspaceId,
        "content",
        "knowledge-base",
        "drafts",
        "%s.md".formatted(runId)
    );
    assertThat(Files.exists(forbiddenWrite)).isFalse();
  }

  @Test
  void confirmationRunDoesNotCreateApprovalRequest() throws Exception {
    WORKER_KIND.set("confirmation");
    String workspaceId = createWorkspace();
    MvcResult runResult = startRun(workspaceId, "需要确认");
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "WAITING_CONFIRMATION");

    mockMvc.perform(get("/v1/agent-runs/{runId}/approvals", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void approvalRequestRejectsClientSuppliedWorkspaceRoot() throws Exception {
    String workspaceId = createWorkspace();
    String runId = JsonPath.read(startRun(workspaceId, "准备候选补丁").getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "WAITING_APPROVAL");

    mockMvc.perform(post("/v1/agent-runs/{runId}/approvals", runId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "APPROVED",
                  "workspaceRoot": "/tmp/should-not-be-used"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  private MvcResult startRun(String workspaceId, String message) throws Exception {
    return mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "%s",
                  "mode": "llm-open-agent"
                }
                """.formatted(message)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private String createWorkspace() throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Approval Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
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

  @TestConfiguration
  static class ApprovalControllerTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return (request) -> {
        if ("confirmation".equals(WORKER_KIND.get())) {
          return new AgentWorkerResponse(
              "agent-backend-response.v1",
              request.runId(),
              "NEEDS_CONFIRMATION",
              "confirmation",
              "请确认",
              true,
              false,
              List.of(),
              false,
              List.of()
          );
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
