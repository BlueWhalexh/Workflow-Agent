# Java Current Team Discovery Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narrow `GET /v1/teams` API that lets backend clients discover the current authenticated principal's team without exposing a global user/team directory.

**Architecture:** Reuse `PrincipalProvider` as the current local-dev source of truth. Keep `IdentityController` responsible for `/v1/me` and `/v1/teams`, and return only stable team metadata derived from the current principal.

**Tech Stack:** Spring Boot MVC, Java records, MockMvc.

---

### Task 1: Current Team API

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/identity/IdentityControllerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/identity/IdentityController.java`

- [x] **Step 1: Write the failing test**

Add a test to `IdentityControllerTest`:

```java
@Test
void teamsReturnsOnlyCurrentPrincipalTeam() throws Exception {
  mockMvc.perform(get("/v1/teams"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.schemaVersion").value("java-backend-api.v1"))
      .andExpect(jsonPath("$.ok").value(true))
      .andExpect(jsonPath("$.data.length()").value(1))
      .andExpect(jsonPath("$.data[0].teamId").value("team_dev"))
      .andExpect(jsonPath("$.data[0].name").value("team_dev"))
      .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
      .andExpect(jsonPath("$.data[0].workspaceRoot").doesNotExist())
      .andExpect(jsonPath("$.data[0].serverStorageRef").doesNotExist())
      .andExpect(jsonPath("$.data[0].token").doesNotExist())
      .andExpect(jsonPath("$.error").doesNotExist());
}
```

- [x] **Step 2: Run focused RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=IdentityControllerTest test
```

Expected: FAIL with 404 `NOT_FOUND` for `GET /v1/teams`, while the existing `/v1/me` test still passes.

- [x] **Step 3: Implement minimal controller method**

Add to `IdentityController`:

```java
@GetMapping("/teams")
public ApiEnvelope<List<TeamResponse>> teams() {
  BackendPrincipal principal = principalProvider.currentPrincipal();
  return ApiEnvelope.ok(List.of(new TeamResponse(
      principal.teamId(),
      principal.teamId(),
      "ACTIVE"
  )));
}

public record TeamResponse(
    String teamId,
    String name,
    String status
) {
}
```

Add `java.util.List` import.

- [x] **Step 4: Run focused GREEN**

Run the same focused Maven command. Expected: PASS.

### Task 2: Documentation And Verification

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Modify: `docs/superpowers/plans/2026-06-14-java-current-team-discovery-baseline.md`

- [x] **Step 1: Update architecture spec**

Record J13A as delivered: `GET /v1/teams` returns the current principal's team only; full user/team directory CRUD, team member listing, invitations, and cross-team discovery remain future work.

- [x] **Step 2: Update delivery report**

Add RED/GREEN/full validation evidence and state that no real provider call was made.

- [x] **Step 3: Run validation**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-current-team-discovery-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TypeScript tests, and typecheck pass; diff check passes; whitespace/conflict/token scans return no matches.

### Self-Review

- Spec coverage: covers current team discovery only. Full user/team directory CRUD, team member listing, invitations, OIDC/OAuth, owner transfer, audit pagination/export, SSE/WebSocket, credential DB/secret manager, and production remote runner remain out of scope.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: response record is `TeamResponse` with `teamId`, `name`, and `status`, matching the test expectations.
