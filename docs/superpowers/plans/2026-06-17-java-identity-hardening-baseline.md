# Java Identity Hardening Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build J31A identity hardening baseline so production-like profiles no longer accept local dev identity headers or silently fall back to the configured dev principal.

**Architecture:** Keep local/dev convenience behavior unchanged for the current test harness. Add a small auth-mode policy used by the dev header filter and principal provider, and fail closed with a public API error envelope when no real authentication exists in production-like profiles.

**Tech Stack:** Java 21, Spring Boot, Spring Security, MockMvc, JUnit, Maven.

---

### Task 1: RED Security Tests

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/security/ProductionIdentityHardeningTest.java`

- [x] **Step 1: Write the failing tests**

```java
@SpringBootTest(
    classes = BackendApplication.class,
    properties = {
        "spring.profiles.active=prod",
        "my-workflow.backend.dev-principal.user-id=prod-fallback-user",
        "my-workflow.backend.dev-principal.team-id=prod-fallback-team",
        "my-workflow.backend.dev-principal.display-name=Prod Fallback"
    }
)
@AutoConfigureMockMvc
class ProductionIdentityHardeningTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void prodProfileRejectsDevHeadersInsteadOfTrustingSpoofedPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("X-Dev-User-Id", "spoofed-user")
            .header("X-Dev-Team-Id", "spoofed-team")
            .header("X-Dev-Display-Name", "Spoofed User"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }

  @Test
  void prodProfileDoesNotFallBackToConfiguredDevPrincipalWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
        .andExpect(jsonPath("$.ok").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }
}
```

- [x] **Step 2: Run RED**

Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=ProductionIdentityHardeningTest`

Expected: FAIL because current code still accepts `X-Dev-*` and returns the configured dev principal in every profile.

### Task 2: Minimal Auth Mode Implementation

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/security/BackendAuthMode.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/identity/AuthenticationRequiredException.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/security/DevHeaderAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/DevPrincipalProvider.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/api/GlobalApiExceptionHandler.java`

- [x] **Step 1: Add auth-mode policy**

Production-like profiles are `prod` and `production`. Dev identity is enabled for every other current local/default/test profile.

- [x] **Step 2: Fail closed in production-like profiles**

The dev header filter must not set a `BackendPrincipal` when dev identity is disabled. If `X-Dev-*` headers are present in production-like profiles, the request surfaces `401` with `AUTHENTICATION_REQUIRED` instead of trusting those headers.

- [x] **Step 3: Remove production fallback**

The principal provider should return the configured dev principal only when dev identity is enabled. Otherwise it should throw `AuthenticationRequiredException`.

- [x] **Step 4: Run GREEN focused tests**

Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=ProductionIdentityHardeningTest,SecurityPrincipalControllerTest,IdentityControllerTest`

Expected: PASS.

### Task 3: Docs And Evidence

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-17-java-identity-hardening-baseline.md`

- [x] **Step 1: Update architecture spec**

Document J31A as a production-like profile fail-closed baseline. Explicitly state this is not full OIDC/OAuth, SSO, session management, invite flow, or user directory CRUD.

- [x] **Step 2: Update completion audit and delivery report**

Record RED/focused/full/typecheck/token-scan evidence and the remaining production identity gaps.

- [x] **Step 3: Run full verification**

Run:
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
- `npm run typecheck`
- `git diff --check`
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
- `rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-identity-hardening-baseline.md`

Expected: all pass, no token matches, no unchecked plan tasks remain.
