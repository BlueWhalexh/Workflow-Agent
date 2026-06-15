# Java Backend Phase One Completion Audit

> Current date: 2026-06-14. This audit is a goal-tracking artifact for `按照sop完成后端一期的开发`; it is not a completion claim.

## Current Goal

Complete backend phase one under the project SOP: central Java backend control plane for multiple users and workspaces, with stable API contracts, permission boundaries, async run/job control, artifact/approval/audit evidence, provider credential safety, and documented verification.

## Authoritative Sources

- `docs/architecture/java-team-backend-platform-spec.md`
- `docs/architecture/backend-platform-roadmap-spec.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Java backend source and tests under `backend/src/main/java` and `backend/src/test/java`
- Archived phase plans under `docs/superpowers/plans/2026-06-14-java-*.md`

## Evidence Summary

| Area | Current evidence | Status |
| --- | --- | --- |
| Java platform skeleton | J1 report and backend Maven/Spring Boot skeleton | Implemented baseline |
| Workspace / identity baseline | J2 workspace API, path guard, JDBC repository, Flyway/Testcontainers | Implemented baseline |
| Async run/job bridge | J3A async `agent-runs`, job/attempt schema, local TS worker bridge | Implemented baseline |
| Artifact registry | J4A artifact ref registry/list/safe read | Implemented baseline |
| Approval boundary | J5A approval request/decision metadata | Implemented baseline |
| Job reliability | J6A cancel-safe terminal guard, J6B retry, J6C stale lock recovery, J6D run events | Implemented baseline |
| Local team/RBAC/audit | J7A dev principal, workspace role guard, append-only audit | Implemented baseline |
| Remote HTTP worker spike | J8A remote worker contract spike | Implemented baseline |
| Provider runtime ref policy | J9A/J23A/J23B config-backed ref, raw secret guard, env-name guard | Implemented baseline |
| Workspace member management | J10A member grant/list, J12A removal, J15A owner transfer | Implemented baseline |
| Team discovery/listing | J13A current team, J14A backend-known team members | Implemented baseline |
| Audit visibility | J11A owner list, J16A pagination/filtering, J18A NDJSON export, J19A digest | Implemented baseline |
| Run event streaming | J17A SSE, J22A reconnect cursor and JDBC event sequence | Implemented baseline |
| Remote runner hardening | J20A HMAC result envelope, J21A production secret guard | Implemented baseline |
| Provider credential internals | J24A schema, J24B repository, J24C scope guard, J24D descriptor, J24E run ref wiring | Implemented baseline |
| Public provider credential API | J25A workspace-scoped owner upsert/list API, env-backed metadata, redacted public response | Implemented baseline |
| Secret manager / non-env injection | J24D validates refs; J24E executes only `env://`; no lookup/injection path | Not implemented |
| Full OIDC/OAuth | Dev header/local principal only | Not implemented |
| Full user/team directory CRUD | Current team and member listing only; no full directory lifecycle | Not implemented |
| Audit retention / persisted signed records | Digest exists; no persisted signatures/hash chain/retention enforcement | Not implemented |
| WebSocket / multi-node fanout | Bounded SSE replay only; no broker/multi-node/WebSocket fanout | Not implemented |
| Production remote runner platform | Contract/signature guards exist; no runner registry, heartbeat, leases, authz, artifact upload, remote cancellation, or secret distribution | Not implemented |

## Latest Gate Resolution

J25A: `Java Provider Credential Public Metadata API` is now implemented as an approved scope expansion.

Plan:

- `docs/superpowers/plans/2026-06-14-java-provider-credential-public-metadata-api.md`

Scope that required approval:

- Adds public API.
- Changes provider credential security boundary.
- Requires controller/service/repository/tests/docs/report updates.
- Touched more than five files.

SOP result:

- User approved the scope expansion.
- The endpoint is JDBC-profile-only in J25A, matching the provider credential repository/service profile and avoiding default in-memory context breakage.
- Verification evidence is archived in `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Recommended Phase Order

1. J25A: public workspace-scoped provider credential metadata API. Completed baseline.
   - Owner can upsert/list env-backed metadata.
   - Public response never returns env names, `apiKeySecretRef`, token, Authorization, raw provider payload, internal path, or server storage ref.
   - Existing J24E `providerRuntimeRef = "credential.<ref>"` remains the run consumption path.

2. J26A: credential lifecycle guard.
   - Disable credential metadata without deleting audit history.
   - Preserve run behavior: disabled refs are not resolved.
   - Still no raw secret or secret manager lookup.

3. J27A: audit retention policy baseline.
   - Configurable retention metadata and owner-visible policy.
   - No destructive purge until a separate explicit phase defines retention execution and audit export safeguards.

4. J28A: persisted signed audit record baseline.
   - Persist signature/digest metadata separately from public response.
   - Keep existing J19A digest stable.

5. J29A: remote runner registration/lease design and baseline.
   - Registry, heartbeat, lease lifecycle, runner authz boundaries.
   - No runner-scoped secret distribution until a dedicated secret phase.

6. J30A+: identity hardening and directory lifecycle.
   - OIDC/OAuth integration plan.
   - User/team CRUD and role sync.

## Completion Criteria For Backend Phase One

Phase one should not be marked complete until all of the following are true and freshly verified:

- Public API surface required by the Java platform spec is implemented or explicitly descoped in the spec.
- Provider credential management has a public metadata API or a documented user-approved deferral.
- Secret handling does not accept raw provider tokens in JSON, logs, artifacts, DB plaintext, worker requests, or public responses.
- Run, approval, artifact, audit, and provider credential flows have focused tests and full Java suite evidence.
- TypeScript worker/SDK bridge compatibility has full `npm test` and `npm run typecheck` evidence.
- Delivery report includes RED/GREEN/full/static evidence for the latest completed phase.
- Architecture spec current-status line and relevant boundaries match the code.
- Token redaction scan has no real-token-shaped matches.
- Unchecked phase plan scan is clean only for completed phase plans; active gated plans may intentionally keep unchecked boxes.

## Current Decision Needed

No J25A approval is pending. The next implementation slice should be selected explicitly because remaining backend phase-one gaps still include public/security-sensitive areas such as credential lifecycle, secret manager/non-env injection, audit retention, signed audit records, production runner platform, and identity hardening.
