package com.myworkflow.agent.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
        "my-workflow.backend.cors.allowed-origins=https://app.example.test"
    }
)
@AutoConfigureMockMvc
class CorsCredentialPolicyTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void allowedOriginPreflightAllowsCredentialsForFrontendCookieAuth() throws Exception {
    mockMvc.perform(options("/v1/me")
            .header("Origin", "https://app.example.test")
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Content-Type, Last-Event-ID"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  void allowedOriginPreflightAllowsDeleteForWorkspaceMemberManagement() throws Exception {
    mockMvc.perform(options("/v1/workspaces/ws_123/members/user_456")
            .header("Origin", "https://app.example.test")
            .header("Access-Control-Request-Method", "DELETE")
            .header("Access-Control-Request-Headers", "Content-Type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
  }

  @Test
  void unlistedOriginPreflightDoesNotReceiveCredentialedCorsAccess() throws Exception {
    mockMvc.perform(options("/v1/me")
            .header("Origin", "https://evil.example.test")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }
}

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=prod"
    }
)
@AutoConfigureMockMvc
class DefaultCorsCredentialPolicyTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void defaultCorsConfigDoesNotExposeCredentialedCrossOriginAccess() throws Exception {
    mockMvc.perform(options("/v1/me")
            .header("Origin", "https://app.example.test")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
  }
}
