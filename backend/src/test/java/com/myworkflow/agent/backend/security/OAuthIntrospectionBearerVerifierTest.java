package com.myworkflow.agent.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.myworkflow.agent.backend.config.BackendProperties;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuthIntrospectionBearerVerifierTest {

  private HttpServer introspectionServer;
  private String introspectionUri;
  private String lastRequestBody;
  private String lastAuthorization;

  @BeforeEach
  void startServer() throws Exception {
    introspectionServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    introspectionUri = "http://127.0.0.1:%d/introspect".formatted(introspectionServer.getAddress().getPort());
    introspectionServer.createContext("/introspect", exchange -> {
      lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
      String response = switch (lastRequestBody) {
        case "token=active-token" -> """
            {"active":true,"sub":"oauth-user","team_id":"team-oauth","name":"OAuth User"}
            """;
        case "token=custom-claims-token" -> """
            {"active":true,"email":"oauth@example.test","tenant_id":"tenant-oauth","display_name":"OAuth Custom"}
            """;
        case "token=inactive-token" -> """
            {"active":false,"sub":"oauth-user","team_id":"team-oauth","name":"OAuth User"}
            """;
        case "token=malformed-token" -> """
            {"active":true,"sub":"oauth-user","team_id":"team-oauth"}
            """;
        default -> """
            {"active":false}
            """;
      };
      byte[] body = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    introspectionServer.createContext("/fail", exchange -> {
      byte[] body = "server unavailable".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(503, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    introspectionServer.start();
  }

  @AfterEach
  void stopServer() {
    if (introspectionServer != null) {
      introspectionServer.stop(0);
    }
  }

  @Test
  void activeTokenMapsConfiguredResponseFieldsToPrincipal() {
    ConfigurableOAuthIntrospectionBearerTokenVerifier verifier =
        new ConfigurableOAuthIntrospectionBearerTokenVerifier(
            introspectionProperties(null, null, "sub", "team_id", "name"),
            ignored -> null
        );

    Optional<BackendPrincipal> principal = verifier.verify("active-token");

    assertThat(lastRequestBody).isEqualTo("token=active-token");
    assertThat(lastAuthorization).isNull();
    assertThat(principal).hasValue(new BackendPrincipal("oauth-user", "team-oauth", "OAuth User"));
  }

  @Test
  void customClaimMappingIsSupported() {
    ConfigurableOAuthIntrospectionBearerTokenVerifier verifier =
        new ConfigurableOAuthIntrospectionBearerTokenVerifier(
            introspectionProperties(null, null, "email", "tenant_id", "display_name"),
            ignored -> null
        );

    Optional<BackendPrincipal> principal = verifier.verify("custom-claims-token");

    assertThat(principal).hasValue(new BackendPrincipal("oauth@example.test", "tenant-oauth", "OAuth Custom"));
  }

  @Test
  void inactiveTokenIsRejected() {
    ConfigurableOAuthIntrospectionBearerTokenVerifier verifier =
        new ConfigurableOAuthIntrospectionBearerTokenVerifier(
            introspectionProperties(null, null, "sub", "team_id", "name"),
            ignored -> null
        );

    assertThat(verifier.verify("inactive-token")).isEmpty();
  }

  @Test
  void missingRequiredDisplayNameFallsBackToUserId() {
    ConfigurableOAuthIntrospectionBearerTokenVerifier verifier =
        new ConfigurableOAuthIntrospectionBearerTokenVerifier(
            introspectionProperties(null, null, "sub", "team_id", "name"),
            ignored -> null
        );

    Optional<BackendPrincipal> principal = verifier.verify("malformed-token");

    assertThat(principal).hasValue(new BackendPrincipal("oauth-user", "team-oauth", "oauth-user"));
  }

  @Test
  void nonSuccessIntrospectionResponseIsRejected() {
    ConfigurableOAuthIntrospectionBearerTokenVerifier verifier =
        new ConfigurableOAuthIntrospectionBearerTokenVerifier(
            new BackendProperties.OAuthIntrospection(
                introspectionUri.replace("/introspect", "/fail"),
                null,
                null,
                "sub",
                "team_id",
                "name"
            ),
            ignored -> null
        );

    assertThat(verifier.verify("active-token")).isEmpty();
  }

  @Test
  void clientAuthUsesSecretFromEnvironmentLookup() {
    ConfigurableOAuthIntrospectionBearerTokenVerifier verifier =
        new ConfigurableOAuthIntrospectionBearerTokenVerifier(
            introspectionProperties("backend-client", "OAUTH_CLIENT_SECRET", "sub", "team_id", "name"),
            name -> "OAUTH_CLIENT_SECRET".equals(name) ? "client-secret" : null
        );

    Optional<BackendPrincipal> principal = verifier.verify("active-token");

    String expected = "Basic " + Base64.getEncoder()
        .encodeToString("backend-client:client-secret".getBytes(StandardCharsets.UTF_8));
    assertThat(lastAuthorization).isEqualTo(expected);
    assertThat(principal).hasValue(new BackendPrincipal("oauth-user", "team-oauth", "OAuth User"));
  }

  @Test
  void configuredClientAuthWithoutEnvironmentSecretIsRejected() {
    BackendProperties.OAuthIntrospection properties =
        introspectionProperties("backend-client", "OAUTH_CLIENT_SECRET", "sub", "team_id", "name");

    assertThatThrownBy(() -> new ConfigurableOAuthIntrospectionBearerTokenVerifier(properties, ignored -> null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void oauthIntrospectionAndOidcConfigurationConflictIsRejected() {
    assertThatThrownBy(() -> new BackendProperties(
        "/tmp/my-workflow-agent-test",
        "dev-user",
        "dev-team",
        "Dev User",
        "",
        365,
        "https://issuer.example.test",
        "",
        "",
        "",
        "sub",
        "team_id",
        "name",
        introspectionUri,
        "",
        "",
        "sub",
        "team_id",
        "name"
    )).isInstanceOf(IllegalArgumentException.class);
  }

  private BackendProperties.OAuthIntrospection introspectionProperties(
      String clientId,
      String clientSecretEnvName,
      String userIdClaim,
      String teamIdClaim,
      String displayNameClaim
  ) {
    return new BackendProperties.OAuthIntrospection(
        introspectionUri,
        clientId,
        clientSecretEnvName,
        userIdClaim,
        teamIdClaim,
        displayNameClaim
    );
  }
}
