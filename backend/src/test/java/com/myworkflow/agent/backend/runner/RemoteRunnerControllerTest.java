package com.myworkflow.agent.backend.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.audit.AuditEventRecord;
import com.myworkflow.agent.backend.audit.AuditRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
        "my-workflow.backend.dev-principal.user-id=owner-user",
        "my-workflow.backend.dev-principal.team-id=team-remote-runner-api",
        "my-workflow.backend.dev-principal.display-name=Owner User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-remote-runner-api-test"
    }
)
@AutoConfigureMockMvc
class RemoteRunnerControllerTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_remote_runner_api_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AuditRepository auditRepository;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void ownerRegistersHeartbeatsAndLeasesWorkspaceRemoteRunnerWithoutSecretMaterial() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");

    MvcResult registerResult = mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "本地远程执行器",
                  "endpointUrl": "http://127.0.0.1:19090/run",
                  "capabilities": ["agent-backend-response.v1", "workspace-read"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.runnerRef").value("runner-local-1"))
        .andExpect(jsonPath("$.data.displayName").value("本地远程执行器"))
        .andExpect(jsonPath("$.data.endpointUrl").value("http://127.0.0.1:19090/run"))
        .andExpect(jsonPath("$.data.status").value("REGISTERED"))
        .andExpect(jsonPath("$.data.capabilities[0]").value("agent-backend-response.v1"))
        .andExpect(jsonPath("$.data.runnerToken").doesNotExist())
        .andExpect(jsonPath("$.data.signatureSecret").doesNotExist())
        .andReturn();

    assertThat(registerResult.getResponse().getContentAsString())
        .doesNotContain("secret", "token", "Authorization", "apiKey");

    mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ONLINE"))
        .andExpect(jsonPath("$.data.lastSeenAt").isString())
        .andExpect(jsonPath("$.data.leaseOwner").doesNotExist());

    MvcResult leaseResult = mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "leaseOwner": "scheduler-local",
                  "leaseTtlSeconds": 30
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("LEASED"))
        .andExpect(jsonPath("$.data.leaseOwner").value("scheduler-local"))
        .andExpect(jsonPath("$.data.leaseExpiresAt").isString())
        .andReturn();

    assertThat(leaseResult.getResponse().getContentAsString())
        .doesNotContain("secret", "token", "Authorization", "apiKey");

    mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "leaseOwner": "scheduler-other",
                  "leaseTtlSeconds": 30
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    MvcResult listResult = mockMvc.perform(get(
            "/v1/workspaces/{workspaceId}/remote-runners",
            workspaceId
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].runnerRef").value("runner-local-1"))
        .andExpect(jsonPath("$.data[0].status").value("LEASED"))
        .andReturn();

    assertThat(listResult.getResponse().getContentAsString())
        .doesNotContain("secret", "token", "Authorization", "apiKey");

    List<AuditEventRecord> auditEvents = auditRepository.findByWorkspaceId(workspaceId);
    assertThat(auditEvents)
        .extracting(AuditEventRecord::eventType)
        .contains("REMOTE_RUNNER_REGISTERED", "REMOTE_RUNNER_HEARTBEAT", "REMOTE_RUNNER_LEASED");
    assertThat(auditEvents)
        .filteredOn(event -> event.eventType().equals("REMOTE_RUNNER_REGISTERED"))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.actorUserId()).isEqualTo("owner-user");
          assertThat(event.message()).contains("runner-local-1");
          assertThat(event.message()).doesNotContain("secret", "token", "Authorization", "apiKey");
        });
  }

  @Test
  void viewerCannotManageWorkspaceRemoteRunners() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");
    grantViewer(workspaceId, "viewer-user");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/remote-runners", workspaceId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("viewer-user", "Viewer User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "viewer runner",
                  "endpointUrl": "http://127.0.0.1:19090/run",
                  "capabilities": ["agent-backend-response.v1"]
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("viewer-user", "Viewer User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "leaseOwner": "scheduler-local",
                  "leaseTtlSeconds": 30
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
  }

  @Test
  void rejectsCredentialBearingEndpointUrlsAndRawSecretAliases() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}",
            workspaceId,
            "runner-local-1"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "bad runner",
                  "endpointUrl": "https://user:pass@example.com/run",
                  "capabilities": ["agent-backend-response.v1"]
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}",
            workspaceId,
            "runner-local-2"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "displayName": "bad runner",
                  "endpointUrl": "http://127.0.0.1:19090/run",
                  "capabilities": ["agent-backend-response.v1"],
                  "runnerToken": "not-accepted"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/remote-runners", workspaceId)
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void openApiDocumentIncludesRemoteRunnerEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs")
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/remote-runners']").exists())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/remote-runners'].get").exists())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}']").exists())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}'].put").exists())
        .andExpect(jsonPath(
            "$.paths['/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat']"
        ).exists())
        .andExpect(jsonPath(
            "$.paths['/v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease']"
        ).exists());
  }

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Runner Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private void grantViewer(String workspaceId, String userId) throws Exception {
    mockMvc.perform(put("/v1/workspaces/{workspaceId}/members/{userId}", workspaceId, userId)
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "teamId": "team-remote-runner-api",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk());
  }

  private static HttpHeaders devHeaders(String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-remote-runner-api");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
