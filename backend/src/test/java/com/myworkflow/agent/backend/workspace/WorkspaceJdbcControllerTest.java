package com.myworkflow.agent.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=user_jdbc",
        "my-workflow.backend.dev-principal.team-id=team_jdbc",
        "my-workflow.backend.dev-principal.display-name=JDBC User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-jdbc-controller-test"
    }
)
@AutoConfigureMockMvc
class WorkspaceJdbcControllerTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_agent_http_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void workspaceApiPersistsThroughJdbcRepository() throws Exception {
    MvcResult createResult = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "JDBC Knowledge",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceId").isString())
        .andExpect(jsonPath("$.data.name").value("JDBC Knowledge"))
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist())
        .andReturn();

    String workspaceId = JsonPath.read(
        createResult.getResponse().getContentAsString(),
        "$.data.workspaceId"
    );

    MvcResult listResult = mockMvc.perform(get("/v1/workspaces"))
        .andExpect(status().isOk())
        .andReturn();
    List<String> workspaceIds = JsonPath.read(listResult.getResponse().getContentAsString(), "$.data[*].workspaceId");
    assertThat(workspaceIds).contains(workspaceId);

    mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Rejected",
                  "workspaceRoot": "/tmp/should-not-be-used"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
  }

  @Test
  void teamDirectoryLifecycleApiPersistsThroughJdbcRepository() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "JDBC Team Directory",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk());

    mockMvc.perform(put("/v1/teams/{teamId}/members/{userId}", "team_jdbc", "editor_jdbc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "JDBC Editor",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.displayName").value("JDBC Editor"))
        .andExpect(jsonPath("$.data.role").value("TEAM_MEMBER"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    mockMvc.perform(post("/v1/teams/{teamId}/members/{userId}/disable", "team_jdbc", "editor_jdbc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISABLED"));
  }

  @Test
  void teamInviteApiPersistsThroughJdbcRepository() throws Exception {
    mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "JDBC Team Invite",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk());

    MvcResult createInviteResult = mockMvc.perform(post("/v1/teams/{teamId}/invites", "team_jdbc")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteeUserId": "invitee_jdbc",
                  "displayName": "JDBC Invitee",
                  "role": "TEAM_MEMBER"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.teamId").value("team_jdbc"))
        .andExpect(jsonPath("$.data.inviteeUserId").value("invitee_jdbc"))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.data.secret").doesNotExist())
        .andReturn();

    String inviteId = JsonPath.read(createInviteResult.getResponse().getContentAsString(), "$.data.id");

    mockMvc.perform(get("/v1/teams/{teamId}/invites", "team_jdbc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(inviteId));

    mockMvc.perform(post("/v1/teams/{teamId}/invites/{inviteId}/accept", "team_jdbc", inviteId)
            .header("X-Dev-User-Id", "invitee_jdbc")
            .header("X-Dev-Team-Id", "team_jdbc")
            .header("X-Dev-Display-Name", "JDBC Invitee"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

    mockMvc.perform(get("/v1/teams/{teamId}/members", "team_jdbc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.userId == 'invitee_jdbc')].status").value("ACTIVE"));
  }
}
