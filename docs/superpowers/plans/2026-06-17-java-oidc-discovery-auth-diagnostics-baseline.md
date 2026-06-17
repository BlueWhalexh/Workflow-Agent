# Java OIDC Discovery Auth Diagnostics Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Java backend OIDC baseline with issuer discovery and redacted auth configuration diagnostics without adding OAuth login/session flows.

**Architecture:** Keep `BearerTokenAuthenticationFilter` unchanged and continue using `BearerTokenVerifier` as the HTTP auth adapter. Extend `BackendProperties.Oidc` with `issuer-uri`, resolve `jwks_uri` from `${issuer-uri}/.well-known/openid-configuration` when direct `jwks-uri` is absent, and fail fast for ambiguous or malformed OIDC configuration. Add a read-only ops endpoint that exposes only redacted auth mode metadata and claim names.

**Tech Stack:** Java 17/21-compatible source, Spring Boot MVC, Spring Security OAuth2 JOSE, Jackson, JUnit 5, MockMvc, local JDK `HttpServer` discovery/JWKS fixtures.

---

## Scope

In:

- `my-workflow.backend.oidc.issuer-uri` discovery support.
- Direct `jwks-uri` compatibility.
- Config validation for `issuer-uri` / `jwks-uri` conflict and malformed discovery payload.
- Redacted `GET /v1/ops/auth-config` diagnostics.
- Tests proving no raw token, Authorization material, `issuerUri`, or `jwksUri` values are exposed.

Out:

- OAuth login, callback, cookies, sessions, CSRF flow, refresh tokens.
- Token introspection.
- External user/team directory sync.
- Real IdP smoke.
- Frontend auth UI.

## Files

- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/security/ConfigurableOidcBearerTokenVerifier.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/ops/OpsController.java`
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/security/OidcJwtBearerVerifierTest.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsAuthConfigControllerTest.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan, checking off completed steps.

## Task 1: RED Discovery And Diagnostics Contract

**Files:**

- Modify: `backend/src/test/java/com/myworkflow/agent/backend/security/OidcJwtBearerVerifierTest.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/ops/OpsAuthConfigControllerTest.java`

- [x] **Step 1: Write failing verifier tests**

Add tests that prove:

- `issuer-uri` discovery can resolve `jwks_uri` and validate a signed JWT.
- Setting both `issuer-uri` and `jwks-uri` is rejected.
- Discovery payload without `jwks_uri` is rejected.

- [x] **Step 2: Write failing ops diagnostics tests**

Add MockMvc coverage for:

- `GET /v1/ops/auth-config` returns `java-backend-api.v1`.
- Discovery mode is reported as `oidc-discovery`.
- Claim names and configured booleans are present.
- Raw `issuerUri`, `jwksUri`, token, secret, and Authorization material are absent.

- [x] **Step 3: Run RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OidcJwtBearerVerifierTest,OpsAuthConfigControllerTest
```

Expected before implementation: fail because `BackendProperties.Oidc` has no `issuerUri` field/constructor and `/v1/ops/auth-config` does not exist.

Actual RED: failed at test compilation because `BackendProperties.Oidc` did not yet accept the new `issuerUri` constructor argument. This confirmed the new discovery contract was not implemented yet.

## Task 2: Implement Discovery And Diagnostics

**Files:**

- Modify implementation files listed above.

- [x] **Step 1: Add OIDC issuer-uri configuration**

Extend `BackendProperties.Oidc` with `issuerUri`, normalize blanks, and expose helper methods for:

- configured mode: `disabled`, `oidc-jwks`, `oidc-discovery`
- discovery enabled
- validation issuer

- [x] **Step 2: Add verifier discovery resolution**

Update `ConfigurableOidcBearerTokenVerifier` so it:

- Creates a bean when either `jwks-uri` or `issuer-uri` is configured.
- Rejects both `jwks-uri` and `issuer-uri` together.
- Resolves `${issuer-uri}/.well-known/openid-configuration`.
- Requires a non-blank `jwks_uri`.
- Keeps existing issuer/audience/token validation.
- Does not log or expose token material.

- [x] **Step 3: Add redacted ops auth-config endpoint**

Add `GET /v1/ops/auth-config` returning:

```text
mode, discoveryEnabled, issuerConfigured, jwksUriConfigured, audienceConfigured,
userIdClaim, teamIdClaim, displayNameClaim
```

Do not return raw URI values, token, secret, Authorization, or provider payload.

- [x] **Step 4: Run focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OidcJwtBearerVerifierTest,OpsAuthConfigControllerTest,OpsControllerTest,OpenApiContractTest
```

Expected: pass.

Actual focused GREEN: 14 Java tests passed after marking the production verifier constructor with `@Autowired` to resolve Spring's constructor selection.

## Task 3: Docs, Reports, Static Verification

**Files:**

- Modify docs listed in Files.

- [x] **Step 1: Update architecture spec and phase audit**

Document J37A as issuer discovery plus redacted auth diagnostics. Keep full OAuth login/session, token introspection, and external directory sync as remaining gaps.

- [x] **Step 2: Update delivery report**

Record RED, focused GREEN, full Java, typecheck, static checks, token scan, and dirty-worktree note for unrelated frontend files.

- [x] **Step 3: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-oidc-discovery-auth-diagnostics-baseline.md
```

Expected: Maven and typecheck pass; diff check passes; token scan exits 1 with no matches; unchecked-plan scan exits 1 after all steps are checked.

Actual final verification: Maven full Java passed with 137 tests; `npm run typecheck` passed; `git diff --check` passed; token scan had no matches; unchecked-plan scan is clean after this final checkbox update.
