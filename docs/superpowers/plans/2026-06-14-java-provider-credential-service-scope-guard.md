# Java Provider Credential Service Scope Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J24C only; do not add public credential APIs, secret manager integration, KMS, key rotation, encrypted secret storage, real provider smoke, DB-backed runtime policy switching, or remote runner secret distribution.

**Goal:** Add an internal provider credential service baseline that resolves credential metadata through workspace/team authorization instead of letting future callers use the repository directly.

**Architecture:** Keep the service in `providersecret` and make it depend on `WorkspaceService` plus `ProviderCredentialRepository`. The service validates credential refs, requires the current principal to have `WORKSPACE_EDITOR` access to the workspace, then resolves ACTIVE metadata by the workspace's authoritative `teamId` and `workspaceId`.

**Tech Stack:** Java, Spring service wiring, in-memory workspace repository for focused service tests, JUnit 5, AssertJ.

---

## Files

- Create: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md`

## Scope

In scope:

- Add an internal `ProviderCredentialService`.
- Add `resolveForWorkspace(workspaceId, credentialRef)`.
- Normalize and validate credential refs before repository lookup.
- Require `WORKSPACE_EDITOR` access through `WorkspaceService`.
- Use the workspace record's authoritative `teamId` and `workspaceId` for repository lookup.
- Prove viewer and cross-team principals cannot resolve credential metadata.

Out of scope:

- Public credential CRUD API.
- Secret manager, KMS, encrypted values, key rotation, or token retrieval.
- Switching `ProviderRuntimePolicy` from config refs to DB refs.
- Injecting provider secrets into local or remote workers.
- Real provider calls.
- Remote runner secret distribution.

## Tasks

- [x] Add RED service test for credential resolution through workspace authorization.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Expected: fails because `ProviderCredentialService` does not exist yet.
- [x] Implement `ProviderCredentialService`.
  - Add `resolveForWorkspace(String workspaceId, String credentialRef)`.
  - Validate refs with `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`.
  - Require `WorkspaceRole.WORKSPACE_EDITOR`.
  - Call `ProviderCredentialRepository.findActiveByScope(workspace.teamId(), workspace.workspaceId(), normalizedRef)`.
- [x] Run focused GREEN service test.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Expected: service tests pass.
- [x] Run provider credential focused suite.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - Expected: service, repository, schema, config policy, and controller policy tests pass.
- [x] Update architecture spec and delivery report with J24C status and boundaries.
- [x] Run full verification.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - Run: `npm test`
  - Run: `npm run typecheck`
  - Run: `git diff --check`
  - Run: `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - Run: `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - Run: `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md`

## Completion Boundary

J24C is complete only if:

- The service test fails before implementation for the expected missing service reason.
- The service resolves credential metadata only after workspace editor authorization.
- The service uses the workspace's stored team/workspace scope, not caller-supplied team metadata.
- Invalid credential refs fail before repository lookup.
- No public API, secret manager, real provider call, runtime policy switch, or remote runner secret distribution is introduced.
- Architecture and delivery docs record RED, GREEN, focused/full/typecheck/static evidence and remaining boundaries.

## Execution Status

Implemented for Phase J24C.

Delivered:

- Added `ProviderCredentialService` under `providersecret`.
- Added `resolveForWorkspace(workspaceId, credentialRef)` for internal DB-backed credential metadata resolution.
- Credential refs are normalized and validated before repository lookup.
- Resolution requires `WORKSPACE_EDITOR` access through `WorkspaceService`.
- Repository lookup uses the workspace record's stored `teamId` and `workspaceId`.
- `ProviderCredentialService` is `jdbc` profile-scoped, matching `ProviderCredentialRepository`.
- No public credential API, secret manager, real provider call, DB-backed runtime policy switch, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Failed first in `testCompile`.
  - Expected failure: `ProviderCredentialService` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - 3 Java tests passed.
  - Covers editor resolution, viewer/cross-team rejection, and invalid ref rejection before repository lookup.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - Failed once before the profile fix.
  - Root cause: default profile Spring context tried to instantiate `ProviderCredentialService`, while `ProviderCredentialRepository` exists only under the `jdbc` profile.
  - Fixed by applying `@Profile("jdbc")` to `ProviderCredentialService`.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 13 Java tests passed after the profile fix.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 89 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24C archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24C archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24C archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-service-scope-guard.md`
  - no unchecked plan tasks after final archive update.

Boundary:

- J24C is an internal service scope guard baseline only. It is not public credential CRUD, DB-backed `ProviderRuntimePolicy` resolution, secret manager, KMS, key rotation, encrypted storage, real provider execution, or remote runner secret distribution.
- `apiKeySecretRef` remains a future secret lookup reference, not a raw provider token value.
