# Java Remote Runner Signature Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional HMAC verification baseline for remote HTTP runner result envelopes so Java can reject tampered signed remote results without changing the public backend response contract.

**Architecture:** Keep `agent-remote-runner-result.v1` as the transport envelope and keep the nested `agent-backend-response.v1` as the only backend-facing runtime result shape. When `my-workflow.backend.agent-worker.remote-http.signature-secret` is configured, `RemoteHttpAgentWorker` requires `signatureKind=hmac-sha256` and validates `signature` over a fixed-field, UTF-8 length-prefixed canonical public envelope/result payload. When no secret is configured, existing `signatureKind=unsigned-local-spike` remains accepted only for local spike tests.

**Tech Stack:** Java 21 target, Spring Boot configuration injection, JDK `Mac` / `HmacSHA256`, JDK `MessageDigest.isEqual`, JUnit 5, local JDK `HttpServer` test fixture.

---

### Task 1: RED Test For HMAC-Signed Remote Runner Envelope

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorkerTest.java`

- [x] **Step 1: Add failing signed-envelope tests**

Add a test that constructs `RemoteHttpAgentWorker` with a shared test secret, accepts a correctly signed envelope, and rejects a tampered signed envelope:

```java
@Test
void verifiesHmacSignedRemoteRunnerEnvelopeAndRejectsTampering() throws Exception {
  String signatureSecret = "runner-secret-for-test";
  try (RemoteWorkerServer server = RemoteWorkerServer.start(
      objectMapper,
      requestBody -> signedEnvelope(requestBody, signatureSecret, "Signed remote answer", false)
  )) {
    RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

    AgentWorkerResponse response = worker.run(new AgentWorkerRequest(
        "run_remote_signed",
        "/server/workspaces/ws_1/content",
        "总结当前知识库",
        "deterministic-open-agent",
        false,
        false,
        null
    ));

    assertThat(response.displayText()).isEqualTo("Signed remote answer");
    assertThat(server.lastRequestBody()).doesNotContain(signatureSecret);
  }

  try (RemoteWorkerServer server = RemoteWorkerServer.start(
      objectMapper,
      requestBody -> signedEnvelope(requestBody, signatureSecret, "Tampered remote answer", true)
  )) {
    RemoteHttpAgentWorker worker = new RemoteHttpAgentWorker(objectMapper, server.url(), 2_000, signatureSecret);

    assertThatThrownBy(() -> worker.run(new AgentWorkerRequest(
        "run_remote_signed_bad",
        "/server/workspaces/ws_1/content",
        "总结当前知识库",
        "deterministic-open-agent",
        false,
        false,
        null
    )))
        .isInstanceOf(AgentWorkerException.class)
        .hasMessageContaining("invalid remote runner envelope signature");
  }
}
```

Add helper methods in the test file that mirror the production canonical string for the local fixture:

```java
private Map<String, Object> signedEnvelope(
    String requestBody,
    String signatureSecret,
    String displayText,
    boolean tamperAfterSigning
) {
  try {
    @SuppressWarnings("unchecked")
    Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
    String runId = (String) request.get("runId");
    Map<String, Object> result = Map.ofEntries(
        entry("schemaVersion", "agent-backend-response.v1"),
        entry("runId", runId),
        entry("status", "SUCCEEDED"),
        entry("outputKind", "answer"),
        entry("displayText", displayText),
        entry("requiresConfirmation", false),
        entry("requiresApproval", false),
        entry("artifactRefs", List.of(".agent-runs/%s/remote.json".formatted(runId))),
        entry("wroteWorkspace", false),
        entry("targetWorkspacePaths", List.of())
    );
    String signature = testSignature(signatureSecret, result);
    Map<String, Object> signedResult = tamperAfterSigning
        ? Map.ofEntries(
            entry("schemaVersion", "agent-backend-response.v1"),
            entry("runId", runId),
            entry("status", "SUCCEEDED"),
            entry("outputKind", "answer"),
            entry("displayText", displayText + " modified"),
            entry("requiresConfirmation", false),
            entry("requiresApproval", false),
            entry("artifactRefs", List.of(".agent-runs/%s/remote.json".formatted(runId))),
            entry("wroteWorkspace", false),
            entry("targetWorkspacePaths", List.of())
        )
        : result;
    return Map.of(
        "schemaVersion", "agent-remote-runner-result.v1",
        "workerKind", "REMOTE_RUNNER",
        "signatureKind", "hmac-sha256",
        "signature", signature,
        "result", signedResult
    );
  } catch (Exception exception) {
    throw new IllegalStateException("Unable to create signed remote worker response", exception);
  }
}
```

- [x] **Step 2: Verify RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test
```

