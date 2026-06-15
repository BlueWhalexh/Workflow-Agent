# Java Workspace JDBC Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase J2B by adding DB-backed identity/workspace runtime wiring, Flyway migration execution against MySQL, and a JDBC `WorkspaceRepository` that preserves the J2A public API and path/security boundaries.

**Architecture:** Keep `WorkspaceRepository` as the port and add a JDBC implementation behind a Spring profile so tests can exercise the same `WorkspaceService` through MySQL-backed persistence. The in-memory repository remains the default local fallback until DB configuration is explicitly enabled; MySQL/Testcontainers evidence is required before full J2 can be considered done.

**Tech Stack:** Java 17 bytecode baseline, Spring Boot 3.5.15, Spring JDBC, Flyway, MySQL Connector/J, Testcontainers MySQL, JUnit 5, AssertJ, MockMvc.

---

## Scope Check

In scope:

- Add JDBC/Flyway/MySQL/Testcontainers dependencies.
- Add a `jdbc` profile `WorkspaceRepository` implementation.
- Add `WorkspaceRepositoryContractTest` that can run against both in-memory and JDBC implementations.
- Add `JdbcWorkspaceRepositoryTest` using Testcontainers MySQL and Flyway migrations.
- Add an HTTP smoke test using the JDBC profile and Testcontainers MySQL.
- Keep public API unchanged:
  - `GET /v1/me`
  - `POST /v1/workspaces`
  - `GET /v1/workspaces`
  - `GET /v1/workspaces/{workspaceId}`
- Keep path/security boundaries:
  - no public absolute path input;
  - no `workspaceRoot` in response;
  - unknown JSON fields rejected;
  - workspace permission guard preserved;
  - internal `serverStorageRef` must remain under configured data root.
- Update delivery report with real MySQL/Testcontainers evidence.

Out of scope:

- Spring Security/OIDC/RBAC filters.
- AgentRun/AgentJob.
- TS worker bridge.
- Artifact registry.
- Approval APIs.
- Remote runner.

## Environment Gate

Required:

- Docker daemon running and reachable by `docker ps`.
- Maven can resolve Spring JDBC, Flyway, MySQL Connector/J, and Testcontainers dependencies.

Stop before claiming full J2 if:

- Docker/Testcontainers cannot start MySQL.
- Flyway migration does not execute against MySQL.
- JDBC repository is only unit-tested without real MySQL.

## File Structure

Modify:

- `backend/pom.xml`
  - Add Spring JDBC, Flyway, MySQL Connector/J, and Testcontainers dependencies.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
  - Keep as default when JDBC profile is not active.
- `backend/src/main/resources/application.yml`
  - Add profile notes and leave DB disabled by default.
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Append J2B delivery evidence.
- `docs/architecture/java-team-backend-platform-spec.md`
  - Update status from J2A to full J2 when MySQL evidence passes.

Create:

- `backend/src/main/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepository.java`
  - MySQL-backed implementation of `WorkspaceRepository`.
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRepositoryContract.java`
  - Shared assertions for repository behavior.
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepositoryContractTest.java`
  - Contract test for default implementation.
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/JdbcWorkspaceRepositoryTest.java`
  - Testcontainers MySQL + Flyway + repository contract.
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceJdbcControllerTest.java`
  - MockMvc test using JDBC profile and Testcontainers MySQL.

## Task 1: Dependencies And Environment RED

- [x] **Step 1: Confirm Docker**

Run:

```bash
docker ps
```

Expected: PASS. If it fails, do not claim MySQL/Testcontainers verification.

- [x] **Step 2: Write RED JDBC repository test**

Create `JdbcWorkspaceRepositoryTest` that references `JdbcWorkspaceRepository`, starts MySQL Testcontainers, runs Flyway migrations, and calls the shared repository contract.

- [x] **Step 3: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest test
```

Expected: FAIL because dependencies/classes do not exist.

- [x] **Step 4: Add dependencies**

Add:

- `spring-boot-starter-jdbc`
- `flyway-core`
- `flyway-mysql`
- `mysql:mysql-connector-j`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:mysql`

- [x] **Step 5: Re-run RED**

Expected: compile moves forward but still fails because `JdbcWorkspaceRepository` does not exist.

## Task 2: Repository Contract

- [x] **Step 1: Extract shared contract**

Create `WorkspaceRepositoryContract` with assertions:

