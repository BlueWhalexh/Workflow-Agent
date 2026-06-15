# Java Audit Record Digest Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a stable public digest to audit metadata responses so audit list/export consumers can verify line-level identity without receiving raw provider payloads, workspace internals, or token material.

**Architecture:** Reuse `AuditController.toResponse(...)` as the single mapping point for both JSON audit list and NDJSON export. Compute `recordDigest` with JDK `MessageDigest` over a canonical string made only from existing public audit metadata fields. This is a digest baseline, not a persisted signed audit record or tamper-evident chain.

**Tech Stack:** Java 21 target, Spring Boot MVC, JDK `MessageDigest` / `HexFormat`, JUnit 5, MockMvc.

---

### Task 1: RED Test For Public Audit Digest

**Files:**
- Modify: `backend/src/test/java/com/myworkflow/agent/backend/audit/AuditControllerTest.java`

- [x] **Step 1: Add failing digest assertions**

In `ownerFiltersAndPagesWorkspaceAuditEvents`, after the existing filtered result assertions, add:

```java
.andExpect(jsonPath("$.data[0].recordDigest").isString())
```

Then read `$.data[0].recordDigest` from the filtered response body and assert:

```java
String digest = JsonPath.read(filtered.getResponse().getContentAsString(), "$.data[0].recordDigest");
assertThat(digest).matches("sha256:[0-9a-f]{64}");
```

In `ownerExportsFilteredAuditEventsAsNdjsonWhileViewerIsDenied`, add an assertion that the single NDJSON line contains `"recordDigest":"sha256:` and still does not contain `workspaceRoot`, `serverStorageRef`, `Authorization`, `rawProvider`, `token`, `apiKey`, or `access_token`.

- [x] **Step 2: Verify RED**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: fail because `recordDigest` is absent from audit list/export responses. Existing controller setup should still compile.

### Task 2: Minimal Digest Implementation

**Files:**
- Modify: `backend/src/main/java/com/myworkflow/agent/backend/audit/AuditController.java`

- [x] **Step 1: Add digest fields and imports**

Add JDK imports:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
```

Extend `AuditEventResponse` with:

```java
String recordDigest
```

- [x] **Step 2: Compute digest in `toResponse`**

Update `toResponse(AuditEventRecord event)` so the new record field is:

```java
recordDigest(event)
```

Add helper methods:

```java
private static String recordDigest(AuditEventRecord event) {
  String canonical = String.join("\n",
      nullToEmpty(event.auditEventId()),
      nullToEmpty(event.actorUserId()),
      nullToEmpty(event.teamId()),
      nullToEmpty(event.workspaceId()),
      nullToEmpty(event.runId()),
      nullToEmpty(event.eventType()),
      nullToEmpty(event.message()),
      event.createdAt().toString()
  );
  try {
    byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.getBytes(StandardCharsets.UTF_8));
    return "sha256:" + HexFormat.of().formatHex(digest);
  } catch (NoSuchAlgorithmException exception) {
    throw new IllegalStateException("SHA-256 digest algorithm is unavailable", exception);
  }
}

private static String nullToEmpty(String value) {
  return value == null ? "" : value;
}
```

Implementation constraints:

- do not add a dependency;
- do not add a schema migration;
- do not include raw provider payload, raw audit payload, internal path, Authorization header, token, or runtime-private source in the digest input or output;
- do not describe this digest as a signed audit record.

- [x] **Step 3: Verify focused GREEN**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml -Dtest=AuditControllerTest test
```

Expected: all `AuditControllerTest` cases pass.

### Task 3: Docs, Report, And Validation

**Files:**
- Modify: `docs/architecture/java-team-backend-platform-spec.md`
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [x] **Step 1: Update current-truth spec**

Record Phase J19A as delivered:

- `recordDigest` is included in audit list and audit export public responses;
- digest format is `sha256:<64 lowercase hex chars>`;
- digest input uses only public audit metadata fields;
- digest is not a persisted signature, not a hash chain, not retention enforcement, and not a replacement for signed audit records.

- [x] **Step 2: Update delivery report**

Append Phase J19A with scope, RED evidence, focused/full verification, token scan, and evidence boundaries.

- [x] **Step 3: Run final verification**

Run:

```bash
/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test
npm test
npm run typecheck
git diff --check
rg -n "[ \t]$|^(<<<<<<<|=======|>>>>>>>)" backend docs/architecture/java-team-backend-platform-spec.md docs/superpowers/plans/2026-06-14-java-audit-record-digest-baseline.md docs/reports/runtime-work-item-execution-resume-delivery.md --glob '!backend/target/**'
rg -n "tp-[A-Za-z0-9]{20,}|Bearer tp-[A-Za-z0-9]{20,}|MIMO_API_KEY=tp-[A-Za-z0-9]{20,}" backend docs src tests --glob '!backend/target/**'
```

Expected: Java tests, TS tests, and typecheck pass; scans return no matches.
