package com.myworkflow.agent.backend.identity;

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
        "my-workflow.backend.dev-principal.user-id=user_dev",
        "my-workflow.backend.dev-principal.team-id=team_dev",
        "my-workflow.backend.dev-principal.display-name=Dev User"
    }
)
@AutoConfigureMockMvc
class IdentityControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void meReturnsConfiguredDevPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.userId").value("user_dev"))
        .andExpect(jsonPath("$.data.teamId").value("team_dev"))
        .andExpect(jsonPath("$.data.displayName").value("Dev User"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void teamsReturnsOnlyCurrentPrincipalTeam() throws Exception {
    mockMvc.perform(get("/v1/teams"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].teamId").value("team_dev"))
        .andExpect(jsonPath("$.data[0].name").value("team_dev"))
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data[0].serverStorageRef").doesNotExist())
        .andExpect(jsonPath("$.data[0].token").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
  }
}
