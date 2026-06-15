# Java Provider Runtime Raw Secret Guard Plan

> **Agent handoff note:** use TDD. This is Phase J23A only; do not add credential DB tables, secret manager integration, public credential APIs, real provider smoke, runner-scoped secret distribution, or raw token storage.

## Goal

Tighten the existing J9A provider runtime reference policy so configured provider refs fail closed when backend config contains raw secret values such as `api-key`, `token`, or `authorization`. Safe metadata such as `provider`, `model`, `base-url`, `timeout-ms`, and `api-key-env-name` remains allowed.

## Scope

In scope:

- Add a focused unit regression proving raw secret config keys are rejected.
- Keep `api-key-env-name` as the allowed injection reference.
- Keep default fake provider behavior unchanged.
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
- `docs/superpowers/plans/2026-06-14-java-provider-runtime-raw-secret-guard.md`

## Tasks

- [x] Write RED `ProviderRuntimePolicyTest` for raw secret config keys.
- [x] Run focused RED:
  - `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
- [x] Implement minimal raw secret config guard in `ProviderRuntimePolicy`.
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

J23A is complete only if:

- `ProviderRuntimePolicy` rejects configured refs that contain raw secret config keys.
- `ProviderRuntimePolicy` still resolves safe provider runtime metadata and `apiKeyEnvName`.
- No public API response schema, DB schema, production dependency, secret storage, real provider call, or remote runner secret distribution is introduced.
- Delivery report records RED, GREEN, focused/full/typecheck/static evidence and explicitly states this is not credential DB/secret manager completion.

## Execution Status

Implemented for Phase J23A.

Delivered:

- Added raw secret config key rejection to `ProviderRuntimePolicy`.
- Kept safe provider runtime metadata resolution unchanged, including `apiKeyEnvName`.
- Kept default fake provider behavior unchanged.
- No public API response schema, DB schema, production dependency, real provider call, or secret storage was added.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - Failed first with 5 tests / 1 failure.
  - Expected failure: `rejectsConfiguredProviderReferencesWithRawSecretValues` expected an exception, but current code did not reject raw secret config keys.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest test`
  - 5 Java tests passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 7 Java tests passed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 83 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J23A archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicy.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderRuntimePolicyTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-runtime-raw-secret-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J23A archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J23A archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-runtime-raw-secret-guard.md`
  - no unchecked plan tasks after final archive update.

Boundary:

- J23A is a backend config guard only. It does not implement credential DB, secret manager, KMS, key rotation, encrypted storage, public credential APIs, remote runner secret distribution, or real external provider execution.
