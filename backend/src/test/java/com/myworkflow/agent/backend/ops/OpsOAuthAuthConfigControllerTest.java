package com.myworkflow.agent.backend.ops;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        "my-workflow.backend.oauth.introspection-uri=http://127.0.0.1:65535/introspect",
        "my-workflow.backend.oauth.user-id-claim=email",
        "my-workflow.backend.oauth.team-id-claim=tenant_id",
        "my-workflow.backend.oauth.display-name-claim=display_name"
    }
)
@AutoConfigureMockMvc
class OpsOAuthAuthConfigControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void authConfigReturnsRedactedOAuthIntrospectionMetadata() throws Exception {
    mockMvc.perform(get("/v1/ops/auth-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.mode").value("oauth-introspection"))
        .andExpect(jsonPath("$.data.discoveryEnabled").value(false))
        .andExpect(jsonPath("$.data.issuerConfigured").value(false))
        .andExpect(jsonPath("$.data.jwksUriConfigured").value(false))
        .andExpect(jsonPath("$.data.audienceConfigured").value(false))
        .andExpect(jsonPath("$.data.oauthIntrospectionConfigured").value(true))
        .andExpect(jsonPath("$.data.oauthClientAuthConfigured").value(false))
        .andExpect(jsonPath("$.data.userIdClaim").value("email"))
        .andExpect(jsonPath("$.data.teamIdClaim").value("tenant_id"))
        .andExpect(jsonPath("$.data.displayNameClaim").value("display_name"))
        .andExpect(jsonPath("$.data.introspectionUri").doesNotExist())
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.data.secret").doesNotExist())
        .andExpect(jsonPath("$.data.authorization").doesNotExist())
        .andExpect(content().string(not(containsString("127.0.0.1:65535"))))
        .andExpect(content().string(not(containsString("Authorization"))))
        .andExpect(content().string(not(containsString("token"))))
        .andExpect(content().string(not(containsString("secret"))));
  }
}
