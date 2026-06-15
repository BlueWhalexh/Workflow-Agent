# Java Provider Runtime Env Name Guard Plan

> **Agent handoff note:** use TDD. This is Phase J23B only; do not add credential DB tables, secret manager integration, public credential APIs, real provider smoke, runner-scoped secret distribution, or raw token storage.

## Goal

Tighten the allowed `api-key-env-name` metadata so configured provider refs can pass only an environment variable name, not an arbitrary string that could contain a secret value.

## Scope

In scope:

- Add a focused unit regression proving invalid `api-key-env-name` values are rejected.
- Keep `MIMO_API_KEY` and other uppercase env-style names valid.
- Keep raw secret config key rejection from J23A.
- Update architecture and delivery docs with the new guard and boundaries.

Out of scope:

- Credential DB, secret manager, KMS, rotation, encrypted storage, or user-facing credential management.
- Reading real secret values in Java.
- Real external provider calls.
- Remote runner secret distribution.
- Public API contract changes.

## Files

- `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicyTest.java`
- `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicy.java`
- `docs/architecture/java-team-backend-platform-spec.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- `docs/superpowers/plans/2026-06-14-java-provider-runtime-env-name-guard.md`

## Tasks

- [x] Write RED `ProviderRuntimePolicyTest` for invalid `api-key-env-name`.
- [x] Run focused RED:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
- [x] Implement minimal `api-key-env-name` format guard.
- [x] Run focused GREEN:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
- [x] Run provider policy focused suite:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
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

J23B is complete only if:

- `ProviderRuntimePolicy` rejects configured refs whose `api-key-env-name` is not an env-var-style identifier.
- `ProviderRuntimePolicy` still resolves safe provider runtime metadata and valid `apiKeyEnvName`.
- No public API response schema, DB schema, production dependency, secret storage, real provider call, or remote runner secret distribution is introduced.
- Delivery report records RED, GREEN, focused/full/typecheck/static evidence and explicitly states this is not credential DB/secret manager completion.

## Execution Status

Implemented for Phase J23B.

Delivered:

- Added `api-key-env-name` format validation to `ProviderRuntimePolicy`.
- Kept `MIMO_API_KEY` and other uppercase env-style names valid.
- Kept J23A raw secret config key rejection unchanged.
- No public API response schema, DB schema, production dependency, real provider call, or secret storage was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - Failed first with 6 tests / 1 failure.
  - Expected failure: `rejectsConfiguredProviderReferencesWithInvalidApiKeyEnvName` expected an exception, but current code accepted `api-key-env-name=fixture-secret-value`.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - 6 Java tests passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 8 Java tests passed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 84 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.

Static verification:

- `git diff --check`
  - passed after J23B archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicy.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicyTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-runtime-env-name-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J23B archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J23B archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-runtime-env-name-guard.md`
  - no unchecked plan tasks after final archive update.

Boundary:

- J23B is a backend config metadata guard only. It does not implement credential DB, secret manager, KMS, key rotation, encrypted storage, public credential APIs, remote runner secret distribution, or real external provider execution.
