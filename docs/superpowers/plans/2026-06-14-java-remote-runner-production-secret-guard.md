# Java Remote Runner Production Secret Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent accidental production `remote-http` runner usage without a configured result-envelope signing secret while preserving the no-secret `unsigned-local-spike` path for local/default profile tests.

**Architecture:** Keep J20A's signed envelope verification unchanged. Add a small profile-aware configuration guard inside the Java `RemoteHttpAgentWorker` construction path: if the active Spring profiles include `prod` or `production` and `my-workflow.backend.agent-worker.kind=remote-http`, then `my-workflow.backend.agent-worker.remote-http.signature-secret` must be non-blank. This guard does not introduce runner identity, key rotation, mTLS, credential DB, public API changes, or DB schema changes.

**Tech Stack:** Java 21 target, Spring Boot `Environment`, JUnit 5, AssertJ, existing Maven test workflow.

---

### Task 1: RED Test For Production Remote Runner Secret Requirement

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorkerTest.java`

- [x] **Step 1: Add failing production-profile constructor test**

Add a focused unit test that constructs the worker with a production-like active profile and blank signature secret:

```java
@Test
void rejectsProductionRemoteHttpWorkerWithoutSignatureSecret() {
  assertThatThrownBy(() -> new RemoteHttpAgentWorker(
      objectMapper,
      serverUrlPlaceholder(),
      2_000,
      "",
      List.of("production")
  ))
      .isInstanceOf(AgentWorkerException.class)
      .hasMessageContaining("Remote HTTP agent worker signature secret is required for production profiles");
}

private static String serverUrlPlaceholder() {
  return "http://127.0.0.1:1/run";
}
```

Add a companion assertion that local/default profiles still preserve the J20A spike behavior:

```java
RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(
    objectMapper,
    serverUrlPlaceholder(),
    2_000,
    "",
    List.of("default")
);
assertThat(worker.workerKind()).isEqualTo("REMOTE_RUNNER");
```

- [x] **Step 2: Verify RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test
```

Expected: fail at compile because the profile-aware test constructor does not exist yet, or fail because production/blank secret is not rejected.

### Task 2: Minimal Profile-Aware Guard

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorker.java`

- [x] **Step 1: Inject active Spring profiles**

Update the Spring constructor to accept `org.springframework.core.env.Environment` and pass `List.of(environment.getActiveProfiles())` to the internal constructor path.

Keep the existing package-private test constructor for local/default behavior and add a package-private overload:

```java
RemoteHttpAgentWorker(
    ObjectMapper objectMapper,
    String endpointUrl,
    long timeoutMs,
    String signatureSecret,
    List<String> activeProfiles
)
```

- [x] **Step 2: Enforce production-only secret requirement**

Normalize profiles case-insensitively. Treat `prod` and `production` as production profiles:

```java
private static void requireProductionSignatureSecret(String signatureSecret, List<String> activeProfiles) {
  boolean productionProfile = activeProfiles.stream()
      .filter(profile -> profile != null)
      .map(String::trim)
      .map(String::toLowerCase)
      .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
  if (productionProfile && (signatureSecret == null || signatureSecret.isBlank())) {
    throw new AgentWorkerException("Remote HTTP agent worker signature secret is required for production profiles");
  }
}
```

Call this before assigning `this.signatureSecret`.

- [x] **Step 3: Verify focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test
```

Expected: all `RemoteHttpAgentWorkerTest` cases pass.

### Task 3: Adjacent Spring Wiring Regression

**Files:**
- No code changes. Reuse: `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorkerControllerTest.java`

- [x] **Step 1: Keep scope within five files**

The implementation scope is capped at five files for this phase. Do not add a separate `RemoteHttpAgentWorkerControllerTest` production-profile context failure test in J21A; the constructor-level unit test proves the guard behavior and the existing controller test proves default-profile Spring wiring remains compatible.

- [x] **Step 2: Verify adjacent tests**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test
```

Expected: remote worker unit and controller tests pass.

### Task 4: Docs, Report, And Final Validation

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Update current-truth spec**

Record Phase J21A:

- production profiles `prod` / `production` require non-blank `remote-http.signature-secret`;
- local/default profiles still allow `unsigned-local-spike` for local fixture evidence;
- this is a guard against accidental production misconfiguration, not runner identity, key rotation, KMS/secret manager, mTLS, registration, heartbeat, lease, artifact upload, remote cancellation, or remote workspace mount.

- [x] **Step 2: Update delivery report**

Append Phase J21A with RED evidence, focused/adjacent/full verification, and evidence boundaries. State that no real remote runner or real provider call was executed.

- [x] **Step 3: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-remote-runner-production-secret-guard.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java focused/adjacent/full tests pass, TS tests and typecheck pass, diff/whitespace/conflict/token scans return no matches.
