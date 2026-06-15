# Java Approval Boundary Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J5A only; do not implement candidate patch publishing, fixed workflow execution after approval, route execution, audit log, RBAC, or remote runner.

**Goal:** Implement the first Java approval boundary: approval requests are created from approval-required worker responses, can be listed for a run, and can be approved/rejected without causing workspace writes.

**Architecture:** Java owns approval state. `AgentRunService` creates a pending `ApprovalRequest` after validating the worker response and registering artifacts, but before marking the run complete as `WAITING_APPROVAL`. Approval decisions update DB metadata only. Publishing/execution remains a later deterministic path.

**Tech Stack:** Spring MVC, Spring JDBC, Flyway MySQL, Testcontainers MySQL, JUnit 5, MockMvc.

---

## Scope Check

In scope:

- Add `V5__approval_request_baseline.sql`.
- Add approval domain records/enums and repository port.
- Add in-memory and JDBC approval repository implementations.
- Create pending approval requests for `requiresApproval=true` worker responses.
- Include artifact ref and target workspace paths in approval metadata.
- Add:
  - `GET /v1/agent-runs/{runId}/approvals`
  - `POST /v1/agent-runs/{runId}/approvals`
- Allow decision values `APPROVED` and `REJECTED`.
- Ensure rejection does not write workspace.
- Ensure confirmation runs do not create approval requests.
- Preserve API boundary: request body must not accept provider tokens, workspace roots, or absolute paths.

Out of scope:

- Applying candidate patches.
- Executing fixed workflow after route preview approval.
- Approval role/RBAC enforcement beyond existing dev principal/workspace guard.
- Audit event table.
- Retry/cancel policy.
- SSE/WebSocket updates.

## Tasks

- [x] Write RED API tests for candidate patch approval request and rejection no-write behavior.
- [x] Write RED API test proving confirmation does not create approval requests.
- [x] Write RED JDBC repository test with Flyway V2+V3+V4+V5.
- [x] Implement approval domain/repository/service/controller.
- [x] Integrate approval request creation into `AgentRunService`.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, and scans.
- [x] Archive delivery evidence.

## Completion Boundary

J5A is complete only if:

- candidate patch and route-preview worker responses create pending approval requests;
- approval request includes run id, requested user, artifact ref, target workspace paths, and status;
- `POST /approvals` updates decision metadata only;
- rejection leaves `wroteWorkspace=false` and does not create target files;
- confirmation responses do not create approval requests;
- MySQL/Testcontainers applies V2+V3+V4+V5;
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.

## Self-Review

- Public API risk: new approval endpoints need review.
- State risk: approval decisions are metadata only; they do not transition to publish or execute.
- Workspace safety: approval request `targetWorkspacePaths` are proposed targets, not write evidence.
- Security risk: no token values or raw provider payloads are accepted by approval APIs.

## Execution Status

Status: completed for J5A on 2026-06-14.

RED evidence:

- `mvn -f backend/pom.xml -Dtest=ApprovalControllerTest,JdbcApprovalRepositoryTest test`
  - Failed first at Java test compile because `ApprovalRepository`, `JdbcApprovalRepository`, `ApprovalRequestRecord`, `ApprovalStatus`, and `ApprovalDecision` did not exist.

GREEN evidence:

- `mvn -f backend/pom.xml -Dtest=ApprovalControllerTest,JdbcApprovalRepositoryTest test`
  - PASS; 4 tests.
  - Covers candidate patch pending approval creation, rejection no-write behavior, confirmation no approval request, unknown `workspaceRoot` rejection, and Testcontainers MySQL V2+V3+V4+V5 migration.
- `mvn -f backend/pom.xml test`
  - PASS; 33 Java tests.
- `npm test`
  - PASS; 44 Vitest files / 178 tests.
- `npm run typecheck`
  - PASS.
- `git diff --check`
  - PASS after archive update.
- J5 whitespace / merge-marker scan
  - no matches after archive update.
- token redaction scan for `tp-*` / `Bearer tp-*` / `MIMO_API_KEY=tp-*`
  - no matches after archive update.

Evidence boundaries:

- Approval API tests use fake local worker output only.
- Approval decisions update metadata only; they do not publish candidate patches or execute fixed workflow routes.
- No real external provider call was executed for J5A.
