package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-run-remote-http-test",
        "my-workflow.backend.agent-worker.kind=remote-http"
    }
)
@AutoConfigureMockMvc
class RemoteHttpAgentWorkerControllerTest {

  private static final RemoteWorkerStub REMOTE_WORKER = RemoteWorkerStub.start();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AgentRunRepository agentRunRepository;

  @DynamicPropertySource
  static void remoteWorkerProperties(DynamicPropertyRegistry registry) {
    registry.add("my-workflow.backend.agent-worker.remote-http.endpoint-url", REMOTE_WORKER::url);
    registry.add("my-workflow.backend.agent-worker.remote-http.timeout-ms", () -> "2000");
  }

  @AfterAll
  static void stopRemoteWorker() {
    REMOTE_WORKER.close();
  }

  @Test
  void javaApiCanSelectRemoteHttpWorkerAndRecordRemoteAttemptKind() throws Exception {
    String workspaceId = createWorkspace("Remote Worker");

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "总结当前知识库",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "SUCCEEDED");
    String body = completed.getResponse().getContentAsString();
    assertThat((String) JsonPath.read(body, "$.data.outputKind")).isEqualTo("answer");
    assertThat((String) JsonPath.read(body, "$.data.displayText")).isEqualTo("Remote answer from HTTP worker");
    assertThat((Boolean) JsonPath.read(body, "$.data.wroteWorkspace")).isFalse();
    assertThat(REMOTE_WORKER.lastRequestBody()).contains("\"runId\":\"%s\"".formatted(runId));
    assertThat(agentRunRepository.findAttempts(runId))
        .extracting(RunAttemptRecord::workerKind)
        .containsExactly("REMOTE_RUNNER");
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
              entry("displayText", "Remote answer from HTTP worker"),
              entry("requiresConfirmation", false),
              entry("requiresApproval", false),
              entry("artifactRefs", List.of(".agent-runs/%s/remote.json".formatted(runId))),
              entry("wroteWorkspace", false),
              entry("targetWorkspacePaths", List.of()),
              entry("source", Map.of("runtimePrivate", true))
          )
      ));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }
}
