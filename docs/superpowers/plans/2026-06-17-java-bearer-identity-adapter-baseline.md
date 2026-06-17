# Java Bearer Identity Adapter Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build J32A bearer identity adapter baseline so production-like profiles can authenticate through a verifier SPI instead of only failing closed.

**Architecture:** Add a `BearerTokenVerifier` SPI that maps an opaque bearer token to `BackendPrincipal`. Add a servlet filter that reads `Authorization: Bearer ...`, invokes the verifier if present, and writes a `BackendPrincipal` into `SecurityContext`; existing `PrincipalProvider` and workspace role guards continue to be the source of downstream authorization behavior.

**Tech Stack:** Java 21, Spring Boot, Spring Security, MockMvc, JUnit, Maven.

---

### Task 1: RED Bearer Identity Tests

**Files:**
- Create: `backend/src/test/java/com/myworkflow/agent/backend/security/BearerIdentityAdapterTest.java`

- [x] **Step 1: Write the failing tests**

```java
@SpringBootTest(
    classes = {
        BackendApplication.class,
        BearerIdentityAdapterTest.TestBearerVerifierConfig.class
    },
    properties = {
        "spring.profiles.active=prod",
        "my-workflow.backend.dev-principal.user-id=prod-fallback-user",
        "my-workflow.backend.dev-principal.team-id=prod-fallback-team",
        "my-workflow.backend.dev-principal.display-name=Prod Fallback"
    }
)
@AutoConfigureMockMvc
class BearerIdentityAdapterTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void prodProfileUsesVerifiedBearerPrincipal() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer valid-user-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("bearer-user"))
        .andExpect(jsonPath("$.data.teamId").value("bearer-team"))
        .andExpect(jsonPath("$.data.displayName").value("Bearer User"))
        .andExpect(jsonPath("$.data.token").doesNotExist())
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  @Test
  void prodProfilePrefersVerifiedBearerPrincipalOverDevHeaders() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer valid-user-token")
            .header("X-Dev-User-Id", "spoofed-user")
            .header("X-Dev-Team-Id", "spoofed-team")
            .header("X-Dev-Display-Name", "Spoofed User"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value("bearer-user"))
        .andExpect(jsonPath("$.data.teamId").value("bearer-team"))
        .andExpect(jsonPath("$.data.displayName").value("Bearer User"))
        .andExpect(jsonPath("$.data.token").doesNotExist());
  }

  @Test
  void prodProfileRejectsInvalidBearerWithoutEchoingToken() throws Exception {
    mockMvc.perform(get("/v1/me")
            .header("Authorization", "Bearer invalid-user-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.error.message").value("Authentication is required"))
        .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invalid-user-token"))));
  }

  @TestConfiguration
  static class TestBearerVerifierConfig {

    @Bean
    BearerTokenVerifier testBearerTokenVerifier() {
      return (token) -> {
        if ("valid-user-token".equals(token)) {
          return Optional.of(new BackendPrincipal("bearer-user", "bearer-team", "Bearer User"));
        }
        return Optional.empty();
      };
    }
  }
}
```

- [x] **Step 2: Run RED**

Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=BearerIdentityAdapterTest`

Expected: FAIL because `BearerTokenVerifier` and bearer auth filter do not exist yet.

### Task 2: Minimal Bearer Adapter Implementation

**Files:**
- Create: `backend/src/main/java/com/myworkflow/agent/backend/security/BearerTokenVerifier.java`
- Create: `backend/src/main/java/com/myworkflow/agent/backend/security/BearerTokenAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/security/BackendSecurityConfig.java`

- [x] **Step 1: Add `BearerTokenVerifier` SPI**

The interface should accept the opaque bearer token value and return `Optional<BackendPrincipal>`.

- [x] **Step 2: Add bearer auth filter**

The filter should:
- Read only `Authorization` headers with the `Bearer ` prefix.
- Trim and reject blank bearer values.
- Use an injected `ObjectProvider<BearerTokenVerifier>` so production can run without a verifier bean and fail closed through the existing principal provider.
- Set `UsernamePasswordAuthenticationToken` with `BackendPrincipal` only when the verifier returns a principal.
- Never write or log raw bearer tokens.

- [x] **Step 3: Wire filter order**

Register bearer auth before the dev header filter. In production-like profiles the dev filter will not override the verified bearer principal. In local/default profiles existing dev behavior remains unchanged.

- [x] **Step 4: Run focused GREEN**

Run: `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test -Dtest=BearerIdentityAdapterTest,ProductionIdentityHardeningTest,SecurityPrincipalControllerTest,IdentityControllerTest`

Expected: PASS.

### Task 3: Docs And Evidence

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/java-backend-phase-one-completion-audit.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-17-java-bearer-identity-adapter-baseline.md`

- [x] **Step 1: Update docs**

Document J32A as an OIDC-ready bearer verifier baseline. Explicitly state it is not real OIDC/OAuth discovery, JWKS validation, token introspection, SSO, session management, invite flow, or directory sync.

- [x] **Step 2: Run full verification**

Run:
- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
- `npm run typecheck`
- `git diff --check`
- `rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'`
- `rg -n -- "^- \\[ \\]" docs/superpowers/plans/2026-06-17-java-bearer-identity-adapter-baseline.md`

Expected: all pass, no token matches, no unchecked J32A plan tasks remain.