Expected: fail because `RemoteHttpAgentWorker` does not yet accept a signature-secret constructor and does not validate `signatureKind=hmac-sha256`.

### Task 2: Minimal Remote Runner Signature Verification

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorker.java`

- [x] **Step 1: Add signature configuration and envelope field**

Add a Spring property:

```java
@Value("${my-workflow.backend.agent-worker.remote-http.signature-secret:}") String signatureSecret
```

Store it as a nullable/blank-normalized field. Extend the envelope record with:

```java
String signature
```

Keep `unsigned-local-spike` accepted only when no secret is configured.

- [x] **Step 2: Add HMAC verification helpers**

Implement `hmac-sha256:<base64>` signatures using JDK APIs only:

```java
private static String canonicalSignaturePayload(RemoteRunnerResultEnvelope envelope) {
  AgentWorkerResponse result = envelope.result();
  StringBuilder payload = new StringBuilder();
  appendScalar(payload, "envelope.schemaVersion", envelope.schemaVersion());
  appendScalar(payload, "envelope.workerKind", envelope.workerKind());
  appendScalar(payload, "envelope.signatureKind", envelope.signatureKind());
  appendScalar(payload, "result.schemaVersion", result.schemaVersion());
  appendScalar(payload, "result.runId", result.runId());
  appendScalar(payload, "result.status", result.status());
  appendScalar(payload, "result.outputKind", result.outputKind());
  appendScalar(payload, "result.displayText", result.displayText());
  appendBoolean(payload, "result.requiresConfirmation", result.requiresConfirmation());
  appendBoolean(payload, "result.requiresApproval", result.requiresApproval());
  appendList(payload, "result.artifactRefs", result.artifactRefs());
  appendBoolean(payload, "result.wroteWorkspace", result.wroteWorkspace());
  appendList(payload, "result.targetWorkspacePaths", result.targetWorkspacePaths());
  return payload.toString();
}
```

Each scalar uses fixed field name, UTF-8 byte length, and value bytes; each list includes list length plus length-prefixed items. Compare expected and received signatures with `MessageDigest.isEqual(...)`. Do not put the secret in exceptions, logs, response objects, request payloads, docs, or test snapshots.

- [x] **Step 3: Verify focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test
```

Expected: all `RemoteHttpAgentWorkerTest` cases pass.

### Task 3: Docs, Report, And Validation

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Update current-truth spec**

Record Phase J20A as delivered:

- `remote-http.signature-secret` enables signed remote runner envelope verification;
- signed envelopes use `signatureKind=hmac-sha256` and `signature=hmac-sha256:<base64>`;
- signature input is the remote envelope identity plus nested public `agent-backend-response.v1` fields;
- `unsigned-local-spike` remains accepted only when no signature secret is configured;
- this is not runner registration, heartbeat, lease, artifact upload, remote cancellation, remote workspace mount, runner authorization, or runner-scoped secret distribution.

- [x] **Step 2: Update delivery report**

Append Phase J20A with scope, RED evidence, focused/adjacent/full verification, token scan, and evidence boundaries. Explicitly state the tests use local JDK `HttpServer`, not a real remote runner service.

- [x] **Step 3: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-remote-runner-signature-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java focused/adjacent/full tests pass, TS tests and typecheck pass, diff/whitespace/conflict/token scans return no matches.

### Task 4: Review Follow-Up For Canonical Payload Safety

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorkerTest.java`
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/run/RemoteHttpAgentWorker.java`
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Add RED collision regression**

Add `rejectsDelimiterCollisionTamperingInSignedEnvelope`, signing one result and returning a semantically different result that collides under newline-delimited canonicalization.

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test
```

Expected: fail because old delimiter-joined canonicalization accepts the tampered result and no exception is thrown.

- [x] **Step 2: Add secret-configured negative cases**

Add `requiresSignedEnvelopeWhenSignatureSecretIsConfigured` to reject:

- `signatureKind=unsigned-local-spike` when a secret is configured;
- `signatureKind=hmac-sha256` with missing `signature`;
- malformed/wrong-prefix signature values.

- [x] **Step 3: Implement length-prefixed canonical payload**

Change production and test signing helpers to encode fixed field names, UTF-8 byte lengths, list lengths, and length-prefixed list items before HMAC.

- [x] **Step 4: Verify review follow-up GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest test
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=RemoteHttpAgentWorkerTest,RemoteHttpAgentWorkerControllerTest test
```

Expected: focused 5 tests pass; adjacent 6 tests pass.
