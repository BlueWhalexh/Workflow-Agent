package com.myworkflow.agent.backend.ops;

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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    classes = {
        BackendApplication.class,
        MysqlBackendPhaseAReadinessTest.TestAgentWorkerConfig.class
    },
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=phase_a_user",
        "my-workflow.backend.dev-principal.team-id=phase_a_team",
        "my-workflow.backend.dev-principal.display-name=Phase A User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-phase-a-readiness"
    }
)
@AutoConfigureMockMvc
class MysqlBackendPhaseAReadinessTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_phase_a_readiness")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void phaseAReadinessRunsCoreFrontendAndRuntimeFlowOnMysql() throws Exception {
    mockMvc.perform(get("/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.data.status").value("ready"));

    MvcResult workspaceResult = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Phase A MySQL Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist())
        .andReturn();
    String workspaceId = JsonPath.read(workspaceResult.getResponse().getContentAsString(), "$.data.workspaceId");

    mockMvc.perform(get("/v1/workspaces"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.workspaceId == '%s')]".formatted(workspaceId)).exists());

    MvcResult runResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "准备 Phase A MySQL readiness",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");

    MvcResult completed = pollRun(runId, "WAITING_APPROVAL");
    String completedBody = completed.getResponse().getContentAsString();
    assertThat(completedBody)
        .doesNotContain("token")
        .doesNotContain("apiKeySecretRef")
        .doesNotContain("workspaceRoot")
        .doesNotContain("serverStorageRef");

    mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.eventType == 'RUN_QUEUED')]").exists())
        .andExpect(jsonPath("$.data[?(@.eventType == 'COMPLETED')]").exists());

    MvcResult artifacts = mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef").value(".agent-runs/%s/phase-a-readiness.json".formatted(runId)))
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist())
        .andReturn();
    String artifactId = JsonPath.read(artifacts.getResponse().getContentAsString(), "$.data[0].artifactId");

    mockMvc.perform(get("/v1/artifacts/{artifactId}", artifactId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("{\"phase\":\"A\",\"mysql\":true}"))
        .andExpect(jsonPath("$.data.absolutePath").doesNotExist());

    mockMvc.perform(get("/v1/agent-runs/{runId}/approvals", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef").value(".agent-runs/%s/phase-a-readiness.json".formatted(runId)))
        .andExpect(jsonPath("$.data[0].targetWorkspacePaths[0]").value("knowledge-base/phase-a.md"));
  }

  @Test
  void mysqlBackedWorkspaceRunHistoryReturnsRecentPublicRunsAndReopensArtifactPreview() throws Exception {
    String workspaceId = createWorkspace("Frontend MySQL Run History");
    List<String> runIds = new ArrayList<>();
    for (int index = 0; index < 21; index++) {
      String runId = createRun(workspaceId, "frontend run history %02d".formatted(index));
      pollRun(runId, "WAITING_APPROVAL");
      runIds.add(runId);
      Thread.sleep(5);
    }

    String newestRunId = runIds.get(runIds.size() - 1);
    String oldestRunId = runIds.get(0);
    MvcResult history = mockMvc.perform(get("/v1/workspaces/{workspaceId}/agent-runs", workspaceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(20))
        .andExpect(jsonPath("$.data[0].runId").value(newestRunId))
        .andExpect(jsonPath("$.data[0].status").value("WAITING_APPROVAL"))
        .andExpect(jsonPath("$.data[0].outputKind").value("candidate-patch"))
        .andExpect(jsonPath("$.data[0].artifactRefs[0]")
            .value(".agent-runs/%s/phase-a-readiness.json".formatted(newestRunId)))
        .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data[0].source").doesNotExist())
        .andExpect(jsonPath("$.data[0].workerKind").doesNotExist())
        .andExpect(jsonPath("$.data[?(@.runId == '%s')]".formatted(oldestRunId)).doesNotExist())
        .andReturn();

    assertThat(history.getResponse().getContentAsString())
        .doesNotContain("apiKeySecretRef")
        .doesNotContain("rawProviderPayload")
        .doesNotContain("workspaceRoot");

    MvcResult artifacts = mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", newestRunId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef")
            .value(".agent-runs/%s/phase-a-readiness.json".formatted(newestRunId)))
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist())
        .andReturn();
    String artifactId = JsonPath.read(artifacts.getResponse().getContentAsString(), "$.data[0].artifactId");

    mockMvc.perform(get("/v1/artifacts/{artifactId}", artifactId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("{\"phase\":\"A\",\"mysql\":true}"))
        .andExpect(jsonPath("$.data.absolutePath").doesNotExist());
  }

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(100);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }

  private String createWorkspace(String name) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
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

  private String createRun(String workspaceId, String userMessage) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "%s",
                  "mode": "deterministic-open-agent"
                }
                """.formatted(userMessage)))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.runId");
  }

  @TestConfiguration
  static class TestAgentWorkerConfig {
    @Bean
    @Primary
    AgentWorker agentWorker() {
      return (request) -> {
        try {
          Path artifact = Path.of(request.workspaceRoot(), ".agent-runs", request.runId(), "phase-a-readiness.json");
          Files.createDirectories(artifact.getParent());
          Files.writeString(artifact, "{\"phase\":\"A\",\"mysql\":true}");
        } catch (Exception exception) {
          throw new IllegalStateException("failed to write phase A readiness artifact", exception);
        }
        return new AgentWorkerResponse(
            "agent-backend-response.v1",
            request.runId(),
            "WAITING_APPROVAL",
            "candidate-patch",
            "Phase A readiness candidate patch",
            false,
            true,
            List.of(".agent-runs/%s/phase-a-readiness.json".formatted(request.runId())),
            false,
            List.of("knowledge-base/phase-a.md")
        );
      };
    }
  }
}
