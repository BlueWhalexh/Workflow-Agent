# Java File Secret Resolver Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local `file://` provider secret resolver baseline so DB-backed provider credentials can resolve file-backed API keys through the existing per-run secret injection path without exposing raw secrets in API responses, worker request metadata, logs, docs, or artifacts.

**Architecture:** Keep Java as the control plane and reuse the existing `ProviderSecretResolver` SPI plus `AgentWorkerSecretInjection`. A new Spring component resolves only configured-root-relative `file://...` references, rejects path traversal and missing/non-file/blank secrets by returning empty, and never logs or returns the file path in public responses. `AgentRunService` resolves non-`env://` secret refs through all available resolver beans, preserving the existing `secret://` fixture behavior and adding `file://` without a new production dependency.

**Tech Stack:** Java 18-compatible code, Spring Boot component configuration, JUnit 5, MockMvc, Testcontainers/MySQL for the run path integration test.

---

## Scope

Files:

- Create: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/FileProviderSecretResolver.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/FileProviderSecretResolverTest.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/run/FileProviderSecretRunControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan file

Out of scope:

- No public API to upload raw secrets.
- No KMS, Keychain, Vault, OAuth, or remote runner secret distribution.
- No raw provider token in request JSON, DB plaintext beyond the local file itself, response, audit message, worker request metadata, docs, or test snapshots.
- No production dependency additions.

## Task 1: RED Unit Test For File Resolver

- [x] Write `FileProviderSecretResolverTest` before production code.
- [x] Cover `file://relative/path` resolving under a configured file-root.
- [x] Cover traversal (`file://../outside`) returning empty.
- [x] Cover non-file scheme returning empty.
- [x] Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=FileProviderSecretResolverTest
```

Expected RED: compilation fails because `FileProviderSecretResolver` does not exist yet.

## Task 2: RED Integration Test For Run Path

- [x] Write `FileProviderSecretRunControllerTest` before production code.
- [x] Seed JDBC provider credential metadata with `apiKeySecretRef = file://mimo/api-key.txt`.
- [x] Write the secret file under the configured root.
- [x] Start a run with `providerRuntimeRef = credential.file-mimo`.
- [x] Assert worker request receives `apiKeyEnvName = PROVIDER_CREDENTIAL_API_KEY`.
- [x] Assert raw secret and `file://` ref do not appear in provider runtime or worker request string.
- [x] Assert secret value appears only in `AgentWorkerSecretInjection`.
- [x] Add a traversal ref case that rejects before worker invocation and does not echo the traversal ref or secret.
- [x] Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=FileProviderSecretRunControllerTest
```

Expected RED: compilation fails because file resolver support does not exist yet, or the run fails with unsupported secret ref scheme.

## Task 3: Minimal Production Implementation

- [x] Extend `BackendProperties` with optional `providerSecretFileRoot`.
- [x] Add `FileProviderSecretResolver` as a conditional Spring component:
  - active only when `my-workflow.backend.provider-secrets.file-root` is configured;
  - handles only `file://` refs;
  - resolves root-relative normalized paths;
  - rejects blank, absolute, traversal, directory, missing, or blank file content by returning `Optional.empty()`;
  - trims trailing surrounding whitespace from file content;
  - does not log, print, or expose secret values.
- [x] Update `AgentRunService` so non-`env://` refs are resolved through available `ProviderSecretResolver` beans.
- [x] Preserve existing `secret://` fixture behavior and existing messages as much as possible.

## Task 4: GREEN Verification

- [x] Run unit focused test:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=FileProviderSecretResolverTest
```

- [x] Run integration focused test:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=ProviderCredentialRunControllerTest,FileProviderSecretRunControllerTest,ProviderSecretPolicyControllerTest
```

- [x] Run full Java backend suite:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] Run TypeScript typecheck:

```bash
npm run typecheck
```

## Task 5: Docs, Report, Static Checks, Commit

- [x] Update architecture spec current status and provider credential boundary text.
- [x] Update backend phase-one completion audit with J33A status.
- [x] Append delivery report evidence with RED/GREEN/full/static boundaries.
- [x] Mark this plan complete only after all evidence is recorded.
- [x] Run static checks:

```bash
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-17-java-file-secret-resolver-baseline.md
```

- [x] Commit and push:

```bash
git add backend/src/main/java/com/myworkflow/agent/backend/providersecret/FileProviderSecretResolver.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/FileProviderSecretResolverTest.java backend/src/test/java/com/myworkflow/agent/backend/run/FileProviderSecretRunControllerTest.java backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java docs/architecture/java-team-backend-platform-spec.md docs/reports/java-backend-phase-one-completion-audit.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/superpowers/plans/2026-06-17-java-file-secret-resolver-baseline.md
git commit -m "feat: add file secret resolver baseline"
git push -u origin main
```

## Self-Review Notes

- Spec coverage: covers file-backed secret resolver and run-path injection only; real KMS/Keychain/secret rotation/remote runner distribution remain out of scope.
- Placeholder scan: no TBD/TODO/deferred implementation placeholders.
- Type consistency: new resolver uses existing `ProviderSecretResolver` and `AgentWorkerSecretInjection` contracts.
