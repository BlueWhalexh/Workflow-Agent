package com.myworkflow.agent.backend.providersecret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpProviderSecretResolverTest {

  private final AtomicReference<String> lastRequestBody = new AtomicReference<>("");
  private final AtomicReference<String> lastAuthorization = new AtomicReference<>("");
  private final AtomicInteger statusCode = new AtomicInteger(200);
  private final AtomicReference<String> responseBody = new AtomicReference<>("""
      {
        "schemaVersion": "provider-secret-resolve-response.v1",
        "secretValue": "http-secret-manager-value-not-real"
      }
      """);

  private HttpServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/resolve", this::handleResolve);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void resolvesSecretRefThroughConfiguredHttpEndpointWithEnvBackedBearerAuth() {
    HttpProviderSecretResolver resolver = new HttpProviderSecretResolver(
        new BackendProperties.HttpSecretManager(
            secretManagerUrl(),
            "HTTP_SECRET_MANAGER_TOKEN",
            5_000
        ),
        name -> "HTTP_SECRET_MANAGER_TOKEN".equals(name) ? "manager-auth-token-not-real" : null
    );

    assertThat(resolver.resolveSecretValue("secret://team-a/provider/mimo"))
        .contains("http-secret-manager-value-not-real");
    assertThat(lastAuthorization.get()).isEqualTo("Bearer manager-auth-token-not-real");
    assertThat(lastRequestBody.get())
        .contains("\"schemaVersion\":\"provider-secret-resolve-request.v1\"")
        .contains("\"secretRef\":\"secret://team-a/provider/mimo\"")
        .doesNotContain("http-secret-manager-value-not-real", "manager-auth-token-not-real");
  }

  @Test
  void ignoresNonSecretRefs() {
    HttpProviderSecretResolver resolver = new HttpProviderSecretResolver(
        new BackendProperties.HttpSecretManager(secretManagerUrl(), "", 5_000),
        ignored -> null
    );

    assertThat(resolver.resolveSecretValue("file://mimo/api-key.txt")).isEmpty();
    assertThat(lastRequestBody.get()).isEmpty();
  }

  @Test
  void configuredAuthWithoutEnvironmentSecretIsRejected() {
    BackendProperties.HttpSecretManager secretManager = new BackendProperties.HttpSecretManager(
        secretManagerUrl(),
        "HTTP_SECRET_MANAGER_TOKEN",
        5_000
    );

    assertThatThrownBy(() -> new HttpProviderSecretResolver(secretManager, ignored -> null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void returnsEmptyForInvalidResponsesWithoutThrowing() {
    HttpProviderSecretResolver resolver = new HttpProviderSecretResolver(
        new BackendProperties.HttpSecretManager(secretManagerUrl(), "", 5_000),
        ignored -> null
    );

    statusCode.set(500);
    assertThat(resolver.resolveSecretValue("secret://team-a/provider/mimo")).isEmpty();

    statusCode.set(200);
    responseBody.set("""
        {
          "schemaVersion": "provider-secret-resolve-response.v1",
          "secretValue": "   "
        }
        """);
    assertThat(resolver.resolveSecretValue("secret://team-a/provider/mimo")).isEmpty();

    responseBody.set("{\"schemaVersion\":\"wrong\",\"secretValue\":\"hidden\"}");
    assertThat(resolver.resolveSecretValue("secret://team-a/provider/mimo")).isEmpty();
  }

  private String secretManagerUrl() {
    return "http://127.0.0.1:%d/resolve".formatted(server.getAddress().getPort());
  }

  private void handleResolve(HttpExchange exchange) throws IOException {
    lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
    lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(statusCode.get(), response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }
}
