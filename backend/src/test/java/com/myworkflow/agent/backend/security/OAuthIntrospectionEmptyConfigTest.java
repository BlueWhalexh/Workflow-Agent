package com.myworkflow.agent.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=prod",
        "my-workflow.backend.oauth.introspection-uri=",
        "my-workflow.backend.dev-principal.user-id=prod-fallback-user",
        "my-workflow.backend.dev-principal.team-id=prod-fallback-team",
        "my-workflow.backend.dev-principal.display-name=Prod Fallback"
    }
)
@AutoConfigureMockMvc
class OAuthIntrospectionEmptyConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired(required = false)
  private ConfigurableOAuthIntrospectionBearerTokenVerifier verifier;

  @Value("${my-workflow.backend.oauth.introspection-uri:missing}")
  private String configuredIntrospectionUri;

  @Test
  void blankIntrospectionUriIsTreatedAsDisabled() throws Exception {
    mockMvc.perform(get("/v1/ops/auth-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mode").value("disabled"))
        .andExpect(jsonPath("$.data.oauthIntrospectionConfigured").value(false));

    org.assertj.core.api.Assertions.assertThat(configuredIntrospectionUri).isEmpty();
    org.assertj.core.api.Assertions.assertThat(verifier).isNull();
  }
}
