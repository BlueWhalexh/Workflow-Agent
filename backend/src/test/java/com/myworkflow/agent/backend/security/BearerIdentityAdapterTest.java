package com.myworkflow.agent.backend.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.identity.BackendPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = {
        BackendApplication.class,
        BearerIdentityAdapterTest.TestBearerVerifierConfig.class
    },
    properties = {
        "spring.profiles.active=prod",
        "my-workflow.backend.dev-principal.user-id=prod-fallback-user",
        "my-workflow.backend.dev-principal.team-id=prod-fallback-team",
        "my-workflow.backend.dev-principal.display-name=Prod Fallback"
    }
)
@AutoConfigureMockMvc
class BearerIdentityAdapterTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void prodProfileUsesVerifiedBearerPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer valid-user-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("bearer-user"))
        .andExpect(jsonPath("$.data.teamId").value("bearer-team"))
        .andExpect(jsonPath("$.data.displayName").value("Bearer User"))
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void prodProfilePrefersVerifiedBearerPrincipalOverDevHeaders() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer valid-user-token")
            .header("X-Dev-User-Id", "spoofed-user")
            .header("X-Dev-Team-Id", "spoofed-team")
            .header("X-Dev-Display-Name", "Spoofed User"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("bearer-user"))
        .andExpect(jsonPath("$.data.teamId").value("bearer-team"))
        .andExpect(jsonPath("$.data.displayName").value("Bearer User"))
        .andExpect(jsonPath("$.data.token").doesNotExist());
  }

  @Test
  void prodProfileRejectsInvalidBearerWithoutEchoingToken() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer invalid-user-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.error.message").value("Authentication is required"))
        .andExpect(jsonPath("$.error.message").value(not(containsString("invalid-user-token"))));
  }

  @TestConfiguration
  static class TestBearerVerifierConfig {

    @Bean
    BearerTokenVerifier testBearerTokenVerifier() {
      return (token) -> {
        if ("valid-user-token".equals(token)) {
          return Optional.of(new BackendPrincipal("bearer-user", "bearer-team", "Bearer User"));
        }
        return Optional.empty();
      };
    }
  }
}
