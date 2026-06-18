# MySQL Backend Phase A Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口 MySQL-backed Java 后端 Phase A，使前端和 runtime 可以基于真实后端契约开始联调，同时不把 baseline 能力包装成生产完成能力。

**Architecture:** Phase A 继续采用 Spring Boot modular monolith、MySQL/Flyway `jdbc` profile、DB-backed job table 和现有 worker bridge。每个切片保持 5 个文件以内，优先修正 capability truth、增加 MySQL/JDBC smoke、补前端 handoff 文档，再补 runtime DB-backed handoff smoke 和 delivery report。

**Tech Stack:** Java 17 target / Spring Boot 3.x / Spring MVC / Spring Security / Spring JDBC / Flyway / MySQL Testcontainers / JUnit 5 / AssertJ / MockMvc / TypeScript typecheck.

---

## Scope And File Map

### Existing Files To Read Before Editing

- `docs/superpowers/specs/2026-06-18-mysql-backend-phase-a-design.md`
- `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceJdbcControllerTest.java`
- `backend/src/test/java/com/myworkflow/agent/backend/run/AgentRunLocalTsControllerTest.java`
- `backend/src/test/java/com/myworkflow/agent/backend/artifact/ArtifactControllerTest.java`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`

### Files Planned By Slice

- Slice 1 modifies:
  - `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
  - `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
  - `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Slice 2 creates:
  - `backend/src/test/java/com/myworkflow/agent/backend/ops/MysqlBackendPhaseAReadinessTest.java`
  - modifies `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Slice 3 creates:
  - `docs/architecture/java-backend-frontend-handoff.md`
  - modifies `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
  - modifies `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Slice 4 creates:
  - `backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRuntimeHandoffControllerTest.java`
  - modifies `docs/reports/runtime-work-item-execution-resume-delivery.md`

If an implementation step needs more than 5 files in one slice, pause and explain before editing.

---

## Task 1: Contract Truth Cleanup

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Write the failing contract truth test**

In `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`, update the capability assertions inside `integrationContractReturnsFrontendAndRuntimeReadinessWithoutSensitiveMaterial()` so they distinguish baseline/local capability from production capability:

```java
        .andExpect(jsonPath("$.data.capabilities.asyncAgentRuns").value(true))
        .andExpect(jsonPath("$.data.capabilities.sseRunEvents").value(true))
        .andExpect(jsonPath("$.data.capabilities.approvalBoundary").value(true))
        .andExpect(jsonPath("$.data.capabilities.artifactRegistry").value(true))
        .andExpect(jsonPath("$.data.capabilities.providerCredentialMetadata").value(true))
        .andExpect(jsonPath("$.data.capabilities.oidcJwtBearer").value(true))
        .andExpect(jsonPath("$.data.capabilities.oauthLoginSession").value(false))
        .andExpect(jsonPath("$.data.capabilities.tokenIntrospection").value(true))
        .andExpect(jsonPath("$.data.capabilities.externalDirectorySync").value(true))
        .andExpect(jsonPath("$.data.capabilities.externalDirectorySnapshotSync").value(true))
        .andExpect(jsonPath("$.data.capabilities.productionDirectoryConnector").value(false))
        .andExpect(jsonPath("$.data.capabilities.httpSecretResolver").value(true))
        .andExpect(jsonPath("$.data.capabilities.productionSecretManager").value(false))
        .andExpect(jsonPath("$.data.capabilities.remoteRunnerDispatch").value(true))
        .andExpect(jsonPath("$.data.capabilities.remoteRunnerArtifactUpload").value(true))
        .andExpect(jsonPath("$.data.capabilities.multiNodeStreamFanout").value(false))
```

This is expected to fail before production code changes because `externalDirectorySnapshotSync`, `productionDirectoryConnector`, and `httpSecretResolver` do not exist yet, and `productionSecretManager` is currently `true`.

- [ ] **Step 2: Run the focused RED test**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest
```

Expected:

```text
BUILD FAILURE
No value at JSON path "$.data.capabilities.externalDirectorySnapshotSync"
```

or a later assertion failure showing `productionSecretManager` expected `false` but was `true`. If the failure is a compile error or unrelated Spring context failure, stop and debug before editing production code.

- [ ] **Step 3: Implement the minimal contract truth change**

In `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`, replace the `IntegrationCapabilities` record and `current()` factory with:

