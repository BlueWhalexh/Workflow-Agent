package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository.ProviderCredentialMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
        FileProviderSecretRunControllerTest.FileProviderSecretRunTestConfig.class
    },
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=user_file_provider_secret_run",
        "my-workflow.backend.dev-principal.team-id=team_file_provider_secret_run",
        "my-workflow.backend.dev-principal.display-name=File Provider Secret Run User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-file-secret-run-test/data",
        "my-workflow.backend.provider-secrets.file-root=${java.io.tmpdir}/my-workflow-agent-file-secret-run-test/secrets"
    }
)
@AutoConfigureMockMvc
class FileProviderSecretRunControllerTest {

  private static final CapturingAgentWorker WORKER = new CapturingAgentWorker();
  private static final Path SECRET_ROOT = Path.of(
      System.getProperty("java.io.tmpdir"),
      "my-workflow-agent-file-secret-run-test",
      "secrets"
  );
  private static final String FILE_SECRET_REF = "file://mimo/api-key.txt";
  private static final String RESOLVED_SECRET_VALUE = "file-provider-secret-not-real";

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_file_provider_secret_run_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProviderCredentialRepository credentialRepository;

  @BeforeEach
  void resetWorkerAndSecretFile() throws Exception {
    WORKER.reset();
    Files.createDirectories(SECRET_ROOT.resolve("mimo"));
    Files.writeString(SECRET_ROOT.resolve("mimo/api-key.txt"), RESOLVED_SECRET_VALUE + "\n");
    Files.writeString(SECRET_ROOT.getParent().resolve("outside.txt"), "outside-file-secret-not-real");
  }

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void runRequestInjectsFileBackedSecretWithoutPuttingSecretInWorkerRequest() throws Exception {
    String workspaceId = createWorkspace("File Secret Credential Workspace");
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-file-mimo",
        "file-mimo",
        "team_file_provider_secret_run",
        workspaceId,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        FILE_SECRET_REF,
        "ACTIVE"
    ));

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "用 file secret ref 总结当前知识库",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "credential.file-mimo"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");
    Map<String, Object> providerRuntime = WORKER.lastProviderRuntime();
    assertThat(providerRuntime)
        .containsEntry("provider", "mimo-real")
        .containsEntry("apiKeyEnvName", "PROVIDER_CREDENTIAL_API_KEY")
        .containsEntry("model", "mimo-v2.5")
        .containsEntry("baseUrl", "https://token-plan-cn.xiaomimimo.com/v1")
        .containsEntry("timeoutMs", 30_000);
    assertThat(providerRuntime)
        .doesNotContainKeys("apiKey", "token", "authorization", "Authorization", "apiKeySecretRef");
    assertThat(providerRuntime.toString()).doesNotContain(FILE_SECRET_REF, RESOLVED_SECRET_VALUE);
    assertThat(WORKER.lastRequest().toString()).doesNotContain(FILE_SECRET_REF, RESOLVED_SECRET_VALUE);
    assertThat(WORKER.lastSecretInjection().environmentVariables())
        .containsEntry("PROVIDER_CREDENTIAL_API_KEY", RESOLVED_SECRET_VALUE);
    assertThat(WORKER.lastSecretInjection().toString())
        .contains("PROVIDER_CREDENTIAL_API_KEY")
        .doesNotContain(RESOLVED_SECRET_VALUE);
  }

  @Test
  void runRequestRejectsTraversalFileSecretRefBeforeWorkerInvocation() throws Exception {
    String workspaceId = createWorkspace("Traversal File Secret Credential Workspace");
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-file-traversal",
        "file-traversal",
        "team_file_provider_secret_run",
        workspaceId,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "file://../outside.txt",
        "ACTIVE"
    ));

    MvcResult result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "路径穿越不应执行",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "credential.file-traversal"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("file://../outside.txt", "outside-file-secret-not-real", "apiKeySecretRef");
    assertThat(WORKER.lastRequest()).isNull();
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
  static class FileProviderSecretRunTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private static final class CapturingAgentWorker implements AgentWorker {

    private final AtomicReference<AgentWorkerRequest> lastRequest = new AtomicReference<>();
    private final AtomicReference<AgentWorkerSecretInjection> lastSecretInjection =
        new AtomicReference<>(AgentWorkerSecretInjection.empty());

    void reset() {
      lastRequest.set(null);
      lastSecretInjection.set(AgentWorkerSecretInjection.empty());
    }

    AgentWorkerRequest lastRequest() {
      return lastRequest.get();
    }

    Map<String, Object> lastProviderRuntime() {
      return lastRequest.get().providerRuntime();
    }

    AgentWorkerSecretInjection lastSecretInjection() {
      return lastSecretInjection.get();
    }

    @Override
    public boolean supportsSecretInjection() {
      return true;
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      return run(request, AgentWorkerSecretInjection.empty());
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request, AgentWorkerSecretInjection secretInjection) {
      lastRequest.set(request);
      lastSecretInjection.set(secretInjection);
      return new AgentWorkerResponse(
          "agent-backend-response.v1",
          request.runId(),
          "SUCCEEDED",
          "answer",
          "File credential ref accepted",
          false,
          false,
          List.of(),
          false,
          List.of()
      );
    }
  }
}
