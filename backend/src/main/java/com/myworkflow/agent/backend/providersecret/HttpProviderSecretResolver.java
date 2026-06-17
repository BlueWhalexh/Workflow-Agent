package com.myworkflow.agent.backend.providersecret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.config.BackendProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${my-workflow.backend.provider-secrets.http-resolver-uri:}'.trim().isEmpty()")
public class HttpProviderSecretResolver implements ProviderSecretResolver {

  private static final String SECRET_REF_PREFIX = "secret://";
  private static final String REQUEST_SCHEMA_VERSION = "provider-secret-resolve-request.v1";
  private static final String RESPONSE_SCHEMA_VERSION = "provider-secret-resolve-response.v1";
  private static final int MAX_RESPONSE_CHARS = 32 * 1024;
  private static final int MAX_SECRET_VALUE_CHARS = 16 * 1024;

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final BackendProperties.HttpSecretManager secretManager;
  private final Function<String, String> secretLookup;

  @Autowired
  public HttpProviderSecretResolver(BackendProperties properties) {
    this(properties.httpSecretManager(), System::getenv);
  }

  HttpProviderSecretResolver(
      BackendProperties.HttpSecretManager secretManager,
      Function<String, String> secretLookup
  ) {
    this(secretManager, secretLookup, new ObjectMapper(), HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(secretManager.timeoutMs()))
        .build());
  }

  HttpProviderSecretResolver(
      BackendProperties.HttpSecretManager secretManager,
      Function<String, String> secretLookup,
      ObjectMapper objectMapper,
      HttpClient httpClient
  ) {
    if (!secretManager.enabled()) {
      throw new IllegalArgumentException("Provider secret HTTP resolver URI must be configured");
    }
    if (secretManager.authConfigured()
        && blankToNull(secretLookup.apply(secretManager.authTokenEnvName())).isEmpty()) {
      throw new IllegalStateException("Provider secret HTTP auth token env var is not set");
    }
    this.secretManager = secretManager;
    this.secretLookup = secretLookup;
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Override
  public Optional<String> resolveSecretValue(String secretRef) {
    if (secretRef == null || !secretRef.startsWith(SECRET_REF_PREFIX) || secretRef.isBlank()) {
      return Optional.empty();
    }
    try {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(secretManager.resolverUri()))
          .timeout(Duration.ofMillis(secretManager.timeoutMs()))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody(secretRef)));
      authorizationHeader().ifPresent(value -> request.header("Authorization", value));
      HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return Optional.empty();
      }
      String body = response.body();
      if (body == null || body.length() > MAX_RESPONSE_CHARS) {
        return Optional.empty();
      }
      return secretValueFrom(body);
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private String requestBody(String secretRef) throws Exception {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("schemaVersion", REQUEST_SCHEMA_VERSION);
    body.put("secretRef", secretRef);
    return objectMapper.writeValueAsString(body);
  }

  private Optional<String> authorizationHeader() {
    if (!secretManager.authConfigured()) {
      return Optional.empty();
    }
    String secret = blankToNull(secretLookup.apply(secretManager.authTokenEnvName())).orElseThrow();
    return Optional.of("Bearer " + secret);
  }

  private Optional<String> secretValueFrom(String body) throws Exception {
    JsonNode json = objectMapper.readTree(body);
    if (!RESPONSE_SCHEMA_VERSION.equals(text(json, "schemaVersion").orElse(null))) {
      return Optional.empty();
    }
    Optional<String> secretValue = text(json, "secretValue");
    if (secretValue.isEmpty() || secretValue.get().length() > MAX_SECRET_VALUE_CHARS) {
      return Optional.empty();
    }
    return secretValue;
  }

  private static Optional<String> text(JsonNode body, String fieldName) {
    JsonNode value = body.get(fieldName);
    if (value == null || value.isNull()) {
      return Optional.empty();
    }
    return blankToNull(value.asText(""));
  }

  private static Optional<String> blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }
}
