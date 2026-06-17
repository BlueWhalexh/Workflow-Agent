# Java HTTP Secret Manager Resolver Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a backend-owned external HTTP secret manager resolver baseline so DB-backed provider credentials can resolve `secret://...` refs without local files or raw API tokens in public requests.

**Architecture:** Keep the existing `ProviderSecretResolver` SPI and `AgentWorkerSecretInjection` path. Add an optional config-backed HTTP resolver for `secret://` refs; it POSTs a ref-only JSON request to a configured secret manager endpoint, optionally authenticates with a bearer token read from an environment variable name, validates a small response envelope, and returns only the secret value to in-memory worker env injection. This is not a vendor KMS/Vault/Keychain SDK, secret rotation, public secret upload API, or remote runner secret distribution.

**Tech Stack:** Spring Boot config, Java `HttpClient`, Jackson, existing provider credential repository/service/run path, JUnit/MockMvc/Testcontainers/local JDK `HttpServer`, TypeScript typecheck.

---

### Task 1: RED tests for HTTP secret manager resolver and contract flag

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/providersecret/HttpProviderSecretResolverTest.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/run/HttpProviderSecretRunControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`

- [x] **Step 1: Write failing resolver tests**

Test desired behavior:
- `HttpProviderSecretResolver` resolves `secret://team/workspace/mimo` through a local HTTP secret manager stub.
- The resolver sends only `schemaVersion` and `secretRef` in request JSON.
- Optional Authorization uses `Bearer <env-secret>` where the raw token comes from an injected env lookup.
- Non-`secret://` refs are ignored.
- Missing configured auth token env value fails fast.
- Non-2xx, malformed JSON, wrong schema, blank `secretValue`, or oversized response returns empty.

- [x] **Step 2: Write failing run-path integration test**

Test desired behavior:
- A JDBC provider credential with `apiKeySecretRef = "secret://team/provider/mimo"` is resolved by the HTTP resolver during `POST /v1/workspaces/{workspaceId}/agent-runs`.
- The capturing local worker receives `apiKeyEnvName=PROVIDER_CREDENTIAL_API_KEY`.
- The raw secret is present only in `AgentWorkerSecretInjection.environmentVariables()`.
- Worker request/provider runtime/public run response do not contain `secret://...`, raw secret, auth token, `apiKeySecretRef`, `apiKey`, `token`, or `Authorization`.

- [x] **Step 3: Update ops RED expectation**

Update `/v1/ops/integration-contract` test to expect `productionSecretManager=true` after the external HTTP resolver baseline exists.

- [x] **Step 4: Run focused RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=HttpProviderSecretResolverTest,HttpProviderSecretRunControllerTest,OpsIntegrationContractControllerTest
```

Expected before implementation: compile failure because `HttpProviderSecretResolver` / HTTP provider-secret config do not exist, and ops still reports `productionSecretManager=false`.

### Task 2: Implement config-backed HTTP secret manager resolver

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/providersecret/HttpProviderSecretResolver.java`

- [x] **Step 1: Extend `BackendProperties`**

Add provider-secret HTTP config:
- `my-workflow.backend.provider-secrets.http-resolver-uri`
- `my-workflow.backend.provider-secrets.http-auth-token-env-name`
- `my-workflow.backend.provider-secrets.http-timeout-ms`

Validation:
- Blank URI disables the resolver.
- Configured URI must be absolute `http` or `https` and must not contain userinfo.
- Auth token env name is optional, but if configured it must match `[A-Z_][A-Z0-9_]*`.
- Timeout must stay in a bounded range, for example 100..30000 ms.

- [x] **Step 2: Add `HttpProviderSecretResolver`**

Responsibilities:
- `@Component` enabled only when `http-resolver-uri` is nonblank.
- Resolve only `secret://` refs; ignore `env://`, `file://`, `keychain://`, null, and blank refs.
- Construct JSON request body with `schemaVersion="provider-secret-resolve-request.v1"` and `secretRef`.
- Add `Authorization: Bearer ...` only when auth env-name is configured.
- Fail fast at construction if configured auth env-name is missing or blank.
- Accept only 2xx responses with `schemaVersion="provider-secret-resolve-response.v1"` and nonblank `secretValue`.
- Refuse oversized response bodies or secret values.
- Return `Optional.empty()` on network, parsing, non-2xx, schema, or blank-value failures without logging raw secret/ref values.

### Task 3: Contract and docs

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-17-java-http-secret-manager-resolver-baseline.md`

- [x] **Step 1: Update ops contract**

Set `productionSecretManager=true` because backend can now resolve `secret://` refs through an external HTTP secret manager adapter.

- [x] **Step 2: Update docs and report**

Document J42A boundaries:
- External HTTP secret manager resolver baseline exists.
- Raw secret remains in memory-only worker env injection.
- Public API, DB metadata, worker request JSON, ops response, and reports do not expose raw secret values.
- Not vendor KMS/Vault/Keychain SDK, key rotation, secret registration UI/API, team-scoped credential API, or remote runner secret distribution.
- Local HTTP stubs are fake integration evidence, not a real secret manager smoke.

### Task 4: Verification and commit

**Files:**
- Verify all modified files.

- [x] **Step 1: Focused tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=HttpProviderSecretResolverTest,HttpProviderSecretRunControllerTest,ProviderCredentialRunControllerTest,FileProviderSecretRunControllerTest,OpsIntegrationContractControllerTest
```

- [x] **Step 2: Full backend tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
```

- [x] **Step 3: Typecheck**

Run:

```bash
npm run typecheck
```

- [x] **Step 4: Static and token scans**

Run:

```bash
git diff --check
git diff --cached --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-http-secret-manager-resolver-baseline.md
```

- [x] **Step 5: Commit and push**

Stage only J42A files and commit:

```bash
git add backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java backend/src/main/java/com/myworkflow/agent/backend/providersecret/HttpProviderSecretResolver.java backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java backend/src/test/java/com/myworkflow/agent/backend/providersecret/HttpProviderSecretResolverTest.java backend/src/test/java/com/myworkflow/agent/backend/run/HttpProviderSecretRunControllerTest.java backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java docs/architecture/java-team-backend-platform-spec.md docs/reports/java-backend-phase-one-completion-audit.md docs/reports/runtime-work-item-execution-resume-delivery.md docs/superpowers/plans/2026-06-17-java-http-secret-manager-resolver-baseline.md
git commit -m "feat: add http secret manager resolver baseline"
git push
```