```java
  public record IntegrationCapabilities(
      boolean asyncAgentRuns,
      boolean sseRunEvents,
      boolean approvalBoundary,
      boolean artifactRegistry,
      boolean providerCredentialMetadata,
      boolean oidcJwtBearer,
      boolean oauthLoginSession,
      boolean tokenIntrospection,
      boolean externalDirectorySync,
      boolean externalDirectorySnapshotSync,
      boolean productionDirectoryConnector,
      boolean httpSecretResolver,
      boolean productionSecretManager,
      boolean remoteRunnerDispatch,
      boolean remoteRunnerArtifactUpload,
      boolean multiNodeStreamFanout
  ) {
    static IntegrationCapabilities current() {
      return new IntegrationCapabilities(
          true,
          true,
          true,
          true,
          true,
          true,
          false,
          true,
          true,
          true,
          false,
          true,
          false,
          true,
          true,
          false
      );
    }
  }
```

Meaning:

- `externalDirectorySync=true`: existing snapshot sync endpoint exists.
- `externalDirectorySnapshotSync=true`: explicit current-truth name for the existing endpoint.
- `productionDirectoryConnector=false`: no SCIM/LDAP/IdP scheduled connector.
- `httpSecretResolver=true`: HTTP resolver baseline exists.
- `productionSecretManager=false`: no vendor KMS/Keychain/rotation production manager.
- `remoteRunnerArtifactUpload=true`: endpoint and tests exist for run-scoped remote artifact upload baseline.

- [ ] **Step 4: Run focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest
```

Expected:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 5: Update delivery report**

Append a section to `docs/reports/runtime-work-item-execution-resume-delivery.md`:

```markdown
## Backend Phase A Contract Truth Cleanup

Status: implemented and verified as a Phase A capability declaration correction.

Scope delivered:

- `productionSecretManager` now reports `false`; the current HTTP/file/env resolvers remain baseline resolver capabilities, not a production KMS/secret-manager completion claim.
- `httpSecretResolver` reports `true`.
- `externalDirectorySnapshotSync` reports `true`.
- `productionDirectoryConnector` reports `false`.
- `oauthLoginSession` and `multiNodeStreamFanout` remain `false`.
- `remoteRunnerArtifactUpload` remains `true` because the run-scoped artifact upload endpoint and tests already exist.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest`
  - Failed before implementation because the new explicit capability fields were absent and `productionSecretManager` was over-claimed.

Focused GREEN:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest`
  - 1 test passed; Maven reported `BUILD SUCCESS`.

Evidence boundaries:

- This proves API capability truthfulness through MockMvc.
- This does not add OAuth login/callback, vendor KMS, SCIM/LDAP connector, or multi-node stream fanout.
```

- [ ] **Step 6: Run static and token scans**

Run:

```bash
git diff --check
rg -n '[ \t]$|^(<<<<<<<|=======|>>>>>>>)' backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
rg -n 'tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}' backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
```

Expected:

- `git diff --check` exits 0.
- Both `rg` commands exit 1 with no matches.

- [ ] **Step 7: Commit Slice 1**

Run:

```bash
git add backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
git commit -m "feat: correct backend phase a capability claims"
```

---

## Task 2: MySQL Profile Readiness Smoke

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/MysqlBackendPhaseAReadinessTest.java`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Write the MySQL readiness smoke test**

Create `backend/src/test/java/com/myworkflow/agent/backend/ops/MysqlBackendPhaseAReadinessTest.java`:

