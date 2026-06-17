package com.myworkflow.agent.backend.providersecret;

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
import com.myworkflow.agent.backend.providersecret.ProviderCredentialRepository.ProviderCredentialMetadata;
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
        "my-workflow.backend.dev-principal.team-id=team-provider-credential-api",
        "my-workflow.backend.dev-principal.display-name=Owner User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-provider-credential-api-test"
    }
)
@AutoConfigureMockMvc
class ProviderCredentialControllerTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_provider_credential_api_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ProviderCredentialRepository repository;

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
  void ownerUpsertsAndListsWorkspaceCredentialMetadataWithoutSecretMaterial() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "provider": "mimo-real",
                  "model": "mimo-v2.5",
                  "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
                  "apiKeyEnvName": "MIMO_API_KEY"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.credentialRef").value("workspace-mimo"))
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.scope").value("WORKSPACE"))
        .andExpect(jsonPath("$.data.provider").value("mimo-real"))
        .andExpect(jsonPath("$.data.model").value("mimo-v2.5"))
        .andExpect(jsonPath("$.data.baseUrl").value("https://token-plan-cn.xiaomimimo.com/v1"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.apiKeyEnvName").doesNotExist())
        .andExpect(jsonPath("$.data.apiKeySecretRef").doesNotExist())
        .andExpect(jsonPath("$.data.apiKey").doesNotExist())
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.data.authorization").doesNotExist())
        .andExpect(jsonPath("$.data.Authorization").doesNotExist());

    assertThat(repository.findActiveByScope(
        "team-provider-credential-api",
        workspaceId,
        "workspace-mimo"
    ))
        .hasValueSatisfying(credential -> {
          assertThat(credential.teamId()).isEqualTo("team-provider-credential-api");
          assertThat(credential.workspaceId()).isEqualTo(workspaceId);
          assertThat(credential.apiKeySecretRef()).isEqualTo("env://MIMO_API_KEY");
        });

    MvcResult listResult = mockMvc.perform(get(
            "/v1/workspaces/{workspaceId}/provider-credentials",
            workspaceId
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].credentialRef").value("workspace-mimo"))
        .andExpect(jsonPath("$.data[0].apiKeyEnvName").doesNotExist())
        .andExpect(jsonPath("$.data[0].apiKeySecretRef").doesNotExist())
        .andReturn();

    assertThat(listResult.getResponse().getContentAsString())
        .doesNotContain("MIMO_API_KEY", "env://MIMO_API_KEY", "apiKeySecretRef", "apiKey", "Authorization");

    List<AuditEventRecord> auditEvents = auditRepository.findByWorkspaceId(workspaceId);
    assertThat(auditEvents)
        .filteredOn(event -> event.eventType().equals("PROVIDER_CREDENTIAL_UPSERTED"))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.actorUserId()).isEqualTo("owner-user");
          assertThat(event.teamId()).isEqualTo("team-provider-credential-api");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
          assertThat(event.message()).contains("workspace-mimo", "mimo-real");
          assertThat(event.message()).doesNotContain("MIMO_API_KEY", "env://", "apiKey", "token", "Authorization");
        });
  }

  @Test
  void ownerDisablesWorkspaceCredentialMetadataWithoutDeletingAuditHistoryOrLeakingSecretMaterial() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "provider": "mimo-real",
                  "model": "mimo-v2.5",
                  "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
                  "apiKeyEnvName": "MIMO_API_KEY"
                }
                """))
        .andExpect(status().isOk());

    MvcResult disableResult = mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.credentialRef").value("workspace-mimo"))
        .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
        .andExpect(jsonPath("$.data.scope").value("WORKSPACE"))
        .andExpect(jsonPath("$.data.provider").value("mimo-real"))
        .andExpect(jsonPath("$.data.status").value("DISABLED"))
        .andExpect(jsonPath("$.data.apiKeyEnvName").doesNotExist())
        .andExpect(jsonPath("$.data.apiKeySecretRef").doesNotExist())
        .andReturn();

    assertThat(disableResult.getResponse().getContentAsString())
        .doesNotContain("MIMO_API_KEY", "env://MIMO_API_KEY", "apiKeySecretRef", "apiKey", "Authorization");
    assertThat(repository.findActiveByScope(
        "team-provider-credential-api",
        workspaceId,
        "workspace-mimo"
    )).isEmpty();

    MvcResult listResult = mockMvc.perform(get(
            "/v1/workspaces/{workspaceId}/provider-credentials",
            workspaceId
        )
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].credentialRef").value("workspace-mimo"))
        .andExpect(jsonPath("$.data[0].status").value("DISABLED"))
        .andExpect(jsonPath("$.data[0].apiKeySecretRef").doesNotExist())
        .andReturn();

    assertThat(listResult.getResponse().getContentAsString())
        .doesNotContain("MIMO_API_KEY", "env://MIMO_API_KEY", "apiKeySecretRef", "apiKey", "Authorization");

    List<AuditEventRecord> auditEvents = auditRepository.findByWorkspaceId(workspaceId);
    assertThat(auditEvents)
        .filteredOn(event -> event.eventType().equals("PROVIDER_CREDENTIAL_DISABLED"))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.actorUserId()).isEqualTo("owner-user");
          assertThat(event.teamId()).isEqualTo("team-provider-credential-api");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
          assertThat(event.message()).contains("workspace-mimo");
          assertThat(event.message()).doesNotContain("MIMO_API_KEY", "env://", "apiKey", "token", "Authorization");
        });
  }

  @Test
  void openApiDocumentIncludesJdbcProviderCredentialEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs")
            .headers(devHeaders("owner-user", "Owner User")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials']").exists())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials'].get").exists())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}']").exists())
        .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}'].put").exists())
        .andExpect(jsonPath(
            "$.paths['/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable']"
        ).exists())
        .andExpect(jsonPath(
            "$.paths['/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable'].post"
        ).exists());
  }

  @Test
  void viewerCannotListUpsertOrDisableProviderCredentials() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");
    grantViewer(workspaceId, "viewer-user");

    mockMvc.perform(get("/v1/workspaces/{workspaceId}/provider-credentials", workspaceId)
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("viewer-user", "Viewer User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "provider": "mimo-real",
                  "apiKeyEnvName": "MIMO_API_KEY"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));

    mockMvc.perform(post(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("viewer-user", "Viewer User")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
  }

  @Test
  void rejectsRawSecretFieldsAndInvalidEnvNamesWithoutPersistingCredential() throws Exception {
    String workspaceId = createWorkspaceAs("owner-user", "Owner User");

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "provider": "mimo-real",
                  "apiKeyEnvName": "not-an-env-name"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "provider": "mimo-real",
                  "apiKey": "plain-secret-fixture-not-real",
                  "apiKeyEnvName": "MIMO_API_KEY"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

    mockMvc.perform(put(
            "/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}",
            workspaceId,
            "workspace-mimo"
        )
            .headers(devHeaders("owner-user", "Owner User"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "provider": "mimo-real",
                  "apiKeyEnvName": "MIMO_API_KEY",
                  "rawProviderPayload": {
                    "authorization": "plain-secret-fixture-not-real"
                  }
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

    assertThat(repository.findActiveByScope(
        "team-provider-credential-api",
        workspaceId,
        "workspace-mimo"
    )).isEmpty();
  }

  private String createWorkspaceAs(String userId, String displayName) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .headers(devHeaders(userId, displayName))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Provider Credential Workspace",
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
                  "teamId": "team-provider-credential-api",
                  "role": "WORKSPACE_VIEWER"
                }
                """))
        .andExpect(status().isOk());
  }

  private static HttpHeaders devHeaders(String userId, String displayName) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Dev-User-Id", userId);
    headers.add("X-Dev-Team-Id", "team-provider-credential-api");
    headers.add("X-Dev-Display-Name", displayName);
    return headers;
  }
}
