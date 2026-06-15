# Java Team RBAC And Audit Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J7A only; do not implement full OIDC login, user/team management UI, external IAM sync, per-provider secret policy, remote runner permissions, or SSE/WebSocket authorization.

**Goal:** Add a minimal Spring Security-backed dev principal, workspace role authorization, and append-only audit baseline so team members can have distinct read/write/approval permissions across workspaces.

**Architecture:** Spring Security stores the current backend principal in `SecurityContext`; local development still supports the configured dev principal and opt-in dev headers for tests/local callers. Workspace membership remains the source of role truth. Services enforce role requirements before sensitive actions and append audit metadata after successful sensitive operations.

**Tech Stack:** Spring Boot Security, Spring MVC, Spring JDBC, Flyway-backed MySQL schema, Testcontainers MySQL, JUnit 5, MockMvc.

---

## Scope Check

In scope:

- Add Spring Security dependency and a stateless local-dev security config.
- Add an OIDC-ready principal boundary by reading `BackendPrincipal` from `SecurityContext`.
- Support local dev/test principal headers:
  - `X-Dev-User-Id`;
  - `X-Dev-Team-Id`;
  - `X-Dev-Display-Name`.
- Keep configured dev principal as fallback when no dev headers are supplied.
- Add role lookup/grant repository methods for `WORKSPACE_OWNER`, `WORKSPACE_EDITOR`, and `WORKSPACE_VIEWER`.
- Enforce workspace roles:
  - viewer or higher can read workspace/run/artifact/approval/event data;
  - editor or owner can start/cancel runs;
  - editor or owner can decide approvals.
- Add append-only audit events for:
  - workspace creation;
  - agent run requested;
  - run cancellation;
  - approval decision;
  - artifact read.
- Add in-memory/JDBC audit repositories and Flyway `V7`.
- Add focused HTTP/JDBC tests proving role denial, viewer read allowance, audit actor/workspace/run metadata, and JDBC persistence.

Out of scope:

- Full OIDC/OAuth login flow.
- User/team/member management public API.
- Role inheritance across teams beyond workspace membership.
- Audit event public listing API.
- Approval publish execution.
- Remote runner authorization.
- Real external provider calls.

## Files

Expected production changes:

- Modify `backend/pom.xml` to add Spring Security starter.
- Modify identity package to add SecurityContext-backed principal support and dev-header authentication.
- Modify workspace repository/service to expose role checks and internal member grants.
- Modify run/artifact/approval services to enforce role requirements and append audit events.
- Add `backend/src/main/java/com/myworkflow/agent/backend/audit/*`.
- Add `backend/src/main/resources/db/migration/V7__audit_event_baseline.sql`.

Expected tests:

- Add `backend/src/test/java/com/myworkflow/agent/backend/security/SecurityPrincipalControllerTest.java`.
- Add `backend/src/test/java/com/myworkflow/agent/backend/workspace/WorkspaceRoleAuthorizationTest.java`.
- Add `backend/src/test/java/com/myworkflow/agent/backend/audit/JdbcAuditRepositoryTest.java`.
- Extend existing repository contract coverage for role lookup/grant where practical.

Docs to update after verification:

- `docs/architecture/java-team-backend-platform-spec.md`.
- `docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md`.
- `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Tasks

- [x] Write RED security principal controller test proving dev headers flow into `/v1/me`.
- [x] Write RED workspace role authorization test proving viewer can read but cannot start runs or decide approvals.
- [x] Write RED audit repository/JDBC test proving append-only event persistence.
- [x] Implement Spring Security config, dev header filter, and SecurityContext principal provider.
- [x] Implement workspace role lookup/grant and role hierarchy checks.
- [x] Implement audit event model/repositories/service and Flyway migration.
- [x] Wire role checks and audit append calls into workspace/run/artifact/approval services.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, diff check, whitespace/merge-marker scan, and token scan.
- [x] Archive delivery evidence and remaining boundaries.

## Execution Status

Implemented for Phase J7A.

Delivered:

- Added Spring Security dev-header authentication and a `SecurityContext`-backed principal provider.
- Kept configured dev principal fallback for local calls without dev headers.
- Added workspace role lookup/grant support for in-memory and JDBC repositories.
- Enforced workspace role hierarchy in service layer:
  - viewer or higher can read workspace/run/artifact/approval data;
  - editor or owner can start/cancel runs and decide approvals.
- Added append-only audit event model, service, in-memory repository, JDBC repository, and Flyway `V7__audit_event_baseline.sql`.
- Recorded audit metadata for workspace creation, agent run request, run cancellation, approval decision, and artifact read.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=SecurityPrincipalControllerTest,WorkspaceRoleAuthorizationTest,JdbcAuditRepositoryTest test`
  - Failed first at Java test compile because `AuditEventRecord`, `AuditRepository`, `JdbcAuditRepository`, and `WorkspaceRepository.grantAccess(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=SecurityPrincipalControllerTest,WorkspaceRoleAuthorizationTest,JdbcAuditRepositoryTest test`
  - 4 tests passed.
  - Covers dev header principal override, default dev principal fallback, viewer read/write denial boundaries, editor approval decision, audit metadata, and Testcontainers MySQL audit persistence.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 51 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J7A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend src/cli/backend-agent-worker.ts docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-team-rbac-audit-baseline.md docs/superpowers/plans/2026-06-14-java-run-event-baseline.md docs/superpowers/plans/2026-06-14-java-stale-lock-recovery-baseline.md docs/superpowers/plans/2026-06-14-java-job-retry-baseline.md docs/superpowers/plans/2026-06-14-java-job-reliability-cancel-baseline.md docs/superpowers/plans/2026-06-14-java-approval-boundary-baseline.md docs/superpowers/plans/2026-06-14-java-artifact-registry-baseline.md docs/superpowers/plans/2026-06-14-java-async-agent-run-bridge.md docs/superpowers/plans/2026-06-14-java-workspace-jdbc-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J7A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J7A archive updates.

Boundaries:

- J7A is a local-dev SecurityContext and workspace-membership RBAC baseline, not full OIDC/OAuth.
- J7A does not add public user/team/member management APIs; tests seed extra members through repository fixtures.
- J7A audit events are append-only metadata, but no public audit listing API was added.
- J7A tests use fake/injected local worker behavior and local Testcontainers MySQL.
- J7A did not execute a real external provider call.
- J7A does not prove remote runner authorization, runner lease security, SSE/WebSocket authorization, or provider secret policy.

## Completion Boundary

J7A is complete only if:

- `GET /v1/me` uses the SecurityContext principal with dev-header override and default dev fallback.
- Workspace role truth comes from workspace membership, not request body fields.
- Viewers can read allowed workspace/run/artifact/approval/event data.
- Viewers cannot start runs, cancel runs, or decide approvals.
- Editors/owners can start runs and decide approvals.
- Audit events contain actor user id, team id, workspace id, event type, and run id where applicable.
- Public error responses do not leak token values, local absolute paths, stack traces, or raw provider payloads.
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.
