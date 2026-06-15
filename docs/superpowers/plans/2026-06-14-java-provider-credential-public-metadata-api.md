# Java Provider Credential Public Metadata API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan changes public API and provider credential security boundaries; pause for user review before implementation if the active SOP requires approval for public API/security changes or more than five touched files.

**Goal:** Add a narrow public API for workspace owners to register env-backed provider credential metadata without storing or returning raw provider secrets.

**Architecture:** Keep J24E run execution unchanged: agent runs still consume `providerRuntimeRef = "credential.<credentialRef>"`. J25A adds a workspace-scoped metadata management surface only: owner writes `apiKeyEnvName`, backend stores `apiKeySecretRef = "env://<name>"`, and public responses never include `apiKeySecretRef`, env names, raw API keys, tokens, Authorization headers, or provider payloads. The API is intentionally workspace-scoped; team-scoped credential APIs, secret manager integration, delete/disable lifecycle, key rotation, and real provider validation remain future work.

**Tech Stack:** Java, Spring Boot MVC, JDBC profile, MySQL Testcontainers, JUnit 5, AssertJ, MockMvc.

---

## Execution Status

Completed on 2026-06-14 as J25A.

- RED controller test failed with expected 404/OpenAPI missing path assertions before the API existed.
- RED repository test failed at compile time because `listByWorkspaceScope(...)` did not exist.
- RED service test failed at compile time because the public metadata API did not exist.
- Focused GREEN passed for repository, service, controller, and J24E run credential ref regression.
- Full GREEN passed with 100 Java tests, 44 TypeScript test files / 178 tests, and TypeScript typecheck.
- Controller coverage includes raw secret alias rejection and unknown `rawProviderPayload` fail-closed behavior.
- Static gates passed after archive updates: `git diff --check`, whitespace/conflict scan, token-shaped secret scan, and unchecked plan scan.

## Scope And File Structure

Expected implementation touches more than five files and changes public API/security posture:

- Create: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialController.java`
  - Public controller for workspace-scoped provider credential metadata.
  - `@Profile("jdbc")`; provider credential repository/service are JDBC-only in J25A, so the endpoint is available in production/JDBC profile and does not break default in-memory local contexts.
  - Endpoints:
    - `GET /v1/workspaces/{workspaceId}/provider-credentials`
    - `PUT /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}`
  - Response omits `apiKeySecretRef`, `apiKeyEnvName`, raw secret values, tokens, and Authorization material.
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java`
  - Add owner-only upsert path.
  - Add editor-or-owner list path if product chooses visibility for editors; otherwise owner-only list. Recommended J25A default: owner-only list because credential metadata can reveal provider strategy and external spend posture.
  - Validate credential ref, provider, optional model/baseUrl, and env-var-style `apiKeyEnvName`.
  - Store only `env://<apiKeyEnvName>` as `apiKeySecretRef`.
  - Add audit event on upsert without env name or secret ref.
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java`
  - Add `listByWorkspaceScope(teamId, workspaceId)` for workspace-scoped ACTIVE/DISABLED metadata.
  - Preserve existing `findActiveByScope` precedence for J24E run execution.
- Create: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialControllerTest.java`
  - Public API contract and security/redaction tests with MockMvc and JDBC profile.
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java`
  - Repository list coverage and raw-secret-column guard reuse.
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
  - Record J25A current truth and boundaries.
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Archive RED/GREEN/full/static evidence.
- Update this plan after execution.

## Contract

### PUT `/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}`

Request:

```json
{
  "provider": "mimo-real",
  "model": "mimo-v2.5",
  "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
  "apiKeyEnvName": "MIMO_API_KEY"
}
```

Response:

```json
{
  "schemaVersion": "java-backend-api.v1",
  "ok": true,
  "data": {
    "credentialRef": "workspace-mimo",
    "workspaceId": "workspace-id",
    "scope": "WORKSPACE",
    "provider": "mimo-real",
    "model": "mimo-v2.5",
    "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
    "status": "ACTIVE"
  },
  "error": null
}
```

Rules:

- Requires `WORKSPACE_OWNER`.
- `credentialRef` must match existing safe ref rules: `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`.
- `apiKeyEnvName` must match env-var-style rules: `[A-Z_][A-Z0-9_]{0,127}`.
- Request must not accept `apiKey`, `token`, `authorization`, `Authorization`, `apiKeySecretRef`, or raw provider payload fields.
- Response must not expose `apiKeyEnvName`, `apiKeySecretRef`, raw secret values, internal workspace paths, or server storage refs.
- Upsert is idempotent for the same workspace and credential ref.
- Stores `teamId` from the authoritative workspace record, not from request body.
- Stores `workspaceId` from the path, not from request body.
- Stores status `ACTIVE`; disable/delete lifecycle is out of scope for J25A.

### GET `/v1/workspaces/{workspaceId}/provider-credentials`

Response:

```json
{
  "schemaVersion": "java-backend-api.v1",
  "ok": true,
  "data": [
    {
      "credentialRef": "workspace-mimo",
      "workspaceId": "workspace-id",
      "scope": "WORKSPACE",
      "provider": "mimo-real",
      "model": "mimo-v2.5",
      "baseUrl": "https://token-plan-cn.xiaomimimo.com/v1",
      "status": "ACTIVE"
    }
  ],
  "error": null
}
```

Rules:

- Recommended J25A default: requires `WORKSPACE_OWNER`.
- Returns workspace-scoped metadata only.
- Does not return team-scoped credentials until a team credential API exists.
- Does not return secret reference values or env names.

## Task 1: RED Public Controller Test

**Files:**

- Create: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialControllerTest.java`

