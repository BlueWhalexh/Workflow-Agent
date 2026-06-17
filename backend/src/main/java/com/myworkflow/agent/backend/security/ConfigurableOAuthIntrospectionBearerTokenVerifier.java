package com.myworkflow.agent.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${my-workflow.backend.oauth.introspection-uri:}'.trim().isEmpty()")
public class ConfigurableOAuthIntrospectionBearerTokenVerifier implements BearerTokenVerifier {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private final BackendProperties.OAuthIntrospection introspection;
  private final Function<String, String> secretLookup;

  @Autowired
  public ConfigurableOAuthIntrospectionBearerTokenVerifier(BackendProperties properties) {
    this(properties.oauthIntrospection(), System::getenv);
  }

  ConfigurableOAuthIntrospectionBearerTokenVerifier(
      BackendProperties.OAuthIntrospection introspection,
      Function<String, String> secretLookup
  ) {
    if (!introspection.enabled()) {
      throw new IllegalArgumentException("OAuth introspection URI must be configured");
    }
    if (introspection.clientAuthConfigured()
        && blankToNull(secretLookup.apply(introspection.clientSecretEnvName())).isEmpty()) {
      throw new IllegalStateException("OAuth introspection client secret env var is not set");
    }
    this.introspection = introspection;
    this.secretLookup = secretLookup;
  }

  @Override
  public Optional<BackendPrincipal> verify(String token) {
    try {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(introspection.introspectionUri()))
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString("token=" + urlEncode(token)));
      authorizationHeader().ifPresent(value -> request.header("Authorization", value));
      HttpResponse<String> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return Optional.empty();
      }
      return principalFrom(OBJECT_MAPPER.readTree(response.body()));
    } catch (Exception exception) {
      return Optional.empty();
    }
  }

  private Optional<String> authorizationHeader() {
    if (!introspection.clientAuthConfigured()) {
      return Optional.empty();
    }
    String secret = blankToNull(secretLookup.apply(introspection.clientSecretEnvName())).orElseThrow();
    String credentials = introspection.clientId() + ":" + secret;
    return Optional.of("Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
  }

  private Optional<BackendPrincipal> principalFrom(JsonNode body) {
    if (!body.path("active").asBoolean(false)) {
      return Optional.empty();
    }
    Optional<String> userId = text(body, introspection.userIdClaim());
    Optional<String> teamId = text(body, introspection.teamIdClaim());
    Optional<String> displayName = text(body, introspection.displayNameClaim()).or(() -> userId);
    if (userId.isEmpty() || teamId.isEmpty() || displayName.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BackendPrincipal(userId.get(), teamId.get(), displayName.get()));
  }

  private static Optional<String> text(JsonNode body, String fieldName) {
    JsonNode value = body.get(fieldName);
    if (value == null || value.isNull()) {
      return Optional.empty();
    }
    String text = value.asText("").trim();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(text);
  }

  private static Optional<String> blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