```java
package com.myworkflow.agent.backend.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.run.AgentWorker;
import com.myworkflow.agent.backend.run.AgentWorkerResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
    classes = {
        BackendApplication.class,
        MysqlBackendPhaseAReadinessTest.TestAgentWorkerConfig.class
    },
    properties = {
        "spring.profiles.active=jdbc",
        "my-workflow.backend.dev-principal.user-id=phase_a_user",
        "my-workflow.backend.dev-principal.team-id=phase_a_team",
        "my-workflow.backend.dev-principal.display-name=Phase A User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-backend-phase-a-readiness"
    }
)
@AutoConfigureMockMvc
class MysqlBackendPhaseAReadinessTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_phase_a_readiness")
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
  void phaseAReadinessRunsCoreFrontendAndRuntimeFlowOnMysql() throws Exception {
    mockMvc.perform(get("/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.data.status").value("ready"));

    MvcResult workspaceResult = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Phase A MySQL Workspace",
                  "defaultBranch": "main"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.workspaceRoot").doesNotExist())
        .andExpect(jsonPath("$.data.serverStorageRef").doesNotExist())
        .andReturn();
    String workspaceId = JsonPath.read(workspaceResult.getResponse().getContentAsString(), "$.data.workspaceId");

    mockMvc.perform(get("/v1/workspaces"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.workspaceId == '%s')]".formatted(workspaceId)).exists());

    MvcResult runResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "准备 Phase A MySQL readiness",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andReturn();
    String runId = JsonPath.read(runResult.getResponse().getContentAsString(), "$.data.runId");

    MvcResult completed = pollRun(runId, "WAITING_APPROVAL");
    String completedBody = completed.getResponse().getContentAsString();
    assertThat(completedBody)
        .doesNotContain("token")
        .doesNotContain("apiKeySecretRef")
        .doesNotContain("workspaceRoot")
        .doesNotContain("serverStorageRef");

    mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.eventType == 'RUN_QUEUED')]").exists())
        .andExpect(jsonPath("$.data[?(@.eventType == 'COMPLETED')]").exists());

    MvcResult artifacts = mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef").value(".agent-runs/%s/phase-a-readiness.json".formatted(runId)))
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist())
        .andReturn();
    String artifactId = JsonPath.read(artifacts.getResponse().getContentAsString(), "$.data[0].artifactId");

    mockMvc.perform(get("/v1/artifacts/{artifactId}", artifactId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("{\"phase\":\"A\",\"mysql\":true}"))
        .andExpect(jsonPath("$.data.absolutePath").doesNotExist());

    mockMvc.perform(get("/v1/agent-runs/{runId}/approvals", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef").value(".agent-runs/%s/phase-a-readiness.json".formatted(runId)))
        .andExpect(jsonPath("$.data[0].targetWorkspacePaths[0]").value("knowledge-base/phase-a.md"));
  }

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(100);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }

  @TestConfiguration
  static class TestAgentWorkerConfig {
    @Bean
    @Primary
    AgentWorker agentWorker() {
      return (request) -> {
        try {
          Path artifact = Path.of(request.workspaceRoot(), ".agent-runs", request.runId(), "phase-a-readiness.json");
          Files.createDirectories(artifact.getParent());
          Files.writeString(artifact, "{\"phase\":\"A\",\"mysql\":true}");
        } catch (Exception exception) {
          throw new IllegalStateException("failed to write phase A readiness artifact", exception);
        }
        return new AgentWorkerResponse(
            "agent-backend-response.v1",
            request.runId(),
            "WAITING_APPROVAL",
            "candidate-patch",
            "Phase A readiness candidate patch",
            false,
            true,
            List.of(".agent-runs/%s/phase-a-readiness.json".formatted(request.runId())),
            false,
            List.of("knowledge-base/phase-a.md")
        );
      };
    }
  }
}
```

This smoke may pass on the first run because much of the MySQL/JDBC flow already exists. If it passes immediately, do not change production code; record that the readiness behavior already existed and the new test is acceptance evidence.

- [ ] **Step 2: Run the focused MySQL smoke**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=MysqlBackendPhaseAReadinessTest
```

Expected if current behavior is already sufficient:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Expected if a real gap exists:

```text
BUILD FAILURE
```

The failure must point to a specific missing MySQL/JDBC behavior before editing production code.

- [ ] **Step 3: Implement only if RED reveals a real gap**

If Step 2 fails, make the smallest production change needed for the failed assertion. Examples:

- If artifact registration is missing under JDBC, fix `JdbcArtifactRepository`.
- If approval creation is missing under JDBC, fix `JdbcApprovalRepository`.
- If events are missing under JDBC, fix `JdbcRunEventRepository` or `AgentRunService`.

Do not edit production code if the smoke passes immediately.

- [ ] **Step 4: Update delivery report**

Append:

```markdown
## Backend Phase A MySQL Readiness Smoke

Status: verified as a MySQL/Testcontainers integration smoke for the DB-only Phase A backend.

Scope delivered:

- Added `MysqlBackendPhaseAReadinessTest`.
- The smoke uses `spring.profiles.active=jdbc` and MySQL Testcontainers.
- It covers readiness, workspace create/list, agent run create/poll, run events, artifact list/read, and approval list.
- Response assertions check that token-shaped fields, `apiKeySecretRef`, `workspaceRoot`, and `serverStorageRef` are not exposed.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=MysqlBackendPhaseAReadinessTest`
  - 1 test passed; Maven reported `BUILD SUCCESS`.

Evidence boundaries:

- This is a MySQL integration smoke, not a deployed browser E2E or real provider E2E.
- The worker in this test is an injected test worker, not a real provider or production remote runner.
```

