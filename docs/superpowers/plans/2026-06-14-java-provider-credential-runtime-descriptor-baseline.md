# Java Provider Credential Runtime Descriptor Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J24D only; do not add public credential CRUD APIs, secret manager integration, encrypted secret storage, real provider calls, worker secret injection, or remote runner secret distribution.

**Goal:** Add an internal runtime descriptor baseline so DB-backed provider credential metadata can be converted into secret-safe runtime metadata after workspace authorization.

**Architecture:** Extend `ProviderCredentialService` only. The existing `resolveForWorkspace(workspaceId, credentialRef)` remains the metadata lookup boundary; the new descriptor method reuses that guard, validates supported provider ids and secret-reference shape, and returns metadata that contains a secret reference but never a raw token value.

**Tech Stack:** Java, Spring service class, in-memory workspace repository for focused service tests, JUnit 5, AssertJ.

---

## Scope

Files:

- Modify: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java`
- Create: `docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

In scope:

- Add `resolveRuntimeDescriptorForWorkspace(workspaceId, credentialRef)` to `ProviderCredentialService`.
- Add a small `ProviderCredentialRuntimeDescriptor` record in the same service file.
- Reuse workspace editor authorization from `resolveForWorkspace`.
- Preserve `provider`, optional `model`, optional `baseUrl`, and `apiKeySecretRef`.
- Validate `apiKeySecretRef` is a reference URI such as `env://MIMO_API_KEY`, `secret://...`, `keychain://...`, or `file://...`.
- Reject unsupported provider ids and raw/plain secret-looking refs before returning a descriptor.

Out of scope:

- Public credential create/list/update/delete APIs.
- Secret manager/KMS/keychain lookup.
- Injecting secret values into local or remote workers.
- Changing `POST /v1/workspaces/{workspaceId}/agent-runs`.
- Real provider calls.
- Remote runner secret distribution.

## Task 1: RED Service Tests

- [x] Add tests to `ProviderCredentialServiceTest`:
  - `resolvesSecretSafeRuntimeDescriptorForWorkspaceEditor`
  - `rejectsRuntimeDescriptorWithUnsupportedProviderOrPlainSecretRef`
- [x] Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test
```

Expected RED:

- test compilation fails because `resolveRuntimeDescriptorForWorkspace` and `ProviderCredentialRuntimeDescriptor` do not exist yet.

## Task 2: Minimal Implementation

- [x] In `ProviderCredentialService`, add:
  - supported provider id set matching the current backend provider runtime allow-list;
  - secret reference URI validation;
  - `resolveRuntimeDescriptorForWorkspace`;
  - `ProviderCredentialRuntimeDescriptor` record.
- [x] Keep existing `resolveForWorkspace` behavior unchanged.
- [x] Do not add new production dependencies.

## Task 3: Focused Verification

- [x] Run focused service test:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test
```

- [x] Run provider focused suite:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test
```

## Task 4: Documentation And Archive

- [x] Update `docs/architecture/java-team-backend-platform-spec.md` with J24D current truth and boundaries.
- [x] Update `docs/reports/runtime-work-item-execution-resume-delivery.md` with RED/GREEN/full evidence and no-real-provider boundary.
- [x] Mark this plan's tasks complete only after the evidence exists.

## Task 5: Full Verification

- [x] Run Java full suite:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] Run TypeScript full suite:

```bash
npm test
```

- [x] Run TypeScript typecheck:

```bash
npm run typecheck
```

- [x] Run static gates:

```bash
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md
```

## Acceptance

J24D is complete only if:

- The descriptor method can only resolve after workspace editor authorization.
- Descriptor output contains provider metadata and a secret reference, not raw secret material.
- Unsupported provider ids and plain/non-URI secret refs fail closed.
- No public credential API, secret manager lookup, worker secret injection, DB-backed agent-run switching, remote runner secret distribution, or real provider call is added.
- Focused, provider focused, full Java, TypeScript full, typecheck, diff/static, and token redaction gates are recorded.

## Execution Status

Implemented for Phase J24D.

RED evidence:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - Failed first in `testCompile`.
  - Expected failure: `ProviderCredentialService.resolveRuntimeDescriptorForWorkspace(...)` did not exist.

Focused verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest test`
  - 5 Java tests passed.
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test`
  - 15 Java tests passed.

Full verification:

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
  - 91 Java tests passed.
- `npm test`
  - 44 test files / 178 tests passed.
- `npm run typecheck`
  - passed.
- `git diff --check`
  - passed after J24D archive updates.
- `rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialService.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/ProviderCredentialServiceTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'`
  - no matches after J24D archive updates.
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
  - no matches after J24D archive updates.
- `rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-provider-credential-runtime-descriptor-baseline.md`
  - no unchecked plan tasks after final archive update.

Evidence boundaries:

- No public credential API, secret manager lookup, worker secret injection, DB-backed agent-run switching, remote runner secret distribution, or real provider call was added.
- `apiKeySecretRef` remains a secret reference URI, not a raw provider token value.