- [x] **Step 1: Add failing owner upsert/list/redaction test**

```java
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
        .doesNotContain("MIMO_API_KEY", "env://MIMO_API_KEY", "apiKeySecretRef", "apiKey", "token", "Authorization");

    assertThat(auditRepository.findByWorkspaceId(workspaceId))
        .filteredOn(event -> event.eventType().equals("PROVIDER_CREDENTIAL_UPSERTED"))
        .singleElement()
        .satisfies(event -> {
          assertThat(event.actorUserId()).isEqualTo("owner-user");
          assertThat(event.teamId()).isEqualTo("team-provider-credential-api");
          assertThat(event.workspaceId()).isEqualTo(workspaceId);
          assertThat(event.message()).contains("workspace-mimo", "mimo-real");
          assertThat(event.message()).doesNotContain("MIMO_API_KEY", "env://", "token", "Authorization");
        });
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
```

- [x] **Step 2: Add failing permission and validation tests**

```java
@Test
void openApiDocumentIncludesJdbcProviderCredentialEndpoints() throws Exception {
  mockMvc.perform(get("/v3/api-docs")
          .headers(devHeaders("owner-user", "Owner User")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials']").exists())
      .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials'].get").exists())
      .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}']").exists())
      .andExpect(jsonPath("$.paths['/v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}'].put").exists());
}

@Test
void viewerCannotListOrUpsertProviderCredentials() throws Exception {
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

  assertThat(repository.findActiveByScope(
      "team-provider-credential-api",
      workspaceId,
      "workspace-mimo"
  )).isEmpty();
}
```

- [x] **Step 3: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest test
```

Expected RED:

- `404 NOT_FOUND` for `/v1/workspaces/{workspaceId}/provider-credentials` because `ProviderCredentialController` does not exist yet.
- The OpenAPI assertion also fails because the JDBC-profile controller path does not exist yet.
- No assertion should fail because of malformed test fixture JSON or missing base wiring.

## Task 2: Repository List Method

**Files:**

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java`

- [x] **Step 1: Add RED repository assertions**

In `ProviderCredentialRepositoryTest.savesAndResolvesActiveMetadataByTeamAndWorkspaceScopeWithoutRawSecretFields`, after existing `findActiveByScope` assertions:

```java
assertThat(repository.listByWorkspaceScope("team-a", "workspace-a"))
    .extracting(ProviderCredentialMetadata::credentialRef)
    .containsExactly("workspace-mimo");
assertThat(repository.listByWorkspaceScope("team-a", "workspace-b")).isEmpty();
assertThat(repository.listByWorkspaceScope("team-b", "workspace-c")).isEmpty();
```

- [x] **Step 2: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test
```

Expected RED:

- `ProviderCredentialRepository.listByWorkspaceScope(...)` does not exist.

- [x] **Step 3: Implement repository list**

Add to `ProviderCredentialRepository`:

```java
public List<ProviderCredentialMetadata> listByWorkspaceScope(String teamId, String workspaceId) {
  return jdbcTemplate.query(
      """
          SELECT id,
                 credential_ref,
                 team_id,
                 workspace_id,
                 provider,
                 model,
                 base_url,
                 api_key_secret_ref,
                 status
          FROM provider_credentials
          WHERE team_id = ?
            AND workspace_id = ?
          ORDER BY credential_ref
          """,
      CREDENTIAL_ROW_MAPPER,
      teamId,
      workspaceId
  );
}
```

Also add:

```java
import java.util.List;
```

- [x] **Step 4: Run GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test
```

