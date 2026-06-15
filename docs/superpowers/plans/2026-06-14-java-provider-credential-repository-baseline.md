# Java Provider Credential Repository Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J24B only; do not add public credential APIs, secret manager integration, KMS, key rotation, encrypted secret storage, real provider smoke, or remote runner secret distribution.

**Goal:** Add the first JDBC repository baseline for provider credential metadata so later credential services can resolve team/workspace-scoped credential references without reading raw provider token values.

**Architecture:** Keep the slice under `providersecret`. The repository owns only metadata persistence and scoped lookup on the existing `provider_credentials` table; it is not wired into the run API or provider runtime policy in this phase.

**Tech Stack:** Java, Spring JDBC `JdbcTemplate`, Flyway migrations, MySQL/Testcontainers, JUnit 5, AssertJ.

---

## Files

- Create: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md`

## Scope

In scope:

- Save provider credential metadata rows into the J24A `provider_credentials` table.
- Read only active credential metadata by `(teamId, workspaceId, credentialRef)`.
- Allow team-scoped credentials with `workspace_id IS NULL`.
- Allow workspace-scoped credentials only for the matching workspace id.
- Prove cross-team, cross-workspace, and disabled credentials are not resolved.
- Prove the Java metadata record exposes `apiKeySecretRef` but no raw `apiKey`, `token`, `authorization`, `secretValue`, or `rawSecret` components.

Out of scope:

- Public credential CRUD API.
- Secret manager, KMS, encrypted values, key rotation, or token retrieval.
- Switching `ProviderRuntimePolicy` from config refs to DB refs.
- Injecting provider secrets into local or remote workers.
- Real provider calls.
- Remote runner secret distribution.

## Tasks

- [x] Add RED repository test for scoped active credential metadata lookup.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - Expected: fails because `ProviderCredentialRepository` does not exist yet.
- [x] Implement `ProviderCredentialRepository` with a nested `ProviderCredentialMetadata` record and `save` / `findActiveByScope` methods.
  - `save` writes only metadata and `api_key_secret_ref`.
  - `findActiveByScope` returns rows where `team_id = ?`, `credential_ref = ?`, `status = 'ACTIVE'`, and `workspace_id IS NULL OR workspace_id = ?`.
- [x] Run focused GREEN repository test.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - Expected: 1 Java test passes.
- [x] Run provider secret focused suite.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - Expected: provider credential schema, repository, config policy, and controller policy tests pass.
- [x] Update architecture spec and delivery report with J24B status and boundaries.
- [x] Run full verification.
  - Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - Run: `npm test`
  - Run: `npm run typecheck`
  - Run: `git diff --check`
  - Run: `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - Run: `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - Run: `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md`

## Completion Boundary

J24B is complete only if:

- The repository test fails before implementation for the expected missing repository reason.
- The repository saves and reads metadata against MySQL/Testcontainers.
- Scoped lookup does not cross team or workspace boundaries and ignores disabled credentials.
- The Java metadata record contains only a secret reference, not raw secret values.
- No public API, secret manager, real provider call, or remote runner secret distribution is introduced.
- Architecture and delivery docs record RED, GREEN, focused/full/typecheck/static evidence and remaining boundaries.

## Execution Status

Implemented for Phase J24B.

Delivered:

- Added `ProviderCredentialRepository` under `providersecret`.
- Added nested `ProviderCredentialMetadata` with `apiKeySecretRef` metadata and no raw secret components.
- Added MySQL/Testcontainers coverage for saving team-scoped, workspace-scoped, and disabled credential metadata.
- Added scoped ACTIVE lookup that returns team-scoped refs, returns matching workspace-scoped refs, and rejects disabled, cross-team, and cross-workspace records.
- No public credential API, secret manager, real provider call, DB-backed runtime policy switch, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - Failed first in `testCompile`.
  - Expected failure: `ProviderCredentialRepository` and `ProviderCredentialMetadata` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest test`
  - 1 Java test passed.
  - Covers metadata save, scoped ACTIVE lookup, disabled/cross-scope rejection, and raw secret component absence.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 10 Java tests passed.
  - Confirms J24B does not break J24A schema, J23 config policy, or HTTP-level provider runtime ref boundaries.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 86 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24B archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepository.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialRepositoryTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24B archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24B archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-repository-baseline.md`
  - no unchecked plan tasks after final archive update.

Boundary:

- J24B is a JDBC metadata repository baseline only. It is not public credential CRUD, DB-backed `ProviderRuntimePolicy` resolution, secret manager, KMS, key rotation, encrypted storage, real provider execution, or remote runner secret distribution.
- `apiKeySecretRef` remains a reference to future secret lookup infrastructure, not a provider token value.
