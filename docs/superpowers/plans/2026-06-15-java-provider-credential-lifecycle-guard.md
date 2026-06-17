# Java Provider Credential Lifecycle Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an owner-only provider credential disable lifecycle guard so disabled workspace credential refs keep public metadata/audit history but cannot be resolved for runs.

**Architecture:** Keep the Java backend as the control plane and the provider credential table as metadata only. The new endpoint updates workspace-scoped credential status to `DISABLED`, returns redacted public metadata, and relies on the existing `findActiveByScope` run path to prevent disabled credentials from reaching the worker.

**Tech Stack:** Spring Boot MVC, Spring JDBC, Flyway-managed MySQL schema, JUnit 5, AssertJ, MockMvc, Testcontainers.

---

## Scope

In scope:

- Public API: `POST /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable`
- Owner-only workspace guard.
- Workspace-scoped metadata only; no physical delete.
- Disabled credential metadata remains visible in owner list as `DISABLED`.
- Disabled credential refs cannot create agent runs.
- Audit event `PROVIDER_CREDENTIAL_DISABLED` with no env name, secret ref, token, or authorization material.
- Report/spec updates and verification evidence.

Out of scope:

- Secret manager lookup or injection.
- Raw provider token validation.
- Credential rotation.
- Team-scoped public credential lifecycle.
- HTTP/UI/backend controller beyond this endpoint.
- Physical deletion or purge.

## Files

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialController.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/ProviderCredentialRunControllerTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

## Tasks

### Task 1: RED controller/API lifecycle behavior

- [x] Add MockMvc coverage proving owner can disable a workspace credential, response/list are public metadata only, audit is redacted, OpenAPI includes the endpoint, and viewer cannot disable.
- [x] Run `mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest test`.
- [x] Expected RED: missing disable endpoint/service method, 404, or compile failure for the new API.

### Task 2: RED service/repository/run guard behavior

- [x] Add service coverage proving owner disable returns `DISABLED`, preserves list visibility, rejects non-owner before save/update, and records `PROVIDER_CREDENTIAL_DISABLED` without secret material.
- [x] Add repository coverage proving `disableWorkspaceCredential` updates only the workspace-scoped row and `findActiveByScope` no longer resolves it.
- [x] Add run controller coverage proving `providerRuntimeRef = "credential.<disabled-ref>"` fails before worker invocation.
- [x] Run `mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialRunControllerTest test`.
- [x] Expected RED: missing repository/service methods or disabled run still resolving.

### Task 3: GREEN implementation

- [x] Add `ProviderCredentialRepository.disableWorkspaceCredential(teamId, workspaceId, credentialRef)` using an `UPDATE ... WHERE team_id = ? AND workspace_id = ? AND credential_ref = ?`, then re-read via `listByWorkspaceScope`.
- [x] Add `ProviderCredentialService.disableWorkspaceCredential(workspaceId, credentialRef)` with owner guard, credential ref validation, missing-row validation, redacted audit, and public metadata response.
- [x] Add controller `@PostMapping("/{credentialRef}/disable")`.
- [x] Keep public metadata fields unchanged and do not expose `apiKeyEnvName` or `apiKeySecretRef`.
- [x] Run the focused tests until green.

### Task 4: Docs and evidence

- [x] Update architecture spec status/providersecret notes with J26A.
- [x] Update phase-one audit recommended order and evidence status.
- [x] Update delivery report with RED/GREEN/full/static evidence.
- [x] Run verification:
  - `mvn -f backend/pom.xml -Dtest=ProviderCredentialControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialRunControllerTest test`
  - `mvn -f backend/pom.xml test`
  - `npm test`
  - `npm run typecheck`
  - `git diff --check`
  - token redaction scan
  - unchecked phase plan scan

## Review Focus

- Public API semantics: disable means lifecycle state update, not delete.
- Security boundary: no raw secret/env/secret ref leaks in response, audit, test fixtures, or docs.
- Run boundary: disabled refs cannot be converted into worker provider runtime.
- Workspace boundary: only owner can disable workspace-scoped credentials in their workspace.
