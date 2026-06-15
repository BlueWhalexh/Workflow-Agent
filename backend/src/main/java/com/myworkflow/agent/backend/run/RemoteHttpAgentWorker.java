package com.myworkflow.agent.backend.run;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "my-workflow.backend.agent-worker.kind", havingValue = "remote-http")
public class RemoteHttpAgentWorker implements AgentWorker {

  static final String REMOTE_ENVELOPE_SCHEMA_VERSION = "agent-remote-runner-result.v1";
  static final String REMOTE_WORKER_KIND = "REMOTE_RUNNER";
  static final String LOCAL_SPIKE_SIGNATURE_KIND = "unsigned-local-spike";
  static final String HMAC_SHA256_SIGNATURE_KIND = "hmac-sha256";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI endpointUri;
  private final Duration timeout;
  private final String signatureSecret;

  @Autowired
  public RemoteHttpAgentWorker(
      ObjectMapper objectMapper,
      @Value("${my-workflow.backend.agent-worker.remote-http.endpoint-url:}") String endpointUrl,
      @Value("${my-workflow.backend.agent-worker.remote-http.timeout-ms:60000}") long timeoutMs,
      @Value("${my-workflow.backend.agent-worker.remote-http.signature-secret:}") String signatureSecret,
      Environment environment
  ) {
    this(objectMapper, HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .build(), endpointUrl, timeoutMs, signatureSecret, Arrays.asList(environment.getActiveProfiles()));
  }

  RemoteHttpAgentWorker(ObjectMapper objectMapper, String endpointUrl, long timeoutMs) {
    this(objectMapper, endpointUrl, timeoutMs, null);
  }

  RemoteHttpAgentWorker(
      ObjectMapper objectMapper,
      String endpointUrl,
      long timeoutMs,
      String signatureSecret
  ) {
    this(objectMapper, endpointUrl, timeoutMs, signatureSecret, List.of());
  }

