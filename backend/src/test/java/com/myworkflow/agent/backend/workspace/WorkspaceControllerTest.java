package com.myworkflow.agent.backend.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "my-workflow.backend.dev-principal.user-id=user_workspace",
        "my-workflow.backend.dev-principal.team-id=team_workspace",
        "my-workflow.backend.dev-principal.display-name=Workspace User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-workspace-controller-test"
    }
)
@AutoConfigureMockMvc
class WorkspaceControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void workspaceRegistrationListAndGetUseOpaqueWorkspaceId() throws Exception {
    MvcResult createResult = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Team Knowledge",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.workspaceId").isString())
        .andExpect(jsonPath("$.data.name").value("Team Knowledge"))
        .andExpect(jsonPath("$.data.defaultBranch").value("main"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist())
        .andReturn();

    String workspaceId = JsonPath.read(
        createResult.getResponse().getContentAsString(),
        "$.data.workspaceId"
    );

    mockMvc.perform(get("/v1/workspaces"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data[0].name").value("Team Knowledge"))
        .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data[0].serverStorageRef").doesNotExist());

    mockMvc.perform(get("/v1/workspaces/{workspaceId}", workspaceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.name").value("Team Knowledge"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist());
  }

  @Test
  void missingWorkspaceReturnsStableEnvelope() throws Exception {
    mockMvc.perform(get("/v1/workspaces/ws_missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"))
        .andExpect(jsonPath("$.error.retryable").value(false));
  }

  @Test
  void workspaceRegistrationRejectsClientSuppliedWorkspaceRoot() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Team Knowledge",
                  "workspaceRoot": "/tmp/should-not-be-used"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.error.retryable").value(false));
  }
}
