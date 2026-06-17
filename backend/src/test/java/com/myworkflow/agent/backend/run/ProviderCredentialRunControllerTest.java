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
        ProviderCredentialRunControllerTest.ProviderCredentialRunTestConfig.class
    },
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=user_provider_credential_run",
        "my-workflow.backend.dev-principal.team-id=team_provider_credential_run",
        "my-workflow.backend.dev-principal.display-name=Provider Credential Run User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-provider-credential-run-test"
    }
)
@AutoConfigureMockMvc
class ProviderCredentialRunControllerTest {

  private static final CapturingAgentWorker WORKER = new CapturingAgentWorker();

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_provider_credential_run_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProviderCredentialRepository credentialRepository;

  @BeforeEach
  void resetWorker() {
    WORKER.reset();
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
  void runRequestUsesDbBackedCredentialRefWithoutPassingSecretMaterial() throws Exception {
    String workspaceId = createWorkspace("Credential Ref Workspace");
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-workspace-mimo",
        "workspace-mimo",
        "team_provider_credential_run",
        workspaceId,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "env://MIMO_API_KEY",
        "ACTIVE"
    ));

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "用 DB credential ref 总结当前知识库",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "credential.workspace-mimo"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    pollRun(runId, "SUCCEEDED");
    Map<String, Object> providerRuntime = WORKER.lastProviderRuntime();
    assertThat(providerRuntime)
        .containsEntry("provider", "mimo-real")
        .containsEntry("apiKeyEnvName", "MIMO_API_KEY")
        .containsEntry("model", "mimo-v2.5")
        .containsEntry("baseUrl", "https://token-plan-cn.xiaomimimo.com/v1")
        .containsEntry("timeoutMs", 30_000);
    assertThat(providerRuntime)
        .doesNotContainKeys("apiKey", "token", "authorization", "Authorization", "apiKeySecretRef");
    assertThat(providerRuntime.toString()).doesNotContain("env://MIMO_API_KEY", "tp-raw-secret-should-not-pass");
  }

  @Test
  void runRequestRejectsCredentialRefsThatRequireSecretManagerResolution() throws Exception {
    String workspaceId = createWorkspace("Credential Ref Rejected Workspace");
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-secret-mimo",
        "secret-mimo",
        "team_provider_credential_run",
        workspaceId,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "secret://team-provider-credential-run/provider/mimo",
        "ACTIVE"
    ));

    MvcResult result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "不要在没有 secret manager 时执行",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "credential.secret-mimo"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("secret://team-provider-credential-run/provider/mimo");
    assertThat(WORKER.lastRequest()).isNull();
  }

  @Test
  void runRequestRejectsDisabledCredentialRefsBeforeWorkerInvocation() throws Exception {
    String workspaceId = createWorkspace("Disabled Credential Ref Workspace");
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-disabled-mimo",
        "disabled-mimo",
        "team_provider_credential_run",
        workspaceId,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        "env://MIMO_API_KEY",
        "DISABLED"
    ));

    MvcResult result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "禁用 credential 不应执行",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "credential.disabled-mimo"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("MIMO_API_KEY", "env://MIMO_API_KEY", "apiKeySecretRef");
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
  static class ProviderCredentialRunTestConfig {

    @Bean
    @Primary
    AgentWorker agentWorker() {
      return WORKER;
    }
  }

  private static final class CapturingAgentWorker implements AgentWorker {

    private final AtomicReference<AgentWorkerRequest> lastRequest = new AtomicReference<>();

    void reset() {
      lastRequest.set(null);
    }

    AgentWorkerRequest lastRequest() {
      return lastRequest.get();
    }

    Map<String, Object> lastProviderRuntime() {
      return lastRequest.get().providerRuntime();
    }

    @Override
    public AgentWorkerResponse run(AgentWorkerRequest request) {
      lastRequest.set(request);
      return new AgentWorkerResponse(
          "agent-backend-response.v1",
          request.runId(),
          "SUCCEEDED",
          "answer",
          "DB credential ref accepted",
          false,
          false,
          List.of(),
          false,
          List.of()
      );
    }
  }
}
