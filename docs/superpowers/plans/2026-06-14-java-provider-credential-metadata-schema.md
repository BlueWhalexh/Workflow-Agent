# Java Provider Credential Metadata Schema Plan

> **Agent handoff note:** use TDD. This is Phase J24A only; do not add public credential APIs, secret manager integration, KMS, key rotation, encrypted secret storage, real provider smoke, or remote runner secret distribution.

## Goal

Add the first DB-level provider credential metadata baseline so the Java backend has a durable place for team/workspace-scoped credential references without storing raw provider token values.

## Scope

In scope:

- Add a Flyway migration for `provider_credentials` metadata.
- Store credential identity, scope, provider metadata, status, and a secret reference name.
- Prove the schema exists after MySQL/Testcontainers migration.
- Prove the table does not include obvious raw secret value columns such as `api_key`, `token`, `authorization`, `secret_value`, or `raw_secret`.
- Update architecture and delivery docs with the new schema boundary.

Out of scope:

- Public credential CRUD API.
- Repository/service/controller behavior for credential records.
- Secret manager, KMS, key rotation, encrypted secret values, or credential material retrieval.
- Real external provider calls.
- Remote runner secret distribution.
- Switching `ProviderRuntimePolicy` from config refs to DB refs.

## Files

- `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialSchemaTest.java`
- `backend/src/main/resources/db/migration/V9__provider_credential_metadata_baseline.sql`
- `docs/architecture/java-team-backend-platform-spec.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- `docs/superpowers/plans/2026-06-14-java-provider-credential-metadata-schema.md`

## Tasks

- [x] Write RED schema test for provider credential metadata without raw secret columns.
- [x] Run focused RED:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest test`
- [x] Add V9 Flyway migration for provider credential metadata.
- [x] Run focused GREEN:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest test`
- [x] Run provider secret focused suite:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
- [x] Run full Java suite:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
- [x] Run TS suite and typecheck:
  - `npm test`
  - `npm run typecheck`
- [x] Run static gates:
  - `git diff --check`
  - whitespace/merge-marker scan over touched files.
  - token scan over backend/docs/src/tests.
- [x] Archive execution evidence and remaining boundaries.

## Completion Boundary

J24A is complete only if:

- Full Flyway migration creates `provider_credentials`.
- The schema stores only metadata and a secret reference, not raw token values.
- No public API, repository/service behavior, secret manager, real provider call, or remote runner secret distribution is introduced.
- Delivery report records RED, GREEN, focused/full/typecheck/static evidence and explicitly states this is not credential DB feature completion.

## Execution Status

Implemented for Phase J24A.

Delivered:

- Added `V9__provider_credential_metadata_baseline.sql`.
- Added `provider_credentials` metadata table with team/workspace scope, provider metadata, `api_key_secret_ref`, status, and timestamps.
- Added MySQL/Testcontainers schema coverage that proves the table exists and does not include raw secret columns.
- No public credential API, repository/service behavior, secret manager, real provider call, or remote runner secret distribution was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest test`
  - Failed first with 1 test / 1 failure.
  - Expected failure: Flyway migrated only to v8 and the table list did not contain `provider_credentials`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest test`
  - 1 Java test passed.
  - Flyway migrated to v9 and `provider_credentials` existed without raw secret columns.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 9 Java tests passed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 85 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/resources/db/migration/V9__provider_credential_metadata_baseline.sql backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialSchemaTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-metadata-schema.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24A archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-metadata-schema.md`
  - no unchecked plan tasks after final archive update.

Boundary:

- J24A is schema-only provider credential metadata. It is not public credential CRUD, DB-backed runtime ref resolution, secret manager, KMS, key rotation, encrypted storage, real provider execution, or remote runner secret distribution.
