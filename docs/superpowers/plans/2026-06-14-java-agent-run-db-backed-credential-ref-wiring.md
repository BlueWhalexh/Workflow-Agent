# Java Agent Run DB-Backed Credential Ref Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J24E only; do not add a new public request field, public credential CRUD API, secret manager lookup, raw secret storage, real provider calls, remote runner secret distribution, or UI.

**Goal:** Wire `POST /v1/workspaces/{workspaceId}/agent-runs` to resolve DB-backed provider credential metadata through the existing `providerRuntimeRef` field without exposing raw provider secrets.

**Architecture:** Keep the public request shape unchanged. `providerRuntimeRef` values that start with `credential.` are resolved through `ProviderCredentialService`; the service still performs workspace editor authorization and scoped metadata lookup. J24E only supports `env://NAME` credential secret refs by converting them to worker `apiKeyEnvName`; other secret reference schemes remain blocked until a secret manager/worker injection path exists.

**Tech Stack:** Java, Spring Boot MVC, JDBC profile, MySQL Testcontainers, JUnit 5, AssertJ, MockMvc.

---

## Scope

Files:

- Create: `backend/src/test/java/com/myworkflow/agent/backend/run/ProviderCredentialRunControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java`
- Create: `docs/superpowers/plans/2026-06-14-java-agent-run-db-backed-credential-ref-wiring.md`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

In scope:

- Treat `providerRuntimeRef: "credential.<credentialRef>"` as a DB-backed credential reference.
- Resolve the credential through `ProviderCredentialService.resolveRuntimeDescriptorForWorkspace`.
- Convert `env://MIMO_API_KEY` to worker runtime metadata `apiKeyEnvName: "MIMO_API_KEY"`.
- Preserve provider/model/baseUrl and default timeout metadata.
- Reject credential refs that resolve to non-`env://` secret refs until a real secret manager/worker injection policy is implemented.
- Keep config-backed `providerRuntimeRef` behavior unchanged.

Out of scope:

- New public request field such as `providerCredentialRef`.
- Public credential CRUD API.
- Secret manager/KMS/keychain lookup.
- Reading raw secret values.
- Writing raw secret values to DB, logs, artifacts, API responses, or worker requests.
- Real external provider calls.
- Remote runner credential distribution.

## Task 1: RED Controller Test

- [x] Add `ProviderCredentialRunControllerTest`.
- [x] Use `spring.profiles.active=jdbc` with MySQL Testcontainers.
- [x] Override `AgentWorker` with a capturing fake worker.
- [x] Create a workspace through the public API.
- [x] Seed `ProviderCredentialRepository` with a workspace-scoped credential:
  - `credentialRef = "workspace-mimo"`
  - `provider = "mimo-real"`
  - `model = "mimo-v2.5"`
  - `baseUrl = "https://token-plan-cn.xiaomimimo.com/v1"`
  - `apiKeySecretRef = "env://MIMO_API_KEY"`
- [x] Start a run with `providerRuntimeRef = "credential.workspace-mimo"`.
- [x] Assert the worker receives provider runtime metadata with `apiKeyEnvName`, not raw secret keys.
- [x] Add rejection coverage for a credential with `apiKeySecretRef = "secret://team/provider/mimo"`.

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest test
```

Expected RED:

- The env-backed credential run fails because `AgentRunService` still routes `credential.workspace-mimo` through config-backed `ProviderRuntimePolicy` and reports an unknown provider runtime reference.

## Task 2: Minimal Implementation

- [x] Inject `ObjectProvider<ProviderCredentialService>` into `AgentRunService`.
- [x] Detect `providerRuntimeRef` values starting with `credential.` before config-backed policy resolution.
- [x] Resolve descriptors with the current workspace id and credential ref suffix.
- [x] Return `null` if no descriptor exists.
- [x] Convert `env://NAME` to worker runtime metadata:
  - `provider`
  - `model` when present
  - `baseUrl` when present
  - `apiKeyEnvName`
  - `timeoutMs = 30000`
- [x] Reject non-env secret refs with `IllegalArgumentException`.
- [x] Do not pass `apiKeySecretRef`, `apiKey`, `token`, `authorization`, or `Authorization` to the worker.

## Task 3: Focused Verification

- [x] Run focused controller test:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest test
```

- [x] Run provider/run focused suite:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=ProviderCredentialRunControllerTest,ProviderCredentialServiceTest,ProviderCredentialRepositoryTest,ProviderCredentialSchemaTest,ProviderRuntimePolicyTest,ProviderSecretPolicyControllerTest test
```

## Task 4: Documentation And Archive

- [x] Update `docs/architecture/java-team-backend-platform-spec.md` with J24E current truth and boundaries.
- [x] Update `docs/reports/runtime-work-item-execution-resume-delivery.md` with RED/GREEN/full evidence.
- [x] Mark this plan's tasks complete only after evidence exists.

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
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend/src/main/java/com/myworkflow/agent/backend/run/AgentRunService.java backend/src/test/java/com/myworkflow/agent/backend/run/ProviderCredentialRunControllerTest.java docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-agent-run-db-backed-credential-ref-wiring.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \[ \]" docs/superpowers/plans/2026-06-14-java-agent-run-db-backed-credential-ref-wiring.md
```

## Acceptance

J24E is complete only if:

- `providerRuntimeRef = "credential.<ref>"` resolves through DB credential metadata and workspace authorization.
- The worker receives only provider metadata and `apiKeyEnvName`, not raw secret values or `apiKeySecretRef`.
- Non-env secret refs are rejected before worker execution.
- Config-backed `providerRuntimeRef` behavior remains covered.
- No new public request field, public credential CRUD, secret manager, real provider call, or remote runner secret distribution is added.

## Execution Status

Implemented for J24E. RED failed for the expected reason: `providerRuntimeRef = "credential.workspace-mimo"` was still routed through config-backed provider runtime policy and rejected as unknown. After the minimal implementation, the focused controller test, provider/run focused suite, Java full suite, TypeScript full suite, typecheck, diff check, whitespace/conflict scan, token redaction scan, and unchecked-plan scan all passed.

The controller test uses a capturing fake worker and MySQL Testcontainers. It proves Java resolves DB-backed credential metadata into worker-safe provider runtime metadata with `apiKeyEnvName`, and rejects non-`env://` secret refs before worker execution. It does not prove a real external provider call, public credential CRUD, secret manager lookup, raw secret injection, or remote runner credential distribution.