Expected GREEN:

- Repository test passes and still proves raw secret fields are not record components.

## Task 3: Service Public Metadata Methods

**Files:**

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java`

- [x] **Step 1: Add service RED tests**

Add imports:

```java
import com.myworkflow.agent.backend.audit.AuditRepository;
import java.util.Arrays;
import java.util.List;
```

Add tests:

```java
@Test
void ownerUpsertsAndListsWorkspaceCredentialPublicMetadataWithoutSecretReference() {
  MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
      new BackendPrincipal("owner-a", "team-a", "Owner A")
  );
  InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
  AuditService auditService = new AuditService(principalProvider, auditRepository);
  WorkspaceService workspaceService = workspaceServiceFor(
      principalProvider,
      new InMemoryWorkspaceRepository(),
      auditService
  );
  WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
  CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
  ProviderCredentialService service = new ProviderCredentialService(
      workspaceService,
      credentialRepository,
      auditService
  );

  ProviderCredentialService.ProviderCredentialPublicMetadata created = service.upsertWorkspaceCredential(
      workspace.workspaceId(),
      " workspace-mimo ",
      "mimo-real",
      "mimo-v2.5",
      "https://token-plan-cn.xiaomimimo.com/v1",
      "MIMO_API_KEY"
  );

  assertThat(created.credentialRef()).isEqualTo("workspace-mimo");
  assertThat(created.workspaceId()).isEqualTo(workspace.workspaceId());
  assertThat(created.scope()).isEqualTo("WORKSPACE");
  assertThat(created.provider()).isEqualTo("mimo-real");
  assertThat(created.model()).isEqualTo("mimo-v2.5");
  assertThat(created.baseUrl()).isEqualTo("https://token-plan-cn.xiaomimimo.com/v1");
  assertThat(created.status()).isEqualTo("ACTIVE");
  assertThat(Arrays.stream(ProviderCredentialService.ProviderCredentialPublicMetadata.class.getRecordComponents())
      .map(component -> component.getName()))
      .doesNotContain("apiKeyEnvName", "apiKeySecretRef", "apiKey", "token", "authorization", "Authorization");

  assertThat(credentialRepository.findActiveByScope(workspace.teamId(), workspace.workspaceId(), "workspace-mimo"))
      .hasValueSatisfying(credential -> {
        assertThat(credential.teamId()).isEqualTo(workspace.teamId());
        assertThat(credential.workspaceId()).isEqualTo(workspace.workspaceId());
        assertThat(credential.apiKeySecretRef()).isEqualTo("env://MIMO_API_KEY");
      });

  assertThat(service.listWorkspaceCredentials(workspace.workspaceId()))
      .extracting(ProviderCredentialService.ProviderCredentialPublicMetadata::credentialRef)
      .containsExactly("workspace-mimo");
  assertThat(auditRepository.findByWorkspaceId(workspace.workspaceId()))
      .filteredOn(event -> event.eventType().equals("PROVIDER_CREDENTIAL_UPSERTED"))
      .singleElement()
      .satisfies(event -> {
        assertThat(event.actorUserId()).isEqualTo("owner-a");
        assertThat(event.teamId()).isEqualTo(workspace.teamId());
        assertThat(event.message()).contains("workspace-mimo", "mimo-real");
        assertThat(event.message()).doesNotContain("MIMO_API_KEY", "env://", "apiKey", "token", "Authorization");
      });
}

@Test
void rejectsPublicCredentialMetadataAccessWithoutWorkspaceOwnerRole() {
  MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
      new BackendPrincipal("owner-a", "team-a", "Owner A")
  );
  InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
  AuditService auditService = new AuditService(principalProvider, auditRepository);
  WorkspaceService workspaceService = workspaceServiceFor(
      principalProvider,
      new InMemoryWorkspaceRepository(),
      auditService
  );
  WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
  workspaceService.grantMember(
      workspace.workspaceId(),
      "viewer-a",
      workspace.teamId(),
      WorkspaceRole.WORKSPACE_VIEWER
  );
  CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
  ProviderCredentialService service = new ProviderCredentialService(
      workspaceService,
      credentialRepository,
      auditService
  );

  principalProvider.setPrincipal(new BackendPrincipal("viewer-a", workspace.teamId(), "Viewer A"));

  assertThatThrownBy(() -> service.listWorkspaceCredentials(workspace.workspaceId()))
      .isInstanceOf(WorkspaceForbiddenException.class);
  assertThatThrownBy(() -> service.upsertWorkspaceCredential(
      workspace.workspaceId(),
      "workspace-mimo",
      "mimo-real",
      null,
      null,
      "MIMO_API_KEY"
  )).isInstanceOf(WorkspaceForbiddenException.class);
  assertThat(credentialRepository.savedCredentials()).isEmpty();
}

