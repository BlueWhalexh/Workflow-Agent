# Java Workspace Identity Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Phase J2A of the Java backend: dev principal, workspace registration/list/get APIs, service-owned workspace path resolution, permission guard, and a MySQL-oriented identity/workspace schema artifact.

**Architecture:** Keep Java as the backend control plane and keep TypeScript runtime untouched. J2A uses an in-process repository behind a `WorkspaceRepository` port so API/permission/path behavior can land without pretending Docker-backed MySQL verification has passed; the MySQL Flyway migration is added as the target schema artifact and must be exercised with real MySQL/Testcontainers before full J2 is closed.

**Tech Stack:** Java 17 bytecode baseline, Spring Boot 3.5.15, Spring Web MVC, Spring Validation, JUnit 5, AssertJ, MockMvc.

---

## Scope Check

In scope for J2A:

- `GET /v1/me` returns the configured dev principal.
- `POST /v1/workspaces` registers a server-hosted workspace for the dev principal's team.
- `GET /v1/workspaces` lists only workspaces visible to the current principal.
- `GET /v1/workspaces/{workspaceId}` returns only authorized workspaces.
- Workspace responses expose `workspaceId` and metadata, never absolute server paths.
- Service path resolution rejects absolute paths and `..` traversal.
- Unauthorized workspace access returns envelope error `WORKSPACE_FORBIDDEN`.
- Missing workspace returns envelope error `WORKSPACE_NOT_FOUND`.
- A MySQL-oriented Flyway migration defines users, teams, memberships, workspaces, and workspace members.
- Delivery report records the J2A evidence and the remaining Docker/MySQL verification gap.

Out of scope for J2A:

- Spring Security/OIDC/RBAC filters.
- JDBC/Flyway runtime wiring.
- Testcontainers/MySQL verification while Docker is unavailable.
- AgentRun/AgentJob schema.
- TS worker bridge.
- Artifact/approval APIs.

## Environment Facts

- Docker daemon is not running: `docker ps` failed to connect to `/Users/didi/.docker/run/docker.sock`.
- System `mvn` is not on PATH; use `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`.
- Local Java is Amazon Corretto 18.0.2.
- J2A is not full J2 completion until the MySQL migration is validated against real MySQL or Testcontainers.

## File Structure

Create:

- `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
  - Holds dev principal and data-root settings.
- `backend/src/main/java/com/myworkflow/agent/backend/identity/BackendPrincipal.java`
  - Current principal DTO used inside backend services.
- `backend/src/main/java/com/myworkflow/agent/backend/identity/PrincipalProvider.java`
  - Port for current principal resolution.
- `backend/src/main/java/com/myworkflow/agent/backend/identity/DevPrincipalProvider.java`
  - Dev profile principal provider.
- `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`
  - `GET /v1/me`.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRecord.java`
  - Internal workspace record including server storage ref.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRole.java`
  - Workspace role enum.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceStatus.java`
  - Workspace lifecycle enum.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceRepository.java`
  - Repository port.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/InMemoryWorkspaceRepository.java`
  - J2A local implementation.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceService.java`
  - Registration, listing, permission checks, and path resolution.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceController.java`
  - `/v1/workspaces` API.
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceNotFoundException.java`
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/WorkspaceForbiddenException.java`
- `backend/src/main/java/com/myworkflow/agent/backend/workspace/InvalidWorkspacePathException.java`
- `backend/src/main/resources/db/migration/V2__identity_workspace_baseline.sql`
  - MySQL-oriented schema artifact.
- `backend/src/test/java/com/myworkflow/agent/backend/identity/IdentityControllerTest.java`
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceControllerTest.java`
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceServiceTest.java`
- `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceSchemaArtifactTest.java`

Modify:

- `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`
  - Add workspace exception envelope mapping.
- `backend/src/main/resources/application.yml`
  - Add dev principal and data-root defaults.
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
  - Append J2A delivery evidence.
- `docs/superpowers/plans/2026-06-14-java-workspace-identity-baseline.md`
  - Mark execution status.

Do not modify:

- `src/runtime/*`
- `src/sdk/*`
- Existing TypeScript SDK/backend adapter contracts.

## Task 1: Identity API RED/GREEN

- [x] **Step 1: Write RED test**

Create `backend/src/test/java/com/myworkflow/agent/backend/identity/IdentityControllerTest.java` asserting `GET /v1/me` returns envelope `java-backend-api.v1` and data fields `userId`, `teamId`, `displayName`.

- [x] **Step 2: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest test
```

Expected: FAIL because `/v1/me` does not exist.

- [x] **Step 3: Implement minimal identity classes and controller**

Add `BackendProperties`, `BackendPrincipal`, `PrincipalProvider`, `DevPrincipalProvider`, and `IdentityController`.

- [x] **Step 4: Run GREEN**

Run the same `IdentityControllerTest`; expected PASS.

## Task 2: Workspace API RED/GREEN

- [x] **Step 1: Write RED controller test**

Create `WorkspaceControllerTest` asserting:

- `POST /v1/workspaces` accepts `name` and optional `defaultBranch`, returns `workspaceId`, `name`, `defaultBranch`, `status`, and no `workspaceRoot` / `serverStorageRef`.
- `GET /v1/workspaces` lists the created workspace.
- `GET /v1/workspaces/{workspaceId}` returns the created workspace.
- `GET /v1/workspaces/ws_missing` returns `WORKSPACE_NOT_FOUND` in the common envelope.

- [x] **Step 2: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceControllerTest test
```

Expected: FAIL because workspace API does not exist.

- [x] **Step 3: Implement workspace model, repository, service, and controller**

