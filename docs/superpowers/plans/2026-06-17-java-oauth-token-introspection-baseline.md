# Java OAuth Token Introspection Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a configurable OAuth token introspection bearer verifier baseline without adding OAuth login, sessions, refresh tokens, or external directory sync.

**Architecture:** Keep HTTP auth routed through the existing `BearerTokenVerifier` SPI. Add an `oauth.introspection` config block that is mutually exclusive with OIDC/JWKS config, posts bearer tokens to a configured introspection endpoint, requires `active=true`, and maps configured response fields to `BackendPrincipal`. Optional client authentication reads the client secret from an environment variable name, not from raw config.

**Tech Stack:** Java 17/21-compatible source, Spring Boot MVC, JDK `HttpClient`, Jackson, JUnit 5, local JDK `HttpServer` introspection fixture.

---

## Scope

In:

- `my-workflow.backend.oauth.introspection-uri`.
- Optional `client-id` plus `client-secret-env-name`.
- Claim/field mapping for user id, team id, and display name.
- Fail-fast missing client secret env var when client auth is configured.
- Mutual exclusion with existing OIDC/JWKS verifier config.
- Readiness/auth diagnostics updates that expose only redacted mode/configured booleans.

Out:

- OAuth authorization-code login/callback.
- Sessions, cookies, CSRF, refresh tokens.
- Token persistence.
- External directory sync.
- Real IdP smoke.
- Raw secret config values.

## Files

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/security/ConfigurableOAuthIntrospectionBearerTokenVerifier.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/security/OAuthIntrospectionBearerVerifierTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsAuthConfigControllerTest.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsIntegrationContractControllerTest.java`
- Modify: docs and this plan for J39A evidence.

## Task 1: RED Introspection Contract

- [x] **Step 1: Write failing verifier tests**

Add tests for active token mapping, inactive token rejection, HTTP failure rejection, Basic auth from injected env lookup, missing env secret fail-fast, and OIDC/introspection config conflict.

- [x] **Step 2: Run RED**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OAuthIntrospectionBearerVerifierTest
```

Expected before implementation: fail at compilation because `BackendProperties.OAuthIntrospection` and `ConfigurableOAuthIntrospectionBearerTokenVerifier` do not exist.

## Task 2: Implement Minimal Verifier

- [x] **Step 1: Add config record and fail-fast validation**

Add `OAuthIntrospection` to `BackendProperties`, normalize blank values, require both `client-id` and `client-secret-env-name` together, validate env-name shape, and reject simultaneous OIDC/JWKS plus introspection config.

- [x] **Step 2: Add introspection verifier**

Add a conditional `BearerTokenVerifier` implementation that posts `token=<value>` to the introspection endpoint, adds Basic auth only when configured, requires JSON `active=true`, maps configured fields, and returns empty for inactive/non-2xx/malformed responses.

- [x] **Step 3: Run focused GREEN**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OAuthIntrospectionBearerVerifierTest,OpsAuthConfigControllerTest,OpsIntegrationContractControllerTest
```

Expected: pass.

## Task 3: Docs And Final Verification

- [x] **Step 1: Update ops contract and docs**

Mark token introspection baseline true in integration contract, update auth-config mode/booleans, and document remaining non-goals.

- [x] **Step 2: Run final verification**

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-oauth-token-introspection-baseline.md
```

Expected: Maven and typecheck pass; diff check passes; token scan exits 1 with no matches; unchecked-plan scan exits 1 after all steps are checked.
