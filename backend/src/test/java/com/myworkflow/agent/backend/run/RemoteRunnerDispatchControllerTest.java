package com.myworkflow.agent.backend.run;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
        "my-workflow.backend.dev-principal.user-id=runner-dispatch-owner",
        "my-workflow.backend.dev-principal.team-id=team-runner-dispatch",
        "my-workflow.backend.dev-principal.display-name=Runner Dispatch Owner",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-remote-runner-dispatch-test"
    }
)
@AutoConfigureMockMvc
class RemoteRunnerDispatchControllerTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_remote_runner_dispatch_test")
      .withUsername("test")
      .withPassword("test");

  private static final RemoteWorkerStub REMOTE_WORKER = RemoteWorkerStub.start();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AgentRunRepository agentRunRepository;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @AfterAll
  static void stopRemoteWorker() {
    REMOTE_WORKER.close();
  }

  @Test
  void ownerCanDispatchRunToRegisteredOnlineRemoteRunnerWithoutSecretMaterial() throws Exception {
    String workspaceId = createWorkspace("Remote Runner Dispatch Workspace");
    registerRunner(workspaceId, "runner-dispatch-1");
    heartbeatRunner(workspaceId, "runner-dispatch-1");

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .headers(devHeaders())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "用远程 runner 总结当前知识库",
                  "mode": "deterministic-open-agent",
                  "remoteRunnerRef": "runner-dispatch-1"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.remoteRunnerRef").doesNotExist())
        .andExpect(jsonPath("$.data.endpointUrl").doesNotExist())
        .andExpect(jsonPath("$.data.runnerToken").doesNotExist())
        .andExpect(jsonPath("$.data.signatureSecret").doesNotExist())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "SUCCEEDED");
    String response = completed.getResponse().getContentAsString();

    assertThat((String) JsonPath.read(response, "$.data.outputKind")).isEqualTo("answer");
    assertThat((String) JsonPath.read(response, "$.data.displayText")).isEqualTo("Registered remote runner answer");
    assertThat(REMOTE_WORKER.lastRequestBody()).contains("\"runId\":\"%s\"".formatted(runId));
    assertThat(REMOTE_WORKER.lastRequestBody()).doesNotContain("runnerToken", "signatureSecret", "apiKey");
    assertThat(response).doesNotContain("runnerToken", "signatureSecret", "Authorization", "apiKey", "endpointUrl");
    assertThat(agentRunRepository.findAttempts(runId))
        .extracting(RunAttemptRecord::workerKind)
        .containsExactly("REMOTE_RUNNER");
  }

  @Test
  void registeredButNotOnlineRunnerCannotBeUsedForDispatch() throws Exception {
    String workspaceId = createWorkspace("Remote Runner Offline Workspace");
    registerRunner(workspaceId, "runner-not-online");

    MvcResult result = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .headers(devHeaders())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "不要调度未 online 的 runner",
                  "mode": "deterministic-open-agent",
                  "remoteRunnerRef": "runner-not-online"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("runnerToken", "signatureSecret", "Authorization", "apiKey");
  }

  private String createWorkspace(String name) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders())
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

  private void registerRunner(String workspaceId, String runnerRef) throws Exception {
    MvcResult result = mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}",
            workspaceId,
            runnerRef
        )
            .headers(devHeaders())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "Registered test runner",
                  "endpointUrl": "%s",
                  "capabilities": ["agent-backend-response.v1", "workspace-read"]
                }
                """.formatted(REMOTE_WORKER.url())))
        .andExpect(status().isOk())
        .andReturn();

    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("runnerToken", "signatureSecret", "Authorization", "apiKey");
  }

  private void heartbeatRunner(String workspaceId, String runnerRef) throws Exception {
    mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat",
            workspaceId,
            runnerRef
        )
            .headers(devHeaders()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ONLINE"));
  }

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId)
              .headers(devHeaders()))
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

  private static HttpHeaders devHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", "runner-dispatch-owner");
    headers.add("X-Dev-Team-Id", "team-runner-dispatch");
    headers.add("X-Dev-Display-Name", "Runner Dispatch Owner");
    return headers;
  }

  private static final class RemoteWorkerStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final AtomicReference<String> lastRequestBody;

    private RemoteWorkerStub(HttpServer server, AtomicReference<String> lastRequestBody) {
      this.server = server;
      this.lastRequestBody = lastRequestBody;
    }

    static RemoteWorkerStub start() {
      try {
        AtomicReference<String> lastRequestBody = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/run", (exchange) -> handle(exchange, lastRequestBody));
        server.start();
        return new RemoteWorkerStub(server, lastRequestBody);
      } catch (IOException exception) {
        throw new IllegalStateException("Unable to start remote worker stub", exception);
      }
    }

    String url() {
      return "http://127.0.0.1:%d/run".formatted(server.getAddress().getPort());
    }

    String lastRequestBody() {
      return lastRequestBody.get();
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private static void handle(HttpExchange exchange, AtomicReference<String> lastRequestBody) throws IOException {
      String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      lastRequestBody.set(requestBody);
      @SuppressWarnings("unchecked")
      Map<String, Object> request = OBJECT_MAPPER.readValue(requestBody, Map.class);
      String runId = (String) request.get("runId");
      byte[] response = OBJECT_MAPPER.writeValueAsBytes(Map.of(
          "schemaVersion", "agent-remote-runner-result.v1",
          "workerKind", "REMOTE_RUNNER",
          "signatureKind", "unsigned-local-spike",
          "result", Map.ofEntries(
              entry("schemaVersion", "agent-backend-response.v1"),
              entry("runId", runId),
              entry("status", "SUCCEEDED"),
              entry("outputKind", "answer"),
              entry("displayText", "Registered remote runner answer"),
              entry("requiresConfirmation", false),
              entry("requiresApproval", false),
              entry("artifactRefs", List.of(".agent-runs/%s/remote-runner.json".formatted(runId))),
              entry("wroteWorkspace", false),
              entry("targetWorkspacePaths", List.of())
          )
      ));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }
}
