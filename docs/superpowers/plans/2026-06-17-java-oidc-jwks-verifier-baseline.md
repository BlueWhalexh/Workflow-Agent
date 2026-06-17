# Java OIDC JWKS Verifier Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a config-backed OIDC/JWKS bearer verifier baseline so the production profile can validate signed JWTs and map trusted claims to `BackendPrincipal`.

**Architecture:** Keep the HTTP/auth boundary unchanged: `BearerTokenAuthenticationFilter` only consumes the `BearerTokenVerifier` SPI. Add a conditional verifier bean that uses Spring Security JWT/JWK validation when `my-workflow.backend.oidc.jwks-uri` is configured, and keep invalid tokens fail-closed by returning no principal. Claim mapping stays local and explicit; no session, SSO UI, discovery, token introspection, or external directory sync is introduced.

**Tech Stack:** Java 17/21-compatible source, Spring Boot 3.5, Spring Security OAuth2 JOSE, JUnit 5, MockMvc, local JDK `HttpServer` JWKS fixture.

---

## Scope

In:

- Config-backed OIDC/JWKS verifier bean behind the existing `BearerTokenVerifier` SPI.
- Properties for issuer, JWKS URI, optional audience, user id claim, team id claim, and display name claim.
- RS256 JWT validation through JWKS in tests using local ephemeral HTTP, not a real IdP.
- Fail-closed behavior for invalid signature, wrong issuer/audience, expired token, and missing required claims.
- No bearer token echo in public error responses or docs.

Out:

- OIDC discovery, token introspection, SSO login/session/cookies, refresh tokens.
- External user/team directory sync and production role mapping.
- Real IdP smoke or real provider credentials.
- API behavior changes outside existing bearer principal resolution.

## Files

- Create: `backend/src/main/java/com/myworkflow/agent/backend/security/ConfigurableOidcBearerTokenVerifier.java`
- Create: `backend/src/test/java/com/myworkflow/agent/backend/security/OidcJwtBearerVerifierTest.java`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: this plan, checking off completed steps.

## Task 1: RED OIDC/JWKS Verifier Contract

**Files:**

- Create: `backend/src/test/java/com/myworkflow/agent/backend/security/OidcJwtBearerVerifierTest.java`

- [x] **Step 1: Write failing verifier tests**

Add tests that prove:

- A valid RS256 JWT from the configured local JWKS maps `sub`, `team_id`, and `name` to `BackendPrincipal`.
- A token with the wrong `aud` claim is rejected.
- A token missing the configured team claim is rejected.

- [x] **Step 2: Run RED command**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OidcJwtBearerVerifierTest
```

Actual before implementation: failed because `ConfigurableOidcBearerTokenVerifier` and `BackendProperties.Oidc` did not exist.

## Task 2: Implement Conditional Verifier

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/config/BackendProperties.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/security/ConfigurableOidcBearerTokenVerifier.java`

- [x] **Step 1: Add Spring Security JWT dependency**

Add `spring-security-oauth2-jose` as a production dependency so `NimbusJwtDecoder` can validate JWKS-backed JWT signatures.

- [x] **Step 2: Add OIDC properties**

Extend `BackendProperties` with:

```text
my-workflow.backend.oidc.issuer
my-workflow.backend.oidc.jwks-uri
my-workflow.backend.oidc.audience
my-workflow.backend.oidc.user-id-claim
my-workflow.backend.oidc.team-id-claim
my-workflow.backend.oidc.display-name-claim
```

Required claim defaults:

```text
user-id-claim=sub
team-id-claim=team_id
display-name-claim=name
```

- [x] **Step 3: Add verifier bean**

Create `ConfigurableOidcBearerTokenVerifier`:

- Annotate with `@Component`.
- Gate bean creation with `@ConditionalOnProperty(name = "my-workflow.backend.oidc.jwks-uri")`.
- Decode tokens with `NimbusJwtDecoder.withJwkSetUri(...)`.
- Validate issuer when configured.
- Validate audience when configured.
- Return `Optional.empty()` on decode/validation/mapping failures.
- Build `BackendPrincipal` only when required mapped claims are non-blank.

- [x] **Step 4: Run focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=OidcJwtBearerVerifierTest,BearerIdentityAdapterTest,ProductionIdentityHardeningTest
```

Actual: passed; 11 Java tests passed.

## Task 3: Docs, Reports, Static Verification

**Files:**

- Modify docs listed in Files.

- [x] **Step 1: Update architecture spec**

Add J36A to the current-status line and document that OIDC/JWKS validation exists as a configurable bearer verifier baseline, while discovery, sessions, token introspection, and external directory sync remain out of scope.

- [x] **Step 2: Update completion audit**

Move Full OIDC/OAuth from "no real JWKS validation" to "JWKS verifier baseline exists; full OAuth/OIDC still partial".

- [x] **Step 3: Update delivery report**

Record RED, focused GREEN, full Java, typecheck, diff check, token scan, and unchecked-plan scan evidence.

- [x] **Step 4: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm run typecheck
git diff --check
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-oidc-jwks-verifier-baseline.md
```

Actual: Maven and typecheck passed; diff check passed; token scan exited 1 with no matches; unchecked-plan scan exited 1 after all steps were checked.
