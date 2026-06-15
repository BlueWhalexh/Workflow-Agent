# Java Audit Pagination Filtering Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add owner-only audit event pagination and filtering baseline for workspace audit listing without changing the existing `java-backend-api.v1` envelope or exposing internal paths/token material.

**Architecture:** Keep `GET /v1/workspaces/{workspaceId}/audit-events` returning `data: AuditEventResponse[]`, and add conservative query parameters: `limit`, `offset`, `eventType`, and `runId`. `AuditQueryService` keeps the existing workspace owner guard, `AuditEventQuery` normalizes/validates query input, and both in-memory/JDBC repositories share the same filtering contract.

**Tech Stack:** Java 17 bytecode target, Spring Boot MVC, Spring JDBC, JUnit 5, AssertJ, MockMvc, Testcontainers MySQL.

---

## File Structure

- Modify `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`
  - Add HTTP RED/GREEN coverage for `limit`, `offset`, `eventType`, `runId`, validation, owner guard, and secrecy.
- Create `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditRepositoryContract.java`
  - Shared contract for filtering/pagination order across repository implementations.
- Create `backend/src/test/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepositoryContractTest.java`
  - Runs the new contract against `InMemoryAuditRepository`.
- Modify `backend/src/test/java/com/myworkflow/agent/backend/audit/JdbcAuditRepositoryTest.java`
  - Runs the same contract against Testcontainers MySQL.
- Create `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditEventQuery.java`
  - Validates `limit` 1..100, `offset >= 0`, trims optional filters.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditRepository.java`
  - Add `findByWorkspaceId(workspaceId, query)` while keeping existing no-query default.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepository.java`
  - Apply eventType/runId filters, stable sort, offset, and limit.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/audit/JdbcAuditRepository.java`
  - Add bounded SQL query with optional filters and `LIMIT/OFFSET`.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditQueryService.java`
  - Accept `AuditEventQuery` and keep `WORKSPACE_OWNER` guard.
- Modify `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`
  - Parse query params and pass an `AuditEventQuery`.
- Modify `docs/architecture/java-team-backend-platform-spec.md`
  - Archive J16A and remove audit pagination/filtering from remaining gaps.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Append RED/GREEN/full verification evidence.

## Task 1: RED HTTP Query Test

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`

- [x] **Step 1: Add failing HTTP query coverage**

Add an `AuditRepository` field and a new test:

```java
@Autowired
private AuditRepository auditRepository;

