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
        "my-workflow.backend.dev-principal.user-id=fallback-user",
        "my-workflow.backend.dev-principal.team-id=fallback-team",
        "my-workflow.backend.dev-principal.display-name=Fallback User"
    }
)
@AutoConfigureMockMvc
class SecurityPrincipalControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void meUsesConfiguredDevPrincipalWhenNoDevHeadersAreSupplied() throws Exception {
    mockMvc.perform(get("/v1/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("fallback-user"))
        .andExpect(jsonPath("$.data.teamId").value("fallback-team"))
        .andExpect(jsonPath("$.data.displayName").value("Fallback User"));
  }

  @Test
  void meUsesSecurityContextPrincipalFromDevHeaders() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("X-Dev-User-Id", "header-user")
            .header("X-Dev-Team-Id", "header-team")
            .header("X-Dev-Display-Name", "Header User"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("header-user"))
        .andExpect(jsonPath("$.data.teamId").value("header-team"))
        .andExpect(jsonPath("$.data.displayName").value("Header User"))
        .andExpect(jsonPath("$.data.token").doesNotExist());
  }
}
