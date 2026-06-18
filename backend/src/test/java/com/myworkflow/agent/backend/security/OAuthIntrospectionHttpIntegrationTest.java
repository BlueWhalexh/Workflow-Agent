package com.myworkflow.agent.backend.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
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
import org.springframework.http.MediaType;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=prod",
        "my-workflow.backend.dev-principal.user-id=prod-fallback-user",
        "my-workflow.backend.dev-principal.team-id=prod-fallback-team",
        "my-workflow.backend.dev-principal.display-name=Prod Fallback",
        "my-workflow.backend.oauth.session-cookie-name=MWA_SESSION"
    }
)
@AutoConfigureMockMvc
class OAuthIntrospectionHttpIntegrationTest {

  private static HttpServer introspectionServer;
  private static String introspectionUri;

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void oauthProperties(DynamicPropertyRegistry registry) {
    ensureIntrospectionServer();
    registry.add("my-workflow.backend.oauth.introspection-uri", () -> introspectionUri);
  }

  @AfterAll
  static void stopIntrospectionServer() {
    if (introspectionServer != null) {
      introspectionServer.stop(0);
    }
  }

  @Test
  void prodProfileUsesOAuthIntrospectionVerifierForBearerPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer active-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("oauth-user"))
        .andExpect(jsonPath("$.data.teamId").value("team-oauth"))
        .andExpect(jsonPath("$.data.displayName").value("OAuth User"))
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void prodProfileUsesConfiguredOAuthSessionCookieForBrowserPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me")
            .cookie(new Cookie("MWA_SESSION", "active-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("oauth-user"))
        .andExpect(jsonPath("$.data.teamId").value("team-oauth"))
        .andExpect(jsonPath("$.data.displayName").value("OAuth User"))
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void prodProfileRejectsMutatingSessionCookieRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .cookie(new Cookie("MWA_SESSION", "active-token"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Cookie Workspace","defaultBranch":"main"}
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_REQUIRED"))
        .andExpect(content().string(not(containsString("active-token"))));
  }

  @Test
  void prodProfileAllowsMutatingSessionCookieRequestWithMatchingCsrfToken() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .cookie(new Cookie("MWA_SESSION", "active-token"))
            .cookie(new Cookie("MWA_CSRF", "csrf-token"))
            .header("X-CSRF-Token", "csrf-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Cookie Workspace","defaultBranch":"main"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Cookie Workspace"))
        .andExpect(jsonPath("$.data.defaultBranch").value("main"))
        .andExpect(content().string(not(containsString("active-token"))))
        .andExpect(content().string(not(containsString("csrf-token"))));
  }

  @Test
  void prodProfileRejectsMutatingSessionCookieRequestWithMismatchedCsrfToken() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .cookie(new Cookie("MWA_SESSION", "active-token"))
            .cookie(new Cookie("MWA_CSRF", "cookie-token"))
            .header("X-CSRF-Token", "header-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Cookie Workspace","defaultBranch":"main"}
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_REQUIRED"))
        .andExpect(content().string(not(containsString("active-token"))))
        .andExpect(content().string(not(containsString("cookie-token"))))
        .andExpect(content().string(not(containsString("header-token"))));
  }

  @Test
  void prodProfileAllowsMutatingBearerRequestWithoutCsrfToken() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .header("Authorization", "Bearer active-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Bearer Workspace","defaultBranch":"main"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Bearer Workspace"))
        .andExpect(jsonPath("$.data.defaultBranch").value("main"))
        .andExpect(content().string(not(containsString("active-token"))));
  }

  @Test
  void prodProfileRejectsInactiveOAuthIntrospectionTokenWithoutEchoingToken() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer inactive-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.error.message").value(not(containsString("inactive-token"))));
  }

  private static synchronized void ensureIntrospectionServer() {
    if (introspectionServer != null) {
      return;
    }
    try {
      introspectionServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      introspectionUri = "http://127.0.0.1:%d/introspect".formatted(introspectionServer.getAddress().getPort());
      introspectionServer.createContext("/introspect", exchange -> {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String response = switch (requestBody) {
          case "token=active-token" -> """
              {"active":true,"sub":"oauth-user","team_id":"team-oauth","name":"OAuth User"}
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
      introspectionServer.start();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to start local OAuth introspection fixture", exception);
    }
  }
}