  RemoteHttpAgentWorker(
      ObjectMapper objectMapper,
      String endpointUrl,
      long timeoutMs,
      String signatureSecret,
      List<String> activeProfiles
  ) {
    this(objectMapper, HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMs))
        .build(), endpointUrl, timeoutMs, signatureSecret, activeProfiles);
  }

  private RemoteHttpAgentWorker(
      ObjectMapper objectMapper,
      HttpClient httpClient,
      String endpointUrl,
      long timeoutMs,
      String signatureSecret,
      List<String> activeProfiles
  ) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.endpointUri = parseEndpoint(endpointUrl);
    this.timeout = Duration.ofMillis(timeoutMs);
    requireProductionSignatureSecret(signatureSecret, activeProfiles);
    this.signatureSecret = normalizeSignatureSecret(signatureSecret);
  }

  @Override
  public String workerKind() {
    return REMOTE_WORKER_KIND;
  }

  @Override
  public AgentWorkerResponse run(AgentWorkerRequest request) {
    try {
      HttpRequest httpRequest = HttpRequest.newBuilder(endpointUri)
          .timeout(timeout)
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
          .build();
      HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
        throw new AgentWorkerException("Remote agent worker failed with HTTP status " + httpResponse.statusCode());
      }
      RemoteRunnerResultEnvelope envelope = objectMapper.readValue(
          httpResponse.body(),
          RemoteRunnerResultEnvelope.class
      );
      validateEnvelope(request, envelope);
      return envelope.result();
    } catch (AgentWorkerException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AgentWorkerException("Remote agent worker request was interrupted", exception);
    } catch (IOException exception) {
      throw new AgentWorkerException("Unable to call remote agent worker", exception);
    }
  }

  private void validateEnvelope(AgentWorkerRequest request, RemoteRunnerResultEnvelope envelope) {
    if (envelope == null || !REMOTE_ENVELOPE_SCHEMA_VERSION.equals(envelope.schemaVersion())) {
      throw new AgentWorkerException("unsupported remote runner envelope schema");
    }
    if (!REMOTE_WORKER_KIND.equals(envelope.workerKind())) {
      throw new AgentWorkerException("unsupported remote runner envelope worker kind");
    }
    AgentWorkerResponse result = envelope.result();
    if (result == null) {
      throw new AgentWorkerException("Remote runner envelope did not include a backend response");
    }
    if (!"agent-backend-response.v1".equals(result.schemaVersion())) {
      throw new AgentWorkerException("Remote runner returned unsupported backend response schema");
    }
    if (!request.runId().equals(result.runId())) {
      throw new AgentWorkerException("Remote runner returned mismatched run id");
    }
    validateEnvelopeSignature(envelope);
  }

  private void validateEnvelopeSignature(RemoteRunnerResultEnvelope envelope) {
    if (signatureSecret == null) {
      if (!LOCAL_SPIKE_SIGNATURE_KIND.equals(envelope.signatureKind())) {
        throw new AgentWorkerException("unsupported remote runner envelope signature kind");
      }
      return;
    }
    if (!HMAC_SHA256_SIGNATURE_KIND.equals(envelope.signatureKind())) {
      throw new AgentWorkerException("remote runner envelope signature is required");
    }
    String receivedSignature = envelope.signature();
    if (receivedSignature == null || receivedSignature.isBlank()) {
      throw new AgentWorkerException("remote runner envelope signature is required");
    }
    String expectedSignature = hmacSha256Signature(envelope, signatureSecret);
    if (!constantTimeEquals(expectedSignature, receivedSignature.trim())) {
      throw new AgentWorkerException("invalid remote runner envelope signature");
    }
  }

  private static String hmacSha256Signature(RemoteRunnerResultEnvelope envelope, String signatureSecret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] signature = mac.doFinal(canonicalSignaturePayload(envelope).getBytes(StandardCharsets.UTF_8));
      return HMAC_SHA256_SIGNATURE_KIND + ":" + Base64.getEncoder().encodeToString(signature);
    } catch (GeneralSecurityException exception) {
      throw new AgentWorkerException("Unable to verify remote runner envelope signature", exception);
    }
  }

  private static String canonicalSignaturePayload(RemoteRunnerResultEnvelope envelope) {
    AgentWorkerResponse result = envelope.result();
    StringBuilder payload = new StringBuilder();
    appendScalar(payload, "envelope.schemaVersion", envelope.schemaVersion());
    appendScalar(payload, "envelope.workerKind", envelope.workerKind());
    appendScalar(payload, "envelope.signatureKind", envelope.signatureKind());
    appendScalar(payload, "result.schemaVersion", result.schemaVersion());
    appendScalar(payload, "result.runId", result.runId());
    appendScalar(payload, "result.status", result.status());
    appendScalar(payload, "result.outputKind", result.outputKind());
    appendScalar(payload, "result.displayText", result.displayText());
    appendBoolean(payload, "result.requiresConfirmation", result.requiresConfirmation());
    appendBoolean(payload, "result.requiresApproval", result.requiresApproval());
    appendList(payload, "result.artifactRefs", result.artifactRefs());
    appendBoolean(payload, "result.wroteWorkspace", result.wroteWorkspace());
    appendList(payload, "result.targetWorkspacePaths", result.targetWorkspacePaths());
    return payload.toString();
  }

  private static List<String> emptyIfNull(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static void appendScalar(StringBuilder payload, String name, String value) {
    String normalized = nullToEmpty(value);
    int byteLength = normalized.getBytes(StandardCharsets.UTF_8).length;
    payload.append(name)
        .append('#')
        .append(byteLength)
        .append(':')
        .append(normalized)
        .append('\n');
  }

  private static void appendBoolean(StringBuilder payload, String name, boolean value) {
    appendScalar(payload, name, Boolean.toString(value));
  }

  private static void appendList(StringBuilder payload, String name, List<String> values) {
    List<String> normalized = emptyIfNull(values);
    payload.append(name)
        .append("[]#")
        .append(normalized.size())
        .append('\n');
    for (String value : normalized) {
      appendScalar(payload, name + ".item", value);
    }
  }

  private static boolean constantTimeEquals(String expected, String received) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        received.getBytes(StandardCharsets.UTF_8)
    );
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String normalizeSignatureSecret(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static void requireProductionSignatureSecret(String signatureSecret, List<String> activeProfiles) {
    boolean productionProfile = emptyIfNull(activeProfiles).stream()
        .filter((profile) -> profile != null)
        .map(String::trim)
        .map(String::toLowerCase)
        .anyMatch((profile) -> profile.equals("prod") || profile.equals("production"));
    if (productionProfile && (signatureSecret == null || signatureSecret.isBlank())) {
      throw new AgentWorkerException("Remote HTTP agent worker signature secret is required for production profiles");
    }
  }

  private static URI parseEndpoint(String endpointUrl) {
    if (endpointUrl == null || endpointUrl.isBlank()) {
      throw new AgentWorkerException("Remote agent worker endpoint URL is required");
    }
    return URI.create(endpointUrl.trim());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record RemoteRunnerResultEnvelope(
      String schemaVersion,
      String workerKind,
      String signatureKind,
      String signature,
      AgentWorkerResponse result
  ) {
  }
}
