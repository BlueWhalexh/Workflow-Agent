# Java Audit Retention Policy Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only, owner-visible audit retention policy baseline without destructive purge behavior.

**Architecture:** Keep audit events append-only. Add backend configuration for retention metadata and expose it through an owner-only audit endpoint so clients can understand the effective policy while the backend still performs no deletion.

**Tech Stack:** Spring Boot MVC, existing `BackendProperties`, existing `WorkspaceService` owner guard, JUnit 5, MockMvc.

---

## Scope

In scope:

- Public API: `GET /v1/workspaces/{workspaceId}/audit-events/retention-policy`
- Owner-only workspace guard.
- Configurable `retentionDays` metadata with default value.
- Fixed report-only mode for J27A.
- Public response explicitly states destructive purge is disabled.
- Existing audit list/export behavior remains unchanged.

Out of scope:

- Destructive purge.
- Async retention execution.
- Object storage export-before-purge.
- Persisted signed audit records or hash chain.
- External KMS/secret manager.
- WebSocket/multi-node fanout.

## Files

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditRetentionPolicyService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

## Tasks

### Task 1: RED controller/API retention policy behavior

- [x] Add MockMvc coverage proving owner can read retention policy metadata and viewer is forbidden.
- [x] Assert response has `workspaceId`, `retentionDays`, `mode = "REPORT_ONLY"`, `destructivePurgeEnabled = false`, and no internal paths or secret/provider payload.
- [x] Assert existing audit list still returns events after policy read.
- [x] Run `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`.
- [x] Expected RED: endpoint returns 404 or code does not compile because retention service/API is missing.

### Task 2: GREEN implementation

- [x] Add `BackendProperties.AuditRetention` and default constructor wiring for `my-workflow.backend.audit.retention-days`.
- [x] Add `AuditRetentionPolicyService.retentionPolicyForWorkspace(workspaceId)` with `WORKSPACE_OWNER` guard.
- [x] Add `GET /v1/workspaces/{workspaceId}/audit-events/retention-policy` to `AuditController`.
- [x] Keep J27A report-only: no repository delete method, no purge scheduler, no audit event mutation.
- [x] Run focused test until green.

### Task 3: Docs and verification

- [x] Update architecture spec status and audit sections with J27A.
- [x] Update phase-one audit status and next recommended phase.
- [x] Update delivery report with RED/GREEN/full/static evidence.
- [x] Mark this plan complete.
- [x] Run verification:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test`
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - `npm test`
  - `npm run typecheck`
  - `git diff --check`
  - token redaction scan
  - unchecked phase plan scan

## Review Focus

- Public API semantics: read-only policy metadata, not retention execution.
- Safety boundary: no destructive purge and no mutation of existing audit records.
- Permission boundary: owner-only visibility matches audit list/export.
- Config boundary: invalid retention days fail fast; default is stable for local/dev.