@Test
void rejectsInvalidPublicCredentialInputsBeforeRepositorySave() {
  MutablePrincipalProvider principalProvider = new MutablePrincipalProvider(
      new BackendPrincipal("owner-a", "team-a", "Owner A")
  );
  InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
  AuditService auditService = new AuditService(principalProvider, auditRepository);
  WorkspaceService workspaceService = workspaceServiceFor(
      principalProvider,
      new InMemoryWorkspaceRepository(),
      auditService
  );
  WorkspaceRecord workspace = workspaceService.registerWorkspace("Team Knowledge", "main");
  CapturingProviderCredentialRepository credentialRepository = new CapturingProviderCredentialRepository();
  ProviderCredentialService service = new ProviderCredentialService(
      workspaceService,
      credentialRepository,
      auditService
  );

  assertThatThrownBy(() -> service.upsertWorkspaceCredential(
      workspace.workspaceId(),
      "../mimo",
      "mimo-real",
      null,
      null,
      "MIMO_API_KEY"
  ))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Invalid provider credential reference");
  assertThatThrownBy(() -> service.upsertWorkspaceCredential(
      workspace.workspaceId(),
      "workspace-mimo",
      "mimo-real",
      null,
      null,
      "not-an-env-name"
  ))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("apiKeyEnvName must be an environment variable name");
  assertThatThrownBy(() -> service.upsertWorkspaceCredential(
      workspace.workspaceId(),
      "workspace-mimo",
      "unknown-provider",
      null,
      null,
      "MIMO_API_KEY"
  ))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unsupported provider credential provider");
  assertThat(credentialRepository.savedCredentials()).isEmpty();
}
```

Update helper signature:

```java
private WorkspaceService workspaceServiceFor(
    PrincipalProvider principalProvider,
    WorkspaceRepository repository,
    AuditService auditService
) {
  return new WorkspaceService(
      new BackendProperties(
          dataRoot.toString(),
          "owner-a",
          "team-a",
          "Owner A"
      ),
      principalProvider,
      repository,
      auditService
  );
}
```

Update existing call sites in this test file to pass:

```java
new AuditService(principalProvider, new InMemoryAuditRepository())
```

Extend `CapturingProviderCredentialRepository`:

```java
@Override
public List<ProviderCredentialMetadata> listByWorkspaceScope(String teamId, String workspaceId) {
  return credentials.values().stream()
      .filter(credential -> credential.teamId().equals(teamId))
      .filter(credential -> workspaceId.equals(credential.workspaceId()))
      .sorted(Comparator.comparing(ProviderCredentialMetadata::credentialRef))
      .toList();
}

private List<ProviderCredentialMetadata> savedCredentials() {
  return credentials.values().stream()
      .sorted(Comparator.comparing(ProviderCredentialMetadata::credentialRef))
      .toList();
}
```

- [x] **Step 2: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test
```

Expected RED:

- New service methods and public metadata record do not exist.

- [x] **Step 3: Implement service methods**

Add constants:

```java
private static final String PROVIDER_CREDENTIAL_ID_PREFIX = "cred_";
private static final Pattern SAFE_ENV_NAME_PATTERN =
    Pattern.compile("[A-Z_][A-Z0-9_]{0,127}");
```

Add imports:

```java
import com.myworkflow.agent.backend.audit.AuditService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
```

Add methods:

```java
public ProviderCredentialPublicMetadata upsertWorkspaceCredential(
    String workspaceId,
    String credentialRef,
    String provider,
    String model,
    String baseUrl,
    String apiKeyEnvName
) {
  String normalizedRef = normalizeCredentialRef(credentialRef);
  WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
  String normalizedProvider = requireSupportedProvider(provider);
  String normalizedEnvName = requireEnvName(apiKeyEnvName);
  ProviderCredentialMetadata saved = repository.save(new ProviderCredentialMetadata(
      providerCredentialId(workspace.workspaceId(), normalizedRef),
      normalizedRef,
      workspace.teamId(),
      workspace.workspaceId(),
      normalizedProvider,
      blankToNull(model),
      blankToNull(baseUrl),
      "env://" + normalizedEnvName,
      "ACTIVE"
  ));
  auditService.record(
      workspace.workspaceId(),
      null,
      "PROVIDER_CREDENTIAL_UPSERTED",
      "Provider credential metadata upserted: credentialRef=%s provider=%s scope=WORKSPACE"
          .formatted(saved.credentialRef(), saved.provider())
  );
  return toPublicMetadata(saved);
}

public List<ProviderCredentialPublicMetadata> listWorkspaceCredentials(String workspaceId) {
  WorkspaceRecord workspace = workspaceService.requireWorkspaceRole(workspaceId, WorkspaceRole.WORKSPACE_OWNER);
  return repository.listByWorkspaceScope(workspace.teamId(), workspace.workspaceId()).stream()
      .map(ProviderCredentialService::toPublicMetadata)
      .toList();
}
```

Constructor change:

```java
private final AuditService auditService;

public ProviderCredentialService(
    WorkspaceService workspaceService,
    ProviderCredentialRepository repository,
    AuditService auditService
) {
  this.workspaceService = workspaceService;
  this.repository = repository;
  this.auditService = auditService;
}
```

Projection and env validation:

```java
private static String providerCredentialId(String workspaceId, String credentialRef) {
  String source = workspaceId + "\n" + credentialRef;
  return PROVIDER_CREDENTIAL_ID_PREFIX + UUID.nameUUIDFromBytes(
      source.getBytes(StandardCharsets.UTF_8)
  ).toString().replace("-", "");
}

private static String requireEnvName(String envName) {
  String normalizedEnvName = blankToNull(envName);
  if (normalizedEnvName == null || !SAFE_ENV_NAME_PATTERN.matcher(normalizedEnvName).matches()) {
    throw new IllegalArgumentException("apiKeyEnvName must be an environment variable name");
  }
  return normalizedEnvName;
}

private static ProviderCredentialPublicMetadata toPublicMetadata(
    ProviderCredentialMetadata credential
) {
  return new ProviderCredentialPublicMetadata(
      credential.credentialRef(),
      credential.workspaceId(),
      credential.workspaceId() == null ? "TEAM" : "WORKSPACE",
      credential.provider(),
      blankToNull(credential.model()),
      blankToNull(credential.baseUrl()),
      credential.status()
  );
}

public record ProviderCredentialPublicMetadata(
    String credentialRef,
    String workspaceId,
    String scope,
    String provider,
    String model,
    String baseUrl,
    String status
) {
}
```

- [x] **Step 4: Update service constructor call sites**

Search:

```bash
rg -n "new ProviderCredentialService|ProviderCredentialService\\(" backend/src/main/java backend/src/test/java
```

Update all explicit constructor calls to pass `AuditService`.

- [x] **Step 5: Run GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test
```

Expected GREEN:

- Existing J24C/J24D tests remain green.
- New J25A service tests pass.

## Task 4: Public Controller

**Files:**

- Create: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialControllerTest.java`

- [x] **Step 1: Implement controller**

```java
package com.myworkflow.agent.backend.providersecret;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("jdbc")
@RequestMapping(path = "/v1/workspaces/{workspaceId}/provider-credentials", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProviderCredentialController {

  private final ProviderCredentialService providerCredentialService;

  public ProviderCredentialController(ProviderCredentialService providerCredentialService) {
    this.providerCredentialService = providerCredentialService;
  }

  @GetMapping
  public ApiEnvelope<List<ProviderCredentialService.ProviderCredentialPublicMetadata>> listCredentials(
      @PathVariable String workspaceId
  ) {
    return ApiEnvelope.ok(providerCredentialService.listWorkspaceCredentials(workspaceId));
  }

  @PutMapping(path = "/{credentialRef}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiEnvelope<ProviderCredentialService.ProviderCredentialPublicMetadata> upsertCredential(
      @PathVariable String workspaceId,
      @PathVariable String credentialRef,
      @Valid @RequestBody UpsertProviderCredentialRequest request
  ) {
    rejectRawSecretAliases(request);
    return ApiEnvelope.ok(providerCredentialService.upsertWorkspaceCredential(
        workspaceId,
        credentialRef,
        request.provider(),
        request.model(),
        request.baseUrl(),
        request.apiKeyEnvName()
    ));
  }

  private static void rejectRawSecretAliases(UpsertProviderCredentialRequest request) {
    if (request.apiKey() != null
        || request.token() != null
        || request.authorization() != null
        || request.Authorization() != null
        || request.apiKeySecretRef() != null) {
      throw new IllegalArgumentException("Raw provider secrets are not accepted");
    }
  }

  public record UpsertProviderCredentialRequest(
      @NotBlank String provider,
      String model,
      String baseUrl,
      @NotBlank String apiKeyEnvName,
      String apiKey,
      String token,
      String authorization,
      String Authorization,
      String apiKeySecretRef
  ) {
  }
}
```

