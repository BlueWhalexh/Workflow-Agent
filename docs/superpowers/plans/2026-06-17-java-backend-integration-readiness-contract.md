# Java Backend Integration Readiness Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a thin backend integration readiness contract so frontend and runtime integrators can discover stable backend capabilities and explicit remaining gaps from a single Java API response.

**Architecture:** Extend the existing ops surface with `GET /v1/ops/integration-contract`. The response is static backend-owned metadata: stable schema, required frontend/runtime endpoints, SSE contract notes, public envelope version, and readiness flags that keep unfinished production capabilities marked false. The endpoint must not expose secrets, provider payloads, workspace paths, issuer/JWKS URLs, or runtime-private result shapes.

**Tech Stack:** Java 17/21-compatible source, Spring Boot MVC, MockMvc, JUnit 5, existing `ApiEnvelope`.

---

## Scope

In:

- `GET /v1/ops/integration-contract`.
- Machine-readable frontend/runtime capability lists.
- Explicit `false` readiness flags for remaining backend phase-one gaps.
- OpenAPI smoke coverage for the new ops endpoint.
- Documentation/report updates with RED/GREEN/full/static evidence.

Out:

- Frontend implementation.
- Runtime execution changes.
- OAuth login/session/token introspection.
- External directory sync.
- Production KMS/Vault/Keychain adapter.
- Remote runner dispatch/artifact upload/cancellation.
- Multi-node stream fanout.
- Real provider or real IdP E2E.

## Files

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpenApiContractTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan, checking off completed steps.

## Task 1: RED Contract Test

**Files:**

- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpenApiContractTest.java`

- [x] **Step 1: Write failing MockMvc test**

Add coverage that proves `GET /v1/ops/integration-contract` returns:

- `java-backend-api.v1` envelope.
- `schemaVersion = "java-backend-integration-contract.v1"`.
- `apiBasePath = "/v1"`.
- `publicEnvelopeSchema = "java-backend-api.v1"`.
- frontend-required endpoints for identity, workspace, run, events, artifacts, approvals, audit, provider credentials, auth diagnostics.
- runtime-required endpoints for agent run create/polling, events, artifacts, approvals, remote runner registry/heartbeat/lease, and auth diagnostics.
- capability flags where implemented baselines are true and remaining production gaps are false.
- no token, secret, Authorization, issuerUri, jwksUri, workspaceRoot, or provider payload material.

- [x] **Step 2: Add OpenAPI expectation**

Extend `OpenApiContractTest` to require `/v1/ops/integration-contract` in the OpenAPI document.

- [x] **Step 3: Run RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest,OpenApiContractTest
```

Expected before implementation: fail with HTTP 404 or missing OpenAPI path for `/v1/ops/integration-contract`.

Actual RED: failed as expected with HTTP 404 for `/v1/ops/integration-contract` and missing OpenAPI path.

## Task 2: Implement Minimal Contract Endpoint

**Files:**

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`

- [x] **Step 1: Add response records**

Add nested records under `OpsController`:

- `IntegrationContractResponse`
- `IntegrationEndpoint`
- `IntegrationCapabilities`

- [x] **Step 2: Add static contract data**

Add endpoint lists using stable strings only. Do not derive from runtime internals or include raw config values.

- [x] **Step 3: Add controller method**

Add:

```java
@GetMapping(value = "/v1/ops/integration-contract", produces = MediaType.APPLICATION_JSON_VALUE)
public ApiEnvelope<IntegrationContractResponse> integrationContract()
```

- [x] **Step 4: Run focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OpsIntegrationContractControllerTest,OpenApiContractTest,OpsControllerTest,OpsAuthConfigControllerTest
```

Expected: pass.

Actual focused GREEN: 6 Java tests passed for integration contract, OpenAPI, health/readiness, and auth-config ops coverage.

## Task 3: Docs, Reports, Verification

**Files:**

- Modify docs listed in Files.

- [x] **Step 1: Update architecture spec and phase audit**

Document J38A as an integration-readiness contract baseline only. Keep frontend/runtime E2E, OAuth/session/introspection, production secrets, external directory, remote dispatch, and multi-node fanout as remaining gaps.

- [x] **Step 2: Update delivery report**

Record RED, focused GREEN, full Java, typecheck, static checks, token scan, unchecked-plan scan, and dirty-worktree note for unrelated frontend/package files.

- [x] **Step 3: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-backend-integration-readiness-contract.md
```

Expected: Maven and typecheck pass; diff check passes; token scan exits 1 with no matches; unchecked-plan scan exits 1 after all steps are checked.

Actual final verification: Maven full Java passed with 138 tests; `npm run typecheck` passed; `git diff --check` passed; token scan had no matches; unchecked-plan scan is clean after this final checkbox update.