- save then find by id returns the same workspace metadata;
- `findVisibleTo(ownerUserId, teamId)` includes the workspace;
- `findVisibleTo(otherUserId, otherTeamId)` excludes the workspace;
- `canAccess` is true for owner and false for non-member.

- [x] **Step 2: Add in-memory contract test**

Create `InMemoryWorkspaceRepositoryContractTest` and run it against existing implementation.

- [x] **Step 3: Verify in-memory contract**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest test
```

Expected: PASS.

## Task 3: JDBC Repository RED/GREEN

- [x] **Step 1: Implement `JdbcWorkspaceRepository` minimally**

Use `JdbcTemplate` and explicit SQL. Preserve `WorkspaceRecord` fields and membership checks.

- [x] **Step 2: Run JDBC repository test**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest test
```

Expected: PASS only if MySQL Testcontainers starts and Flyway applies `V2__identity_workspace_baseline.sql`.

## Task 4: JDBC HTTP Smoke

- [x] **Step 1: Write JDBC controller smoke**

Create `WorkspaceJdbcControllerTest` with JDBC profile/Testcontainers. Verify:

- `POST /v1/workspaces` persists in MySQL;
- `GET /v1/workspaces` sees persisted data;
- response does not expose server path;
- `workspaceRoot` unknown input is rejected.

- [x] **Step 2: Run smoke**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceJdbcControllerTest test
```

Expected: PASS.

## Task 5: Verification And Archive

- [x] **Step 1: Focused DB verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest,WorkspaceJdbcControllerTest test
```

Expected: PASS with real Testcontainers MySQL.

- [x] **Step 2: Java backend verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

Expected: PASS.

- [x] **Step 3: Repository verification**

Run:

```bash
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests
```

Expected: all pass/no matches.

- [x] **Step 4: Archive**

Update delivery report with:

- dependency/runtime changes;
- RED/GREEN evidence;
- real MySQL/Testcontainers result;
- whether full J2 is now closed.

## Completion Boundary

J2B is complete only if Testcontainers MySQL runs, Flyway applies the migration, the JDBC repository contract passes, the JDBC HTTP smoke passes, Java backend tests pass, TS tests/typecheck pass, and scans are clean. If Docker remains unavailable, this plan can be written but not claimed complete.

## Self-Review

- Spec coverage: closes the DB-backed half of Phase J2 when MySQL evidence passes.
- Placeholder scan: no intentional placeholders.
- Public API: unchanged; J2B only adds a JDBC implementation behind `spring.profiles.active=jdbc`.
- Runtime boundary: Java still does not parse TypeScript runtime-private result shapes.
- Known warning: Flyway reported MySQL 8.4 is newer than the latest version it explicitly tested (`8.1`); migration still applied successfully in Testcontainers.

## Execution Status

Status: completed for J2B on 2026-06-14.

RED evidence:

- `mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest test`
  - Failed first because the JDBC/Testcontainers/Flyway API and repository class were not present.
  - After adding dependencies, failure narrowed to missing `JdbcWorkspaceRepository`, confirming fixture setup was not the blocker.

GREEN evidence:

- `mvn -f backend/pom.xml -Dtest=InMemoryWorkspaceRepositoryContractTest,JdbcWorkspaceRepositoryTest test`
  - PASS; repository contract passed against in-memory and Testcontainers MySQL-backed implementations.
- `mvn -f backend/pom.xml -Dtest=WorkspaceJdbcControllerTest test`
  - PASS; JDBC profile HTTP smoke persisted and listed workspace data through MySQL and rejected client-supplied `workspaceRoot`.
- `mvn -f backend/pom.xml -Dtest=JdbcWorkspaceRepositoryTest,WorkspaceJdbcControllerTest test`
  - PASS; focused DB suite used real Testcontainers MySQL containers and applied `V2__identity_workspace_baseline.sql`.
- `mvn -f backend/pom.xml test`
  - PASS; 21 Java tests.
- `npm test`
  - PASS; 43 Vitest files / 177 tests.
- `npm run typecheck`
  - PASS.
- `git diff --check`
  - PASS.
- trailing whitespace / merge-marker scan for J2B scope
  - no matches.
- token redaction scan for `tp-*` / `Bearer tp-*` / `MIMO_API_KEY=tp-*`
  - no matches.
- Type consistency: keeps `WorkspaceRepository`, `WorkspaceRecord`, `WorkspaceRole`, `WorkspaceStatus`, and API response fields unchanged from J2A.
