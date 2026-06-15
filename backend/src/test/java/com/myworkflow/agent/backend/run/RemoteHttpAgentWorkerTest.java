package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RemoteHttpAgentWorkerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void postsWorkerRequestAndReturnsNestedBackendEnvelope() throws Exception {
    try (RemoteWorkerServer server = RemoteWorkerServer.start(objectMapper, this::successfulEnvelope)) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000);

      AgentWorkerResponse response = worker.run(new AgentWorkerRequest(
          "run_remote_answer",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      ));

      assertThat(worker.workerKind()).isEqualTo("REMOTE_RUNNER");
      assertThat(server.lastRequestBody()).contains("\"runId\":\"run_remote_answer\"");
      assertThat(server.lastRequestBody()).contains("\"workspaceRoot\":\"/server/workspaces/ws_1/content\"");
      assertThat(server.lastRequestBody()).doesNotContain("source");
      assertThat(response.schemaVersion()).isEqualTo("agent-backend-response.v1");
      assertThat(response.runId()).isEqualTo("run_remote_answer");
      assertThat(response.status()).isEqualTo("SUCCEEDED");
      assertThat(response.outputKind()).isEqualTo("answer");
      assertThat(response.displayText()).isEqualTo("Remote backend answer");
      assertThat(Arrays.stream(AgentWorkerResponse.class.getRecordComponents())
          .map(component -> component.getName()))
          .doesNotContain("source");
    }
  }

  @Test
  void rejectsUnsupportedRemoteRunnerEnvelope() throws Exception {
    try (RemoteWorkerServer server = RemoteWorkerServer.start(objectMapper, ignored -> Map.of(
        "schemaVersion", "unsupported.v1",
        "workerKind", "REMOTE_RUNNER",
        "signatureKind", "unsigned-local-spike",
        "result", Map.of(
            "schemaVersion", "agent-backend-response.v1",
            "runId", "run_remote_bad",
            "status", "SUCCEEDED",
            "outputKind", "answer",
            "displayText", "Bad envelope",
            "requiresConfirmation", false,
            "requiresApproval", false,
            "artifactRefs", List.of(),
            "wroteWorkspace", false,
            "targetWorkspacePaths", List.of()
        )
    ))) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000);

      assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
          "run_remote_bad",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      )))
          .isInstanceOf(AgentWorkerException.class)
          .hasMessageContaining("unsupported remote runner envelope");
    }
  }

  @Test
  void verifiesHmacSignedRemoteRunnerEnvelopeAndRejectsTampering() throws Exception {
    String signatureSecret = "runner-secret-for-test";
    try (RemoteWorkerServer server = RemoteWorkerServer.start(
        objectMapper,
        requestBody -> signedEnvelope(requestBody, signatureSecret, "Signed remote answer", false)
    )) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

      AgentWorkerResponse response = worker.run(new AgentWorkerRequest(
          "run_remote_signed",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      ));

      assertThat(response.displayText()).isEqualTo("Signed remote answer");
      assertThat(server.lastRequestBody()).doesNotContain(signatureSecret);
    }

    try (RemoteWorkerServer server = RemoteWorkerServer.start(
        objectMapper,
        requestBody -> signedEnvelope(requestBody, signatureSecret, "Tampered remote answer", true)
    )) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

      assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
          "run_remote_signed_bad",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      )))
          .isInstanceOf(AgentWorkerException.class)
          .hasMessageContaining("invalid remote runner envelope signature");
    }
  }

  @Test
  void rejectsDelimiterCollisionTamperingInSignedEnvelope() throws Exception {
    String signatureSecret = "runner-secret-for-test";
    try (RemoteWorkerServer server = RemoteWorkerServer.start(
        objectMapper,
        requestBody -> delimiterCollisionEnvelope(requestBody, signatureSecret)
    )) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

      assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
          "run_remote_collision",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      )))
          .isInstanceOf(AgentWorkerException.class)
          .hasMessageContaining("invalid remote runner envelope signature");
    }
  }

  @Test
  void requiresSignedEnvelopeWhenSignatureSecretIsConfigured() throws Exception {
    String signatureSecret = "runner-secret-for-test";
    try (RemoteWorkerServer server = RemoteWorkerServer.start(objectMapper, this::successfulEnvelope)) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

      assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
          "run_remote_unsigned_with_secret",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      )))
          .isInstanceOf(AgentWorkerException.class)
          .hasMessageContaining("remote runner envelope signature is required");
    }

    try (RemoteWorkerServer server = RemoteWorkerServer.start(
        objectMapper,
        requestBody -> hmacEnvelopeWithoutSignature(requestBody)
    )) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

      assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
          "run_remote_missing_signature",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      )))
          .isInstanceOf(AgentWorkerException.class)
          .hasMessageContaining("remote runner envelope signature is required");
    }

    try (RemoteWorkerServer server = RemoteWorkerServer.start(
        objectMapper,
        requestBody -> malformedSignatureEnvelope(requestBody)
    )) {
      RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

      assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
          "run_remote_malformed_signature",
          "/server/workspaces/ws_1/content",
          "总结当前知识库",
          "deterministic-open-agent",
          false,
          false,
          null
      )))
          .isInstanceOf(AgentWorkerException.class)
          .hasMessageContaining("invalid remote runner envelope signature");
    }
  }

  @Test
  void rejectsProductionRemoteHttpWorkerWithoutSignatureSecret() {
    assertThatThrownBy(() -> new RemoteHttpAgentWorker(
        objectMapper,
        serverUrlPlaceholder(),
        2_000,
        "",
        List.of("production")
    ))
        .isInstanceOf(AgentWorkerException.class)
        .hasMessageContaining("Remote HTTP agent worker signature secret is required for production profiles");

    RemoteHttpAgentWorker localSpikeWorker = new RemoteHttpAgentWorker(
        objectMapper,
        serverUrlPlaceholder(),
        2_000,
        "",
        List.of("default")
    );
    assertThat(localSpikeWorker.workerKind()).isEqualTo("REMOTE_RUNNER");
  }

  private Map<String, Object> successfulEnvelope(String requestBody) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
      String runId = (String) request.get("runId");
      return Map.of(
          "schemaVersion", "agent-remote-runner-result.v1",
          "workerKind", "REMOTE_RUNNER",
          "signatureKind", "unsigned-local-spike",
          "result", Map.ofEntries(
              entry("schemaVersion", "agent-backend-response.v1"),
              entry("runId", runId),
              entry("status", "SUCCEEDED"),
              entry("outputKind", "answer"),
              entry("displayText", "Remote backend answer"),
              entry("requiresConfirmation", false),
              entry("requiresApproval", false),
              entry("artifactRefs", List.of(".agent-runs/%s/remote.json".formatted(runId))),
              entry("wroteWorkspace", false),
              entry("targetWorkspacePaths", List.of()),
              entry("source", Map.of("runtimePrivate", true))
          )
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to parse remote worker request", exception);
    }
  }

  private static String serverUrlPlaceholder() {
    return "http://127.0.0.1:1/run";
  }

  private Map<String, Object> signedEnvelope(
      String requestBody,
      String signatureSecret,
      String displayText,
      boolean tamperAfterSigning
  ) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
      String runId = (String) request.get("runId");
      Map<String, Object> result = backendResponse(runId, displayText);
      String signature = testSignature(signatureSecret, result);
      return Map.of(
          "schemaVersion", "agent-remote-runner-result.v1",
          "workerKind", "REMOTE_RUNNER",
          "signatureKind", "hmac-sha256",
          "signature", signature,
          "result", tamperAfterSigning ? backendResponse(runId, displayText + " modified") : result
      );
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create signed remote worker response", exception);
    }
  }

  private Map<String, Object> delimiterCollisionEnvelope(String requestBody, String signatureSecret) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
      String runId = (String) request.get("runId");
      Map<String, Object> signedResult = backendResponse(runId, "answer\nSigned remote answer", "false");
      Map<String, Object> tamperedResult = backendResponse(runId, "answer", "Signed remote answer\nfalse");
      return Map.of(
          "schemaVersion", "agent-remote-runner-result.v1",
          "workerKind", "REMOTE_RUNNER",
          "signatureKind", "hmac-sha256",
          "signature", testSignature(signatureSecret, signedResult),
          "result", tamperedResult
      );
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create delimiter collision remote worker response", exception);
    }
  }

  private Map<String, Object> hmacEnvelopeWithoutSignature(String requestBody) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
      String runId = (String) request.get("runId");
      return Map.of(
          "schemaVersion", "agent-remote-runner-result.v1",
          "workerKind", "REMOTE_RUNNER",
          "signatureKind", "hmac-sha256",
          "result", backendResponse(runId, "Unsigned remote answer")
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create unsigned remote worker response", exception);
    }
  }

  private Map<String, Object> malformedSignatureEnvelope(String requestBody) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
      String runId = (String) request.get("runId");
      return Map.of(
          "schemaVersion", "agent-remote-runner-result.v1",
          "workerKind", "REMOTE_RUNNER",
          "signatureKind", "hmac-sha256",
          "signature", "sha256:not-hmac",
          "result", backendResponse(runId, "Malformed signature remote answer")
      );
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create malformed signature remote worker response", exception);
    }
  }

  private static Map<String, Object> backendResponse(String runId, String displayText) {
    return backendResponse(runId, "answer", displayText);
  }

  private static Map<String, Object> backendResponse(String runId, String outputKind, String displayText) {
    return Map.ofEntries(
        entry("schemaVersion", "agent-backend-response.v1"),
        entry("runId", runId),
        entry("status", "SUCCEEDED"),
        entry("outputKind", outputKind),
        entry("displayText", displayText),
        entry("requiresConfirmation", false),
        entry("requiresApproval", false),
        entry("artifactRefs", List.of(".agent-runs/%s/remote.json".formatted(runId))),
        entry("wroteWorkspace", false),
        entry("targetWorkspacePaths", List.of())
    );
  }

  @SuppressWarnings("unchecked")
  private static String testSignature(String signatureSecret, Map<String, Object> result) throws Exception {
    StringBuilder payload = new StringBuilder();
    appendTestScalar(payload, "envelope.schemaVersion", "agent-remote-runner-result.v1");
    appendTestScalar(payload, "envelope.workerKind", "REMOTE_RUNNER");
    appendTestScalar(payload, "envelope.signatureKind", "hmac-sha256");
    appendTestScalar(payload, "result.schemaVersion", (String) result.get("schemaVersion"));
    appendTestScalar(payload, "result.runId", (String) result.get("runId"));
    appendTestScalar(payload, "result.status", (String) result.get("status"));
    appendTestScalar(payload, "result.outputKind", (String) result.get("outputKind"));
    appendTestScalar(payload, "result.displayText", (String) result.get("displayText"));
    appendTestBoolean(payload, "result.requiresConfirmation", (Boolean) result.get("requiresConfirmation"));
    appendTestBoolean(payload, "result.requiresApproval", (Boolean) result.get("requiresApproval"));
    appendTestList(payload, "result.artifactRefs", (List<String>) result.get("artifactRefs"));
    appendTestBoolean(payload, "result.wroteWorkspace", (Boolean) result.get("wroteWorkspace"));
    appendTestList(payload, "result.targetWorkspacePaths", (List<String>) result.get("targetWorkspacePaths"));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "hmac-sha256:"
        + Base64.getEncoder().encodeToString(mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
  }

  private static void appendTestScalar(StringBuilder payload, String name, String value) {
    int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
    payload.append(name)
        .append('#')
        .append(byteLength)
        .append(':')
        .append(value)
        .append('\n');
  }

  private static void appendTestBoolean(StringBuilder payload, String name, boolean value) {
    appendTestScalar(payload, name, Boolean.toString(value));
  }

  private static void appendTestList(StringBuilder payload, String name, List<String> values) {
    payload.append(name)
        .append("[]#")
        .append(values.size())
        .append('\n');
    for (String value : values) {
      appendTestScalar(payload, name + ".item", value);
    }
  }

  private static final class RemoteWorkerServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<String> lastRequestBody;

    private RemoteWorkerServer(HttpServer server, AtomicReference<String> lastRequestBody) {
      this.server = server;
      this.lastRequestBody = lastRequestBody;
    }

    static RemoteWorkerServer start(
        ObjectMapper objectMapper,
        Function<String, Map<String, Object>> responseFactory
    ) throws IOException {
      AtomicReference<String> lastRequestBody = new AtomicReference<>("");
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/run", (exchange) -> handle(exchange, objectMapper, responseFactory, lastRequestBody));
      server.start();
      return new RemoteWorkerServer(server, lastRequestBody);
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

    private static void handle(
        HttpExchange exchange,
        ObjectMapper objectMapper,
        Function<String, Map<String, Object>> responseFactory,
        AtomicReference<String> lastRequestBody
    ) throws IOException {
      String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      lastRequestBody.set(requestBody);
      byte[] response = objectMapper.writeValueAsBytes(responseFactory.apply(requestBody));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }
}
