# Java Platform Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first Java backend platform skeleton for Phase J1: Spring Boot app, health/readiness endpoints, common API envelope, profile config, module boundaries, OpenAPI document, and Java test harness.

**Architecture:** Add a separate `backend/` Maven project so the existing TypeScript agent runtime remains untouched. Phase J1 creates only the Java control-plane skeleton; it does not call TS worker, does not create DB schema, and does not implement auth/RBAC beyond module boundaries.

**Tech Stack:** Java 17 bytecode baseline for local Phase J1 execution, Spring Boot 3.5.15, Maven, Spring Web MVC, Spring Actuator, Spring Validation, springdoc-openapi, JUnit 5, AssertJ, MockMvc.

---

## Scope Check

This plan implements only Phase J1 from `docs/architecture/java-team-backend-platform-spec.md`.

In scope:

- Maven backend project under `backend/`.
- Spring Boot application bootstrap.
- Common response envelope with schema version `java-backend-api.v1`.
- `/health` and `/ready` endpoints.
- OpenAPI document exposure through springdoc.
- Test harness proving app boot, endpoint envelopes, and OpenAPI paths.
- `.gitignore` update for Maven build output.
- Delivery report update after implementation verification.

Out of scope:

- MySQL schema.
- Flyway migration.
- Spring Security auth implementation.
- Workspace model.
- Agent run/job model.
- TS worker bridge.
- Provider credential handling.
- Remote runner.
- Real provider calls.

## File Structure

Create:

- `backend/pom.xml`
  - Maven project descriptor and dependency declaration.
- `backend/src/main/java/com/myworkflow/agent/backend/BackendApplication.java`
  - Spring Boot entry point.
- `backend/src/main/java/com/myworkflow/agent/backend/api/ApiEnvelope.java`
  - Stable API response wrapper.
- `backend/src/main/java/com/myworkflow/agent/backend/api/ApiError.java`
  - Stable API error payload.
- `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`
  - Minimal error mapping without stack traces.
