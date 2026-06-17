package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository;
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository.ProviderCredentialMetadata;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
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
        HttpProviderSecretRunControllerTest.HttpProviderSecretRunTestConfig.class
    },
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=user_http_provider_secret_run",
        "my-workflow.backend.dev-principal.team-id=team_http_provider_secret_run",
        "my-workflow.backend.dev-principal.display-name=HTTP Provider Secret Run User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-http-secret-run-test/data"
    }
)
@AutoConfigureMockMvc
class HttpProviderSecretRunControllerTest {

  private static final CapturingAgentWorker WORKER = new CapturingAgentWorker();
  private static final SecretManagerStub SECRET_MANAGER = SecretManagerStub.start();
  private static final String SECRET_REF = "secret://team-http-provider-secret-run/provider/mimo";
  private static final String RESOLVED_SECRET_VALUE = "http-provider-secret-not-real";

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_http_provider_secret_run_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProviderCredentialRepository credentialRepository;

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("my-workflow.backend.provider-secrets.http-resolver-uri", SECRET_MANAGER::url);
  }

  @AfterAll
  static void stopSecretManager() {
    SECRET_MANAGER.close();
  }

  @BeforeEach
  void resetWorkerAndSecretManager() {
    WORKER.reset();
    SECRET_MANAGER.reset();
  }

  @Test
  void runRequestInjectsHttpSecretManagerValueWithoutExposingSecretMaterial() throws Exception {
    String workspaceId = createWorkspace("HTTP Secret Credential Workspace");
    credentialRepository.save(new ProviderCredentialMetadata(
        "credential-http-mimo",
        "http-mimo",
        "team_http_provider_secret_run",
        workspaceId,
        "mimo-real",
        "mimo-v2.5",
        "https://token-plan-cn.xiaomimimo.com/v1",
        SECRET_REF,
        "ACTIVE"
    ));

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "用 http secret manager ref 总结当前知识库",
                  "mode": "llm-open-agent",
                  "providerRuntimeRef": "credential.http-mimo"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "SUCCEEDED");
    Map<String, Object> providerRuntime = WORKER.lastProviderRuntime();
    assertThat(providerRuntime)
        .containsEntry("provider", "mimo-real")
        .containsEntry("apiKeyEnvName", "PROVIDER_CREDENTIAL_API_KEY")
        .containsEntry("model", "mimo-v2.5")
        .containsEntry("baseUrl", "https://token-plan-cn.xiaomimimo.com/v1")
        .containsEntry("timeoutMs", 30_000);
    assertThat(providerRuntime)
        .doesNotContainKeys("apiKey", "token", "authorization", "Authorization", "apiKeySecretRef");
    assertThat(providerRuntime.toString()).doesNotContain(SECRET_REF, RESOLVED_SECRET_VALUE);
    assertThat(WORKER.lastRequest().toString()).doesNotContain(SECRET_REF, RESOLVED_SECRET_VALUE);
    assertThat(WORKER.lastSecretInjection().environmentVariables())
        .containsEntry("PROVIDER_CREDENTIAL_API_KEY", RESOLVED_SECRET_VALUE);
    assertThat(completed.getResponse().getContentAsString())
        .doesNotContain(SECRET_REF, RESOLVED_SECRET_VALUE, "apiKeySecretRef", "Authorization");
    assertThat(SECRET_MANAGER.lastRequestBody()).contains(SECRET_REF);
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
  static class HttpProviderSecretRunTestConfig {

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
    public String workerKind() {
      return "HTTP_SECRET_TEST_WORKER";
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
    public AgentWorkerResponse run(
        AgentWorkerRequest request,
        AgentWorkerSecretInjection secretInjection
    ) {
      lastRequest.set(request);
      lastSecretInjection.set(secretInjection);
      return new AgentWorkerResponse(
          "agent-backend-response.v1",
          request.runId(),
          "SUCCEEDED",
          "answer",
          "HTTP secret manager answer",
          false,
          false,
          List.of(".agent-runs/%s/http-secret-test.json".formatted(request.runId())),
          false,
          List.of()
      );
    }
  }

  private static final class SecretManagerStub implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<String> lastRequestBody;

    private SecretManagerStub(HttpServer server, AtomicReference<String> lastRequestBody) {
      this.server = server;
      this.lastRequestBody = lastRequestBody;
    }

    static SecretManagerStub start() {
      try {
        AtomicReference<String> lastRequestBody = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resolve", exchange -> handle(exchange, lastRequestBody));
        server.start();
        return new SecretManagerStub(server, lastRequestBody);
      } catch (IOException exception) {
        throw new IllegalStateException("Unable to start secret manager stub", exception);
      }
    }

    String url() {
      return "http://127.0.0.1:%d/resolve".formatted(server.getAddress().getPort());
    }

    void reset() {
      lastRequestBody.set("");
    }

    String lastRequestBody() {
      return lastRequestBody.get();
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private static void handle(HttpExchange exchange, AtomicReference<String> lastRequestBody) throws IOException {
      lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = """
          {
            "schemaVersion": "provider-secret-resolve-response.v1",
            "secretValue": "%s"
          }
          """.formatted(RESOLVED_SECRET_VALUE).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }
}
