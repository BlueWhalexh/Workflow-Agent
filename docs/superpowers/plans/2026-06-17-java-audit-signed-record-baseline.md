# Java Audit Signed Record Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist audit record integrity metadata at append time so list/export clients do not depend on controller-only digest calculation.

**Architecture:** Keep audit events append-only. Move canonical record digest generation into the audit repository write path, add a per-workspace hash-chain baseline, and expose the persisted integrity metadata through the existing owner-only audit list/export surfaces.

**Tech Stack:** Spring Boot MVC, existing audit repository contract, in-memory repository, JDBC repository, Flyway migration, JUnit 5, MockMvc.

---

## Scope

In scope:

- Persist `recordDigest` for newly appended audit events.
- Persist `previousRecordDigest` and `chainDigest` as a per-workspace append-order integrity chain.
- Persist `signatureKind = "sha256-chain-v1"` and `signatureValue = chainDigest` as a local integrity signature baseline.
- Add a Flyway migration for JDBC storage.
- Expose persisted integrity fields in existing audit list/export responses.
- Preserve owner-only access and existing query/filter behavior.

Out of scope:

- External KMS, private-key signatures, non-repudiation, key rotation, or secret manager integration.
- Rewriting historical audit rows.
- Destructive audit mutation or retention execution.
- Public audit write API.
- Multi-node transactional chain locking beyond the current JDBC baseline.

## Files

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditEventRecord.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditRecordIntegrity.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/InMemoryAuditRepository.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/JdbcAuditRepository.java`
- Create: `backend/src/main/resources/db/migration/V10__audit_event_integrity_baseline.sql`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditRepositoryContract.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/JdbcAuditRepositoryTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

## Tasks

### Task 1: RED repository/controller contract

- [x] Extend audit repository contract tests to require persisted `recordDigest`, `previousRecordDigest`, `chainDigest`, `signatureKind`, and `signatureValue`.
- [x] Extend controller list/export tests to assert persisted integrity fields are present and redacted.
- [x] Run `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest,AuditControllerTest test`.
- [x] Expected RED: missing record fields/methods or missing response fields.

### Task 2: GREEN implementation

- [x] Add integrity fields to `AuditEventRecord`.
- [x] Add `AuditRecordIntegrity` canonical digest and chain helper.
- [x] Compute and persist integrity metadata in in-memory append path.
- [x] Add JDBC migration and persist/map integrity metadata in JDBC append/query paths.
- [x] Return persisted integrity metadata from audit list/export without recalculating in controller.
- [x] Run focused tests until green.

### Task 3: Docs and verification

- [x] Update architecture spec status and audit sections with J28A.
- [x] Update phase-one audit status and remaining gaps.
- [x] Update delivery report with RED/GREEN/full/static evidence.
- [x] Mark this plan complete.
- [x] Run verification:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=InMemoryAuditRepositoryContractTest,JdbcAuditRepositoryTest,AuditControllerTest test`
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - `npm test`
  - `npm run typecheck`
  - `git diff --check`
  - token redaction scan
  - unchecked phase plan scan

## Review Focus

- Integrity semantics: J28A is a local SHA-256 hash-chain baseline, not KMS-backed non-repudiation.
- Data model safety: migration only adds nullable columns; no destructive audit mutation.
- Permission boundary: integrity metadata remains exposed only through existing owner-only audit list/export.
- Ordering boundary: current chain follows repository append behavior for a workspace; multi-node locking is a future hardening item.