If Step 2 failed first, include the RED evidence before Focused verification.

- [ ] **Step 5: Run scans and commit**

Run:

```bash
git diff --check
rg -n '[ \t]$|^(<<<<<<<|=======|>>>>>>>)' backend/src/test/java/com/myworkflow/agent/backend/ops/MysqlBackendPhaseAReadinessTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
rg -n 'tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}' backend/src/test/java/com/myworkflow/agent/backend/ops/MysqlBackendPhaseAReadinessTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
git add backend/src/test/java/com/myworkflow/agent/backend/ops/MysqlBackendPhaseAReadinessTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
git commit -m "test: add mysql backend phase a readiness smoke"
```

---

## Task 3: Frontend Handoff Contract Documentation

**Files:**
- Create: `docs/architecture/java-backend-frontend-handoff.md`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Add a contract test for frontend handoff endpoint visibility**

In `OpsIntegrationContractControllerTest`, add these assertions after existing frontend endpoint assertions:

```java
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/ops/integration-contract')]").exists())
        .andExpect(jsonPath("$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/session/csrf')]").exists())
```

If `/v1/ops/integration-contract` is not currently included in `frontendRequiredEndpoints`, the test should fail. That is the RED for this slice.

- [ ] **Step 2: Run focused RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest
```

Expected before implementation if the endpoint is absent:

```text
No value at JSON path "$.data.frontendRequiredEndpoints[?(@.method == 'GET' && @.path == '/v1/ops/integration-contract')]"
BUILD FAILURE
```

- [ ] **Step 3: Add integration-contract endpoint to frontend contract if missing**

If Step 2 fails, add this line to `frontendEndpointContract()` in `OpsController`:

```java
          new IntegrationEndpoint("GET", "/v1/ops/integration-contract", "frontend/runtime capability contract")
```

Place it after `/v1/ops/auth-config`.

- [ ] **Step 4: Create frontend handoff doc**

Create `docs/architecture/java-backend-frontend-handoff.md`:

```markdown
# Java Backend Frontend Handoff

> 状态：Phase A handoff contract。本文说明前端 API client 如何接入 MySQL-backed Java 后端一期。

## Base Contract

- API base path: `/v1`
- Envelope: `java-backend-api.v1`
- Capability endpoint: `GET /v1/ops/integration-contract`
- Auth diagnostics: `GET /v1/ops/auth-config`

前端必须先读取 `GET /v1/ops/integration-contract`，用返回的 `frontendRequiredEndpoints` 和 `capabilities` 判断 UI 是否可以开启对应功能。

## Credentialed Browser Requests

Session-cookie 模式下：

1. 前端用 `credentials: "include"` 请求后端。
2. mutating request 前先请求 `GET /v1/session/csrf`。
3. 后端返回 `data.token` 和 `data.headerName = "X-CSRF-Token"`，并写入 `MWA_CSRF` cookie。
4. 前端对 `POST`、`PUT`、`PATCH`、`DELETE` 请求加 `X-CSRF-Token: <token>`。

Bearer-token API client 不需要 CSRF header。

## Envelope Unwrap

前端只消费：

- `schemaVersion`
- `ok`
- `data`
- `error.code`
- `error.message`
- `error.retryable`

`ok=false` 时不要读 `data`。

## Run Flow

1. `POST /v1/workspaces/{workspaceId}/agent-runs`
2. `GET /v1/agent-runs/{runId}`
3. `GET /v1/agent-runs/{runId}/events`
4. Optional: `GET /v1/agent-runs/{runId}/events/stream`
5. `GET /v1/agent-runs/{runId}/artifacts`
6. `GET /v1/agent-runs/{runId}/approvals`

SSE EOF is not terminal evidence. The UI must use the run status or durable event list to decide terminal state.

## Artifact And Approval Rules

- `targetWorkspacePaths` means proposed targets, not confirmed writes.
- `wroteWorkspace=true` is required before the UI claims a workspace write happened.
- Artifact API responses expose artifact IDs and safe relative refs, not absolute paths.
- Approval decisions are explicit user actions through `POST /v1/agent-runs/{runId}/approvals`.

