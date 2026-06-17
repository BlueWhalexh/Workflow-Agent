package com.myworkflow.agent.backend.ops;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class OpsAuthConfigControllerTest {

  private static HttpServer discoveryServer;
  private static String issuer;
  private static String jwksUri;

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void oidcProperties(DynamicPropertyRegistry registry) {
    ensureDiscoveryServer();
    registry.add("my-workflow.backend.oidc.issuer-uri", () -> issuer);
    registry.add("my-workflow.backend.oidc.audience", () -> "my-workflow-backend");
    registry.add("my-workflow.backend.oidc.user-id-claim", () -> "email");
    registry.add("my-workflow.backend.oidc.team-id-claim", () -> "team_id");
    registry.add("my-workflow.backend.oidc.display-name-claim", () -> "name");
  }

  @AfterAll
  static void stopDiscoveryServer() {
    if (discoveryServer != null) {
      discoveryServer.stop(0);
    }
  }

  @Test
  void authConfigReturnsRedactedOidcDiscoveryMetadata() throws Exception {
    mockMvc.perform(get("/v1/ops/auth-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.mode").value("oidc-discovery"))
        .andExpect(jsonPath("$.data.discoveryEnabled").value(true))
        .andExpect(jsonPath("$.data.issuerConfigured").value(true))
        .andExpect(jsonPath("$.data.jwksUriConfigured").value(false))
        .andExpect(jsonPath("$.data.audienceConfigured").value(true))
        .andExpect(jsonPath("$.data.userIdClaim").value("email"))
        .andExpect(jsonPath("$.data.teamIdClaim").value("team_id"))
        .andExpect(jsonPath("$.data.displayNameClaim").value("name"))
        .andExpect(jsonPath("$.data.issuerUri").doesNotExist())
        .andExpect(jsonPath("$.data.jwksUri").doesNotExist())
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.data.secret").doesNotExist())
        .andExpect(jsonPath("$.data.authorization").doesNotExist())
        .andExpect(content().string(not(containsString(issuer))))
        .andExpect(content().string(not(containsString(jwksUri))))
        .andExpect(content().string(not(containsString("Authorization"))))
        .andExpect(content().string(not(containsString("token"))))
        .andExpect(content().string(not(containsString("secret"))));
  }

  private static synchronized void ensureDiscoveryServer() {
    if (discoveryServer != null) {
      return;
    }
    try {
      discoveryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      issuer = "http://127.0.0.1:%d".formatted(discoveryServer.getAddress().getPort());
      jwksUri = issuer + "/jwks";
      discoveryServer.createContext("/.well-known/openid-configuration", exchange -> {
        byte[] body = """
            {"issuer":"%s","jwks_uri":"%s"}
            """.formatted(issuer, jwksUri).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      discoveryServer.createContext("/jwks", exchange -> {
        byte[] body = """
            {"keys":[]}
            """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      discoveryServer.start();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to start local OIDC discovery fixture", exception);
    }
  }
}