- `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
  - `/health` and `/ready` endpoints.
- `backend/src/main/java/com/myworkflow/agent/backend/ops/OpenApiConfig.java`
  - OpenAPI metadata.
- `backend/src/main/resources/application.yml`
  - Application name, profile defaults, actuator exposure.
- `backend/src/test/java/com/myworkflow/agent/backend/api/ApiEnvelopeTest.java`
  - Unit test for response envelope shape.
- `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsControllerTest.java`
  - HTTP tests for `/health` and `/ready`.
- `backend/src/test/java/com/myworkflow/agent/backend/ops/OpenApiContractTest.java`
  - OpenAPI contract smoke.

Modify:

- `.gitignore`
  - Add `backend/target/`.
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Add Phase J1 delivery section after verification.

Do not modify:

- `src/runtime/*`
- `src/sdk/*`
- TypeScript SDK contracts.
- existing tests, except running them for verification.

## Environment Facts

- System `mvn` is not on PATH.
- IntelliJ bundled Maven is available at `/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`.
- Local Java is Amazon Corretto 18.0.2.
- Spring Initializr metadata lists Spring Boot `3.5.15` as the current Spring Boot 3.5 release line option.
- Phase J1 uses `<java.version>17</java.version>` so the skeleton compiles on the local JDK 18 while staying compatible with a future Java 21 runtime target.

## Stop Conditions

Stop before implementation if any of these happen:

- Maven dependency download requires network approval and the user has not approved it.
- The selected Spring Boot/springdoc versions conflict with Java 21 or the local Maven runtime.
- Implementing the plan would require changing more than the files listed above.
- The Java skeleton needs to parse `agent-backend-response.v1` or touch TS runtime code. That belongs to Phase J3, not J1.

## Task 1: Maven Skeleton And Application Bootstrap

**Files:**

- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/BackendApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Modify: `.gitignore`

- [x] **Step 1: Run RED command for missing backend project**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

Expected: FAIL because `backend/pom.xml` does not exist.

- [x] **Step 2: Add Maven project descriptor**

Create `backend/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.15</version>
    <relativePath/>
  </parent>

  <groupId>com.myworkflow.agent</groupId>
  <artifactId>my-workflow-agent-backend</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>my-workflow-agent-backend</name>
  <description>Java central backend control plane for My Workflow Agent</description>

  <properties>
    <java.version>17</java.version>
    <springdoc.version>2.8.9</springdoc.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
      <version>${springdoc.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [x] **Step 3: Add application entry point**

Create `backend/src/main/java/com/myworkflow/agent/backend/BackendApplication.java`:

```java
package com.myworkflow.agent.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
```

- [x] **Step 4: Add application config**

Create `backend/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: my-workflow-agent-backend

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info

my-workflow:
  backend:
    schema-version: java-backend-api.v1
```

- [x] **Step 5: Ignore Maven build output**

Append this line to `.gitignore` if it is not already present:

```gitignore
backend/target/
```

- [x] **Step 6: Compile the empty backend**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -DskipTests package
```

Expected: PASS and `backend/target/` exists locally but is ignored by git.

If Maven dependency download fails because of sandboxed network access, request escalation for the same Maven command before changing code.

## Task 2: Common API Envelope

**Files:**

- Create: `backend/src/main/java/com/myworkflow/agent/backend/api/ApiEnvelope.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/api/ApiError.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/api/ApiEnvelopeTest.java`

- [x] **Step 1: Write RED test for envelope helpers**

Create `backend/src/test/java/com/myworkflow/agent/backend/api/ApiEnvelopeTest.java`:

```java
package com.myworkflow.agent.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiEnvelopeTest {

  @Test
  void okCreatesStableSuccessEnvelope() {
    ApiEnvelope<String> envelope = ApiEnvelope.ok("ready");

    assertThat(envelope.schemaVersion()).isEqualTo("java-backend-api.v1");
    assertThat(envelope.ok()).isTrue();
    assertThat(envelope.data()).isEqualTo("ready");
    assertThat(envelope.error()).isNull();
  }

  @Test
  void failureCreatesStableErrorEnvelope() {
    ApiError error = new ApiError("BACKEND_ERROR", "Backend error", false);

    ApiEnvelope<Void> envelope = ApiEnvelope.failure(error);

    assertThat(envelope.schemaVersion()).isEqualTo("java-backend-api.v1");
    assertThat(envelope.ok()).isFalse();
    assertThat(envelope.data()).isNull();
    assertThat(envelope.error()).isEqualTo(error);
  }
}
```

- [x] **Step 2: Run RED test**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApiEnvelopeTest test
```

Expected: FAIL because `ApiEnvelope` and `ApiError` do not exist.

- [x] **Step 3: Add error payload**

Create `backend/src/main/java/com/myworkflow/agent/backend/api/ApiError.java`:

```java
package com.myworkflow.agent.backend.api;

public record ApiError(
    String code,
    String message,
    boolean retryable
) {
}
```

- [x] **Step 4: Add response envelope**

Create `backend/src/main/java/com/myworkflow/agent/backend/api/ApiEnvelope.java`:

```java
package com.myworkflow.agent.backend.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ApiEnvelope<T>(
    String schemaVersion,
    boolean ok,
    T data,
    ApiError error
) {
  public static final String SCHEMA_VERSION = "java-backend-api.v1";

  public static <T> ApiEnvelope<T> ok(T data) {
    return new ApiEnvelope<>(SCHEMA_VERSION, true, data, null);
  }

  public static ApiEnvelope<Void> failure(ApiError error) {
    return new ApiEnvelope<>(SCHEMA_VERSION, false, null, error);
  }
}
```

- [x] **Step 5: Run envelope tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApiEnvelopeTest test
```

Expected: PASS.

## Task 3: Health And Readiness Endpoints

**Files:**

- Create: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsControllerTest.java`

- [x] **Step 1: Write RED HTTP tests**

Create `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsControllerTest.java`:

```java
package com.myworkflow.agent.backend.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class OpsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void healthReturnsStableEnvelope() throws Exception {
    mockMvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.status").value("ok"))
        .andExpect(jsonPath("$.data.service").value("my-workflow-agent-backend"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void readyReturnsStableEnvelope() throws Exception {
    mockMvc.perform(get("/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.data.status").value("ready"))
        .andExpect(jsonPath("$.data.service").value("my-workflow-agent-backend"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }
}
```

- [x] **Step 2: Run RED endpoint tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpsControllerTest test
```

Expected: FAIL because `/health` and `/ready` are not implemented.

- [x] **Step 3: Add ops controller**

Create `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`:

```java
package com.myworkflow.agent.backend.ops;

import com.myworkflow.agent.backend.api.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

  private static final String SERVICE_NAME = "my-workflow-agent-backend";

  @GetMapping("/health")
  public ApiEnvelope<OpsStatusResponse> health() {
    return ApiEnvelope.ok(new OpsStatusResponse("ok", SERVICE_NAME));
  }

  @GetMapping("/ready")
  public ApiEnvelope<OpsStatusResponse> ready() {
    return ApiEnvelope.ok(new OpsStatusResponse("ready", SERVICE_NAME));
  }

  public record OpsStatusResponse(
      String status,
      String service
  ) {
  }
}
```

- [x] **Step 4: Run endpoint tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpsControllerTest test
```

Expected: PASS.

## Task 4: Error Envelope And OpenAPI Contract

**Files:**

- Create: `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpenApiConfig.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpenApiContractTest.java`

- [x] **Step 1: Write RED OpenAPI contract test**

Create `backend/src/test/java/com/myworkflow/agent/backend/ops/OpenApiContractTest.java`:

```java
package com.myworkflow.agent.backend.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myworkflow.agent.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
class OpenApiContractTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void openApiDocumentIncludesOpsEndpoints() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("My Workflow Agent Backend API"))
        .andExpect(jsonPath("$.info.version").value("v1"))
        .andExpect(jsonPath("$.paths['/health']").exists())
        .andExpect(jsonPath("$.paths['/ready']").exists());
  }
}
```

- [x] **Step 2: Run RED OpenAPI test**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest test
```

Expected: FAIL because OpenAPI metadata title/version are not configured.

- [x] **Step 3: Add global exception handler**

Create `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`:

```java
package com.myworkflow.agent.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(Exception exception) {
    ApiError error = new ApiError(
        "BACKEND_ERROR",
        "Unexpected backend error",
        false
    );
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiEnvelope.failure(error));
  }
}
```

- [x] **Step 4: Add OpenAPI metadata config**

Create `backend/src/main/java/com/myworkflow/agent/backend/ops/OpenApiConfig.java`:

```java
package com.myworkflow.agent.backend.ops;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI backendOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("My Workflow Agent Backend API")
            .version("v1")
            .description("Java central backend control plane for My Workflow Agent"));
  }
}
```

- [x] **Step 5: Run OpenAPI test**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest test
```

Expected: PASS.

- [x] **Step 6: Run focused Java tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

Expected: PASS.

## Task 5: Verification And Phase Archive

**Files:**

- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-platform-skeleton.md`

- [x] **Step 1: Run Java focused verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

Expected: PASS.

- [x] **Step 2: Run existing TypeScript verification**

Run:

```bash
npm test
```

Expected: PASS.

Run:

```bash
npm run typecheck
```

Expected: PASS.

- [x] **Step 3: Run diff and token scans**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.

Run:

```bash
rg -n "t[p]-[A-Za-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" backend docs src tests
```

Expected: no matches and exit code 1.

- [x] **Step 4: Update delivery report**

Append this section to `docs/reports/runtime-work-item-execution-resume-delivery.md`:

```markdown
## Phase J1 - Java Platform Skeleton

Status: implemented.

Scope:

- Added `backend/` Maven Spring Boot skeleton.
- Added common API envelope with `java-backend-api.v1`.
- Added `/health` and `/ready` endpoints.
- Added OpenAPI metadata and `/v3/api-docs` contract smoke.
- Did not add DB schema, auth, workspace model, agent worker bridge, provider integration, or remote runner.

Verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test` - PASS.
- `npm test` - PASS.
- `npm run typecheck` - PASS.
- `git diff --check` - PASS.
- `rg -n "t[p]-[A-Za-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" backend docs src tests` - no matches.

Evidence type:

- Java tests are local unit/integration tests with MockMvc.
- No real provider call was executed.
- No TS worker bridge was executed in Phase J1.
```

- [x] **Step 5: Update this plan execution status**

Append this section to this plan file:

```markdown
## Execution Status

Status: implemented.

Completed tasks:

- Task 1: Maven skeleton and application bootstrap.
- Task 2: common API envelope.
- Task 3: health/readiness endpoints.
- Task 4: OpenAPI contract.
- Task 5: verification and phase archive.
- Reviewer follow-up: OpenAPI response schema, framework-level error envelopes, actuator public surface, and untracked-file whitespace verification.

Verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test` - PASS.
- `npm test` - PASS.
- `npm run typecheck` - PASS.
- `git diff --check` - PASS.
- token pattern scan - no matches.
```

## Execution Status

Status: implemented.

Completed tasks:

- Task 1: Maven skeleton and application bootstrap.
- Task 2: common API envelope.
- Task 3: health/readiness endpoints.
- Task 4: OpenAPI contract.
- Task 5: verification and phase archive.

RED evidence:

- Missing `backend/pom.xml` failed as expected before skeleton creation.
- `ApiEnvelopeTest` failed as expected before `ApiEnvelope` / `ApiError` existed.
- `OpsControllerTest` failed as expected with 404 before `/health` and `/ready` existed.
- `OpenApiContractTest` failed as expected before fixed OpenAPI title/version config.
- Reviewer follow-up `OpenApiContractTest,ApiErrorEnvelopeTest` failed as expected before:
  - 404/405 were mapped to 500 `INTERNAL_ERROR`;
  - unexpected exception had `retryable=true`;
  - OpenAPI response schema used `*/*` instead of stable `application/json`.
- Reviewer follow-up `OpsControllerTest` failed as expected before actuator web exposure was disabled: `/actuator/health` returned raw actuator JSON.

Verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -DskipTests package` - PASS.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ApiEnvelopeTest test` - PASS, 2 tests.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpsControllerTest test` - PASS, 3 tests.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest test` - PASS, 1 test.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=OpenApiContractTest,ApiErrorEnvelopeTest test` - PASS, 4 tests.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test` - PASS, 9 tests.
- `npm test` - PASS, 43 test files / 177 tests.
- `npm run typecheck` - PASS.
- `git diff --check` - PASS for tracked diffs.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-platform-skeleton.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'` - no whitespace/conflict-marker matches in untracked Phase J1 files.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests` - no token-like matches.

Evidence boundaries:

- Java endpoint and OpenAPI evidence is local MockMvc integration evidence.
- No real provider call was executed.
- No TypeScript worker bridge was executed.
- Actuator web endpoints are not exposed in Phase J1; `/health` and `/ready` are the public ops endpoints.

## Implementation Notes

- Execute this plan only after approving Java dependency downloads and the creation of a new `backend/` project.
- Do not add MySQL, Flyway, Spring Security auth rules, workspace tables, or worker bridge code in Phase J1.
- Do not call `runBackendAgent()` in Phase J1. That belongs to Phase J3.
- Do not describe MockMvc tests as real provider or real TS worker evidence.
- Keep Java public API responses free of local absolute paths and provider tokens.

## Self-Review

Spec coverage:

- Phase J1 skeleton: Task 1.
- API envelope: Task 2.
- health/readiness: Task 3.
- OpenAPI: Task 4.
- verification/archive: Task 5.
- DB/auth/workspace/worker bridge are explicitly out of scope.

Open-item scan:

- No unresolved implementation holes are intentionally left in this plan.

Type consistency:

- Java package root is consistently `com.myworkflow.agent.backend`.
- API schema version is consistently `java-backend-api.v1`.
- Service name is consistently `my-workflow-agent-backend`.