- [x] **Step 2: Run focused controller GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest test
```

Expected GREEN:

- Owner upsert/list passes.
- Viewer forbidden tests pass.
- Raw secret field and invalid env-name tests pass.

## Task 5: Focused Regression

**Files:**

- No additional files beyond Tasks 1-4.

- [x] **Step 1: Run focused regression**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialRunControllerTest test
```

Expected GREEN:

- Public metadata API passes.
- Existing J24E run execution still resolves DB-backed credential refs.
- JDBC-profile OpenAPI assertion in `ProviderCredentialControllerTest` includes new public endpoints.

## Task 6: Docs And Archive

**Files:**

- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md`

- [x] **Step 1: Update architecture spec**

Add J25A status:

```markdown
J25A provider credential public metadata API baseline 已实现
```

Add boundary statement:

```markdown
J25A 已实现 workspace-scoped provider credential public metadata API baseline：workspace owner 可以通过 `PUT /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}` 写入 env-backed credential metadata，并通过 `GET /v1/workspaces/{workspaceId}/provider-credentials` 列出 workspace-scoped metadata。请求只接受 `apiKeyEnvName`，后端保存 `env://<name>` reference；public response 不返回 `apiKeyEnvName`、`apiKeySecretRef` 或 raw secret。J25A 不包含 team-scoped credential API、secret manager/KMS/keychain/file lookup、delete/disable lifecycle、key rotation、真实 provider validation 或 remote runner secret distribution。
```

- [x] **Step 2: Update delivery report**

Add a `Phase J25A - Java Provider Credential Public Metadata API` section with:

- RED evidence for controller, repository, and service.
- Focused GREEN commands and pass counts.
- Full Java/TS/typecheck/static evidence.
- Boundary statement that all provider calls are fake/local test evidence; no real external provider call.

- [x] **Step 3: Mark this plan complete**

Only after all commands below pass, change all unchecked boxes to checked and add `Execution Status`.

## Task 7: Full Verification

- [x] **Step 1: Java full suite**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 2: TypeScript full suite**

```bash
npm test
```

- [x] **Step 3: TypeScript typecheck**

```bash
npm run typecheck
```

- [x] **Step 4: Static gates**

```bash
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret backend/src/test/java/com/myworkflow/agent/backend/providersecret docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md
```

Expected:

- `git diff --check` exits 0.
- Whitespace/conflict scan has no matches.
- Token/secret scan has no matches for real-token-shaped provider secrets.
- Plan unchecked scan has no matches only after execution is fully archived.

## Acceptance

J25A is complete only if:

- Workspace owner can upsert workspace-scoped env-backed provider credential metadata through public API.
- Workspace owner can list workspace-scoped credential metadata through public API.
- Viewer and cross-team users cannot list or upsert credential metadata.
- Public request rejects raw secret aliases and secret-looking `apiKeyEnvName` values.
- Public response never includes `apiKeyEnvName`, `apiKeySecretRef`, raw secret fields, internal workspace paths, or server storage refs.
- Repository stores `apiKeySecretRef = "env://<envName>"` only after env-name validation.
- Existing J24E run path still consumes `providerRuntimeRef = "credential.<credentialRef>"`.
- Audit records credential metadata upsert without secret/env-name leakage.
- JDBC-profile OpenAPI includes the new endpoints.
- No public team-scoped credential API, secret manager lookup, raw secret storage/read, real provider validation, key rotation, delete/disable lifecycle, production dependency, or remote runner secret distribution is added.

## Implementation Gate

Do not execute this plan until the user accepts the scope expansion. This phase is intentionally larger than the five-file local limit because it creates a new public API, touches permission/security behavior, and must update tests plus architecture/report archives.
