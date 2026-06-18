package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=runtime_handoff_user",
        "my-workflow.backend.dev-principal.team-id=runtime_handoff_team",
        "my-workflow.backend.dev-principal.display-name=Runtime Handoff User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-runtime-handoff-test"
    }
)
@AutoConfigureMockMvc
class JdbcRuntimeHandoffControllerTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_runtime_handoff_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private WorkspaceService workspaceService;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void mysqlBackedJavaApiRunsLocalTsWorkerAndPersistsRuntimeEvidence() throws Exception {
    String workspaceId = createWorkspace("Runtime Handoff");
    Path repoRoot = Path.of("..").toAbsolutePath().normalize();
    TestWorkspaceCopier.copy(
        repoRoot.resolve("tests/fixtures/workspaces/basic-raw-mirror"),
        workspaceService.resolveContentPath(workspaceId, "")
    );

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "总结当前知识库",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("QUEUED"))
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "SUCCEEDED");
    String body = completed.getResponse().getContentAsString();
    assertThat((String) JsonPath.read(body, "$.data.outputKind")).isEqualTo("answer");
    assertThat((String) JsonPath.read(body, "$.data.displayText")).contains("Sources:");
    assertThat((Boolean) JsonPath.read(body, "$.data.wroteWorkspace")).isFalse();
    assertThat(body)
        .doesNotContain("apiKeySecretRef")
        .doesNotContain("Authorization")
        .doesNotContain("workspaceRoot");

    mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.eventType == 'RUN_QUEUED')]").exists())
        .andExpect(jsonPath("$.data[?(@.eventType == 'RUNNING')]").exists())
        .andExpect(jsonPath("$.data[?(@.eventType == 'COMPLETED')]").exists());

    mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef").isString())
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist());
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

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(150);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }
}