Add the workspace files listed above. The service must create workspace content directories under `my-workflow.backend.data-root`, never from request-provided paths.

- [x] **Step 4: Run GREEN**

Run `WorkspaceControllerTest`; expected PASS.

## Task 3: Permission And Path Guard RED/GREEN

- [x] **Step 1: Write RED service test**

Create `WorkspaceServiceTest` asserting:

- Resolving `notes/a.md` returns a path under the workspace content root.
- Resolving `../outside.md` throws `InvalidWorkspacePathException`.
- Resolving an absolute path throws `InvalidWorkspacePathException`.
- A principal from another team cannot access the workspace and gets `WorkspaceForbiddenException`.

- [x] **Step 2: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceServiceTest test
```

Expected: FAIL before `WorkspaceService` exists.

- [x] **Step 3: Implement guard behavior**

Implement normalization with `Path.normalize()`, reject absolute input, reject resolved paths that do not start with the workspace content root, and enforce repository membership before path resolution.

- [x] **Step 4: Run GREEN**

Run `WorkspaceServiceTest`; expected PASS.

## Task 4: Schema Artifact RED/GREEN

- [x] **Step 1: Write RED schema artifact test**

Create `WorkspaceSchemaArtifactTest` that reads `backend/src/main/resources/db/migration/V2__identity_workspace_baseline.sql` and asserts it contains `users`, `teams`, `team_memberships`, `workspaces`, `workspace_members`, foreign keys, and indexes for team/workspace access.

- [x] **Step 2: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceSchemaArtifactTest test
```

Expected: FAIL because migration file does not exist.

- [x] **Step 3: Add migration file**

Add MySQL-oriented DDL for the identity/workspace baseline. Do not claim it is MySQL-verified until Docker/Testcontainers is available.

- [x] **Step 4: Run GREEN**

Run `WorkspaceSchemaArtifactTest`; expected PASS.

## Task 5: Verification And Archive

- [x] **Step 1: Focused Java verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest,WorkspaceControllerTest,WorkspaceServiceTest,WorkspaceSchemaArtifactTest test
```

Expected: PASS.

- [x] **Step 2: Backend Java verification**

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
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-identity-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests
```

Expected: tests/typecheck/diff pass; scans return no matches.

- [x] **Step 4: Update delivery report**

Append J2A status, RED/GREEN evidence, Docker/MySQL gap, and review focus to `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Completion Boundary

J2A is complete when the API, permission/path guards, schema artifact, tests, scans, report, and review are done. Full Phase J2 remains incomplete until the identity/workspace schema is executed against real MySQL/Testcontainers and a DB-backed repository replaces or complements the in-memory repository.

## Execution Status

Status: implemented for J2A; full J2 remains incomplete because Docker/Testcontainers-backed MySQL migration execution was unavailable.

Completed tasks:

- Task 1: dev principal and `GET /v1/me`.
- Task 2: workspace registration/list/get API using opaque `workspaceId`.
- Task 3: service path guard and workspace permission guard.
- Task 4: MySQL-oriented schema artifact for identity/workspace baseline.
- Task 5: focused and broad verification.

RED evidence:

- `IdentityControllerTest` failed with 404 before `/v1/me` existed.
- `WorkspaceControllerTest` failed with 404 before `/v1/workspaces` existed.
- `WorkspaceSchemaArtifactTest` failed with `NoSuchFileException` before `V2__identity_workspace_baseline.sql` existed.
- Additional API boundary RED: `WorkspaceControllerTest.workspaceRegistrationRejectsClientSuppliedWorkspaceRoot` failed because unknown `workspaceRoot` was ignored and returned 200 before unknown JSON fields were rejected.
- Internal path isolation RED: `WorkspaceServiceTest.rejectsRepositoryStorageRefsOutsideDataRoot` failed because a malicious repository `serverStorageRef` could resolve outside `dataRoot` before `contentRoot` enforced the data-root boundary.

Coverage note:

- Initial `WorkspaceServiceTest` cases were added after the initial service implementation and therefore are post-implementation guard coverage, not strict RED evidence. The later malicious `serverStorageRef` case is valid RED evidence. Together they verify safe relative path resolution, traversal rejection, absolute path rejection, cross-principal access denial, and internal storage-ref boundary checks.

Verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest test` - PASS, 1 test.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceControllerTest test` - PASS, 3 tests.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceServiceTest test` - PASS, 4 tests.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=WorkspaceSchemaArtifactTest test` - PASS, 1 test.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest,WorkspaceControllerTest,WorkspaceServiceTest,WorkspaceSchemaArtifactTest test` - PASS, 9 tests.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test` - PASS, 18 tests.
- `npm test` - PASS, 43 test files / 177 tests.
- `npm run typecheck` - PASS.
- `git diff --check` - PASS for tracked diffs.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend .gitignore docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-workspace-identity-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'` - no matches.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests` - no matches.

Known gaps:

- Docker daemon was unavailable, so the migration has not been executed against real MySQL/Testcontainers.
- The runtime repository is an in-memory J2A implementation behind `WorkspaceRepository`; full J2 still needs JDBC/Flyway wiring and MySQL-backed integration tests.
- This phase does not implement Spring Security/OIDC/RBAC, agent run/job, TS worker bridge, artifact registry, or approval APIs.

## Self-Review

- Spec coverage: J2A covers dev principal, workspace registration, server path resolution, workspace permission guard, and path traversal tests. It does not close DB-backed schema execution.
- Placeholder scan: no TBD/TODO/fill-in placeholders are intentionally left.
- Type consistency: `workspaceId`, `teamId`, `userId`, `serverStorageRef`, `java-backend-api.v1`, and exception codes are consistent across tasks.