@Test
void ownerFiltersAndPagesWorkspaceAuditEvents() throws Exception {
  String workspaceId = createWorkspaceAs("owner-audit", "Owner Audit");
  grantViewer(workspaceId);
  auditRepository.append(
      "owner-audit",
      "team-audit-listing",
      workspaceId,
      "run-audit-page-1",
      "ARTIFACT_READ",
      "Artifact read",
      java.time.Instant.parse("2030-01-01T00:00:00Z")
  );
  auditRepository.append(
      "owner-audit",
      "team-audit-listing",
      workspaceId,
      "run-audit-page-2",
      "APPROVAL_DECIDED",
      "Approval decided",
      java.time.Instant.parse("2030-01-01T00:00:01Z")
  );

  MvcResult filtered = mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
          .headers(devHeaders("owner-audit", "Owner Audit"))
          .queryParam("eventType", "ARTIFACT_READ")
          .queryParam("limit", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(1))
      .andExpect(jsonPath("$.data[0].eventType").value("ARTIFACT_READ"))
      .andExpect(jsonPath("$.data[0].runId").value("run-audit-page-1"))
      .andReturn();
  assertThat(filtered.getResponse().getContentAsString())
      .doesNotContain("workspaceRoot")
      .doesNotContain("serverStorageRef")
      .doesNotContain("Authorization")
      .doesNotContain("rawProvider")
      .doesNotContain("token");

  mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
          .headers(devHeaders("owner-audit", "Owner Audit"))
          .queryParam("runId", "run-audit-page-2"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(1))
      .andExpect(jsonPath("$.data[0].eventType").value("APPROVAL_DECIDED"));

  mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
          .headers(devHeaders("owner-audit", "Owner Audit"))
          .queryParam("limit", "2")
          .queryParam("offset", "1"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()").value(2))
      .andExpect(jsonPath("$.data[0].eventType").value("WORKSPACE_MEMBER_GRANTED"));

  mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
          .headers(devHeaders("owner-audit", "Owner Audit"))
          .queryParam("limit", "0"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

  mockMvc.perform(get("/v1/workspaces/{workspaceId}/audit-events", workspaceId)
          .headers(devHeaders("viewer-audit", "Viewer Audit"))
          .queryParam("eventType", "ARTIFACT_READ"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.error.code").value("WORKSPACE_FORBIDDEN"));
}
```

- [x] **Step 2: Run RED command**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: FAIL because current controller/repository ignores query params; the eventType-filtered request returns more than one event or the wrong first event instead of only `ARTIFACT_READ`.

## Task 2: RED Repository Contract

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditRepositoryContract.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepositoryContractTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/JdbcAuditRepositoryTest.java`

- [x] **Step 1: Add shared repository contract**

Create `AuditRepositoryContract` that appends four records, then asserts:

```java
assertThat(repository.findByWorkspaceId(workspaceId, new AuditEventQuery(2, 1, null, null)))
    .extracting(AuditEventRecord::eventType)
    .containsExactly("WORKSPACE_MEMBER_GRANTED", "ARTIFACT_READ");
assertThat(repository.findByWorkspaceId(workspaceId, new AuditEventQuery(10, 0, "ARTIFACT_READ", null)))
    .extracting(AuditEventRecord::runId)
    .containsExactly("run_contract_1");
assertThat(repository.findByWorkspaceId(workspaceId, new AuditEventQuery(10, 0, null, "run_contract_2")))
    .extracting(AuditEventRecord::eventType)
    .containsExactly("APPROVAL_DECIDED");
```

- [x] **Step 2: Wire in-memory and JDBC tests**

Add `InMemoryAuditRepositoryContractTest` that calls the contract with `new InMemoryAuditRepository()`. In `JdbcAuditRepositoryTest`, call the contract after Flyway migration and prerequisite workspace/run setup.

- [x] **Step 3: Run RED command**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest test
```

Expected: FAIL at Java compilation because `AuditEventQuery` and `AuditRepository.findByWorkspaceId(workspaceId, query)` do not exist.

## Task 3: Minimal Implementation

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditEventQuery.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/JdbcAuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditQueryService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`

- [x] **Step 1: Add query value object**

`AuditEventQuery`:

```java
public record AuditEventQuery(int limit, int offset, String eventType, String runId) {
  public static final int DEFAULT_LIMIT = 100;
  public static final int MAX_LIMIT = 100;

  public AuditEventQuery {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Audit event limit must be between 1 and 100");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Audit event offset must be greater than or equal to 0");
    }
    eventType = normalizeOptional(eventType);
    runId = normalizeOptional(runId);
  }

  public static AuditEventQuery defaultQuery() {
    return new AuditEventQuery(DEFAULT_LIMIT, 0, null, null);
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim();
  }
}
```

- [x] **Step 2: Add repository query method**

Add:

```java
default List<AuditEventRecord> findByWorkspaceId(String workspaceId) {
  return findByWorkspaceId(workspaceId, AuditEventQuery.defaultQuery());
}

List<AuditEventRecord> findByWorkspaceId(String workspaceId, AuditEventQuery query);
```

- [x] **Step 3: Implement in-memory filtering**

Filter by workspace, optional eventType/runId, sort by `createdAt` then `auditEventId`, skip `offset`, limit `limit`.

- [x] **Step 4: Implement JDBC filtering**

Build SQL with optional `AND event_type = ?` and `AND run_id = ?`, then `ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?`.

- [x] **Step 5: Wire service/controller**

Add `AuditQueryService.listWorkspaceAuditEvents(workspaceId, query)` and controller query params:

```java
@RequestParam(defaultValue = "100") int limit,
@RequestParam(defaultValue = "0") int offset,
@RequestParam(required = false) String eventType,
@RequestParam(required = false) String runId
```

- [x] **Step 6: Run focused GREEN commands**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest,JdbcAuditRepositoryTest test
```

Expected: all focused audit tests pass.

## Task 4: Documentation And Delivery Report

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-audit-pagination-filtering-baseline.md`

- [x] **Step 1: Update platform spec**

Archive J16A. Remove audit pagination/filtering from remaining gaps while keeping audit export as future work. Document query params and that response still excludes internal path/provider/token fields.

- [x] **Step 2: Update delivery report**

Append `## Phase J16A - Java Audit Pagination Filtering Baseline` with scope, RED evidence, focused verification, full verification, and boundaries.

- [x] **Step 3: Check off completed plan steps**

Update this plan’s checkboxes as tasks complete.

## Task 5: Full Verification And Self-Review

**Files:**
- Inspect all changed files.

- [x] **Step 1: Run Java full suite**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 2: Run TypeScript regression suite**

```bash
npm test
npm run typecheck
```

- [x] **Step 3: Run static scans**

```bash
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-pagination-filtering-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

- [x] **Step 4: Self-review**

Review these points:

- Correctness: `limit`, `offset`, `eventType`, and `runId` combine correctly.
- Permission boundary: workspace owner guard still happens before audit data is returned.
- Backward compatibility: no-query endpoint still returns a list under `data`.
- Security: response does not include `workspaceRoot`, `serverStorageRef`, Authorization header, raw provider payload, or token material.
- Scope: no audit export, no public audit write API, no OIDC/OAuth, no credential DB, no SSE/WebSocket, no production remote runner.