## Fields The Frontend Must Not Display

- raw provider token
- `apiKeySecretRef`
- environment variable names used for secrets
- `Authorization`
- cookie values
- absolute workspace paths
- raw provider payload
- Java exception stack traces

## Phase A Boundaries

This handoff supports local/team DB-only integration. It does not prove production OAuth login/callback, production secret manager, production remote runner identity, multi-node stream fanout, or deployed browser E2E.
```

- [ ] **Step 5: Run focused GREEN and docs scans**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest
git diff --check
rg -n '[ \t]$|^(<<<<<<<|=======|>>>>>>>)' docs/architecture/java-backend-frontend-handoff.md backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java docs/reports/runtime-work-item-execution-resume-delivery.md
rg -n 'tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}' docs/architecture/java-backend-frontend-handoff.md backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java docs/reports/runtime-work-item-execution-resume-delivery.md
```

Expected:

- Maven focused test passes.
- `git diff --check` exits 0.
- Both `rg` scans exit 1 with no matches.

- [ ] **Step 6: Update report and commit**

Append a concise report section:

```markdown
## Backend Phase A Frontend Handoff Contract

Status: documented and verified for frontend API client handoff.

Scope delivered:

- Added `docs/architecture/java-backend-frontend-handoff.md`.
- The handoff defines envelope unwrap, CSRF flow, run/event/artifact/approval flow, SSE EOF boundary, and forbidden frontend fields.
- The integration contract exposes `GET /v1/ops/integration-contract` to frontend clients.

Evidence boundaries:

- This is a contract/documentation handoff, not frontend implementation or browser E2E.
```

Commit:

```bash
git add docs/architecture/java-backend-frontend-handoff.md backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java docs/reports/runtime-work-item-execution-resume-delivery.md
git commit -m "docs: add backend frontend handoff contract"
```

---

## Task 4: Runtime DB-backed Handoff Smoke

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRuntimeHandoffControllerTest.java`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Write DB-backed runtime handoff smoke**

Create `backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRuntimeHandoffControllerTest.java`:

```java
package com.myworkflow.agent.backend.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.myworkflow.agent.backend.BackendApplication;
import com.myworkflow.agent.backend.workspace.WorkspaceService;
import java.nio.file.Path;
import java.time.Duration;
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
        "my-workflow.backend.dev-principal.user-id=runtime_handoff_user",
        "my-workflow.backend.dev-principal.team-id=runtime_handoff_team",
        "my-workflow.backend.dev-principal.display-name=Runtime Handoff User",
        "my-workflow.backend.data-root=${java.io.tmpdir}/my-workflow-agent-runtime-handoff-test"
    }
)
@AutoConfigureMockMvc
class JdbcRuntimeHandoffControllerTest {

