# Java Artifact Registry Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use test-driven-development. This is Phase J4A only; do not implement approval decisions, publishing, object storage, retention, or remote runner upload.

**Goal:** Implement the first artifact registry slice for the Java backend: register worker artifact refs after async run completion, list artifacts for an authorized run, and read safe text artifacts without exposing server absolute paths.

**Architecture:** Worker responses already include relative artifact refs in `agent-backend-response.v1`. Java owns a separate artifact registry table and records those refs as metadata scoped by `runId` and `workspaceId`. Public APIs return opaque `artifactId` plus relative `artifactRef`; artifact reads resolve through `WorkspaceService.resolveContentPath()` so absolute paths and traversal remain blocked.

---

## Scope Check

In scope:

- Add `V4__artifact_registry_baseline.sql`.
- Add `ArtifactRefRecord` and `ArtifactRepository` with in-memory/JDBC implementations.
- Register artifacts from `AgentWorkerResponse.artifactRefs()` after run completion.
- Add:
  - `GET /v1/agent-runs/{runId}/artifacts`
  - `GET /v1/artifacts/{artifactId}`
- Return metadata without internal absolute paths.
- Read text artifacts only.
- Classify raw provider refs as `RAW_PROVIDER` and mark `REDACTED`.
- Keep raw provider content assumptions conservative: tests must use redacted fixture content only.

Out of scope:

- Object storage.
- Artifact retention.
- Signed URLs.
- Binary streaming.
- Raw provider unredacted payload access.
- Approval records.
- Candidate patch publishing.

## Tasks

- [x] Write RED tests for artifact list/read API.
- [x] Write RED JDBC artifact repository test with Flyway V2+V3+V4.
- [x] Implement artifact domain/repository/service/controller.
- [x] Integrate artifact registration in `AgentRunService`.
- [x] Run focused Java tests.
- [x] Run full Java, TS, typecheck, and scans.
- [x] Archive delivery evidence.

## Completion Boundary

J4A is complete only if:

- artifact refs are registered after worker completion;
- list/read APIs require existing run/workspace access through existing guards;
- public responses do not expose server absolute paths;
- read endpoint resolves paths through workspace path guard;
- raw provider refs are classified as redacted metadata;
- MySQL/Testcontainers applies V2+V3+V4;
- Java/TS full suites, typecheck, diff check, whitespace/merge-marker scan, and token scan pass.

## Execution Status

Status: completed for J4A on 2026-06-14.

RED evidence:

- `mvn -f backend/pom.xml -Dtest=ArtifactControllerTest,JdbcArtifactRepositoryTest test`
  - Failed first at test compile because `ArtifactRepository`, `JdbcArtifactRepository`, and `ArtifactRefRecord` did not exist.

GREEN evidence:

- `mvn -f backend/pom.xml -Dtest=ArtifactControllerTest,JdbcArtifactRepositoryTest test`
  - PASS; 2 tests.
  - Covers artifact list/read API, no absolute path in public response, raw provider metadata marked `REDACTED`, and Testcontainers MySQL V2+V3+V4 migration.
- `mvn -f backend/pom.xml test`
  - PASS; 29 Java tests.
- `npm test`
  - PASS; 44 Vitest files / 178 tests.
- `npm run typecheck`
  - PASS.
- `git diff --check`
  - PASS after archive update.
- J4 whitespace / merge-marker scan
  - no matches after archive update.
- token redaction scan for `tp-*` / `Bearer tp-*` / `MIMO_API_KEY=tp-*`
  - no matches after archive update.
