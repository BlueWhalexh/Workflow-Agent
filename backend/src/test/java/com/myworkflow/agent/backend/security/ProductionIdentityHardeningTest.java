package com.myworkflow.agent.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=prod",
        "my-workflow.backend.dev-principal.user-id=prod-fallback-user",
        "my-workflow.backend.dev-principal.team-id=prod-fallback-team",
        "my-workflow.backend.dev-principal.display-name=Prod Fallback"
    }
)
@AutoConfigureMockMvc
class ProductionIdentityHardeningTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void prodProfileRejectsDevHeadersInsteadOfTrustingSpoofedPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("X-Dev-User-Id", "spoofed-user")
            .header("X-Dev-Team-Id", "spoofed-team")
            .header("X-Dev-Display-Name", "Spoofed User"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }

  @Test
  void prodProfileDoesNotFallBackToConfiguredDevPrincipalWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }
}