  @Container
  private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
      .withDatabaseName("my_workflow_runtime_handoff_test")
      .withUsername("test")
      .withPassword("test");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private WorkspaceService workspaceService;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Test
  void mysqlBackedJavaApiRunsLocalTsWorkerAndPersistsRuntimeEvidence() throws Exception {
    String workspaceId = createWorkspace("Runtime Handoff");
    Path repoRoot = Path.of("..").toAbsolutePath().normalize();
    TestWorkspaceCopier.copy(
        repoRoot.resolve("tests/fixtures/workspaces/basic-raw-mirror"),
        workspaceService.resolveContentPath(workspaceId, "")
    );

    MvcResult createRunResult = mockMvc.perform(post("/v1/workspaces/{workspaceId}/agent-runs", workspaceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userMessage": "总结当前知识库",
                  "mode": "deterministic-open-agent"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("QUEUED"))
        .andReturn();

    String runId = JsonPath.read(createRunResult.getResponse().getContentAsString(), "$.data.runId");
    MvcResult completed = pollRun(runId, "SUCCEEDED");
    String body = completed.getResponse().getContentAsString();
    assertThat((String) JsonPath.read(body, "$.data.outputKind")).isEqualTo("answer");
    assertThat((String) JsonPath.read(body, "$.data.displayText")).contains("Sources:");
    assertThat((Boolean) JsonPath.read(body, "$.data.wroteWorkspace")).isFalse();
    assertThat(body)
        .doesNotContain("apiKeySecretRef")
        .doesNotContain("Authorization")
        .doesNotContain("workspaceRoot");

    mockMvc.perform(get("/v1/agent-runs/{runId}/events", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.eventType == 'RUN_QUEUED')]").exists())
        .andExpect(jsonPath("$.data[?(@.eventType == 'RUNNING')]").exists())
        .andExpect(jsonPath("$.data[?(@.eventType == 'COMPLETED')]").exists());

    mockMvc.perform(get("/v1/agent-runs/{runId}/artifacts", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].artifactRef").isString())
        .andExpect(jsonPath("$.data[0].absolutePath").doesNotExist());
  }

  private String createWorkspace(String name) throws Exception {
    MvcResult result = mockMvc.perform(post("/v1/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "%s",
                  "defaultBranch": "main"
                }
                """.formatted(name)))
        .andExpect(status().isOk())
        .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.workspaceId");
  }

  private MvcResult pollRun(String runId, String expectedStatus) throws Exception {
    AssertionError lastError = null;
    long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
    while (System.nanoTime() < deadline) {
      MvcResult result = mockMvc.perform(get("/v1/agent-runs/{runId}", runId))
          .andExpect(status().isOk())
          .andReturn();
      String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
      if (expectedStatus.equals(status)) {
        return result;
      }
      lastError = new AssertionError("Expected status %s but was %s".formatted(expectedStatus, status));
      Thread.sleep(150);
    }
    throw lastError == null ? new AssertionError("Run was not found") : lastError;
  }
}
```

This smoke uses the real local TS worker, but not a real external provider. It may pass on the first run if the DB-backed runtime path already works.

- [ ] **Step 2: Run focused smoke**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=JdbcRuntimeHandoffControllerTest
```

Expected:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

If it fails, diagnose whether the failure is local TS worker path, Testcontainers/MySQL wiring, artifact registration, or event persistence before editing production code.

- [ ] **Step 3: Run full verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
git diff --check
rg -n '[ \t]$|^(<<<<<<<|=======|>>>>>>>)' backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRuntimeHandoffControllerTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
rg -n 'tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}|ANTHROPIC_AUTH_TOKEN=tp-[A-Za-z0-9]{20,}' backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRuntimeHandoffControllerTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
```

Expected:

- Maven full backend suite passes.
- Root TypeScript typecheck passes.
- Static scans are clean.

- [ ] **Step 4: Update delivery report**

Append:

```markdown
## Backend Phase A Runtime DB-backed Handoff Smoke

Status: verified as a MySQL-backed Java API -> local TS worker -> durable run/event/artifact handoff smoke.

Scope delivered:

- Added `JdbcRuntimeHandoffControllerTest`.
- The smoke uses `spring.profiles.active=jdbc` and MySQL Testcontainers.
- It runs the local TS worker through the Java API and verifies run status, events, and artifact refs persisted through the backend.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - Backend suite passed; record the exact test count from Maven output.
- `npm run typecheck`
  - Root `tsc --noEmit` passed.

Evidence boundaries:

- This is not a real external provider E2E.
- This is not a deployed browser E2E.
- This is not production remote runner identity, mTLS, runner-scoped secret distribution, or multi-node fanout.
```

- [ ] **Step 5: Commit Slice 4**

Run:

```bash
git add backend/src/test/java/com/myworkflow/agent/backend/run/JdbcRuntimeHandoffControllerTest.java docs/reports/runtime-work-item-execution-resume-delivery.md
git commit -m "test: add mysql runtime handoff smoke"
```

---

## Final Phase A Exit Gate

- [ ] **Step 1: Confirm all four commits exist**

Run:

```bash
git log --oneline -4
```

Expected commit subjects:

```text
test: add mysql runtime handoff smoke
docs: add backend frontend handoff contract
test: add mysql backend phase a readiness smoke
feat: correct backend phase a capability claims
```

- [ ] **Step 2: Run final status and diff checks**

Run:

```bash
git status --short
git diff --stat HEAD~4..HEAD
```

Expected:

- `git status --short` is empty.
- Diff stat only includes Phase A files.

- [ ] **Step 3: Push**

Run:

```bash
git push
```

Expected:

```text
main -> main
```

## Self-Review Notes

- Spec coverage: all four Phase A slices from `2026-06-18-mysql-backend-phase-a-design.md` are represented.
- Placeholder scan: no unresolved marker phrases or vague follow-up steps are allowed in this plan.
- Type consistency: capability field names used in tests match the planned `IntegrationCapabilities` record.
- Evidence boundaries: every smoke distinguishes MySQL integration from deployed browser E2E and real external provider E2E.
