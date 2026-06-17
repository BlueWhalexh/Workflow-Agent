# Java Backend Phase One Completion Audit

> Current date: 2026-06-17. This audit is a goal-tracking artifact for `按照sop完成后端一期的开发`; it is not a completion claim.

## Current Goal

Complete backend phase one under the project SOP: central Java backend control plane for multiple users and workspaces, with stable API contracts, permission boundaries, async run/job control, artifact/approval/audit evidence, provider credential safety, and documented verification.

## Authoritative Sources

- `docs/architecture/java-team-backend-platform-spec.md`
- `docs/architecture/backend-platform-roadmap-spec.md`
- `docs/reports/runtime-work-item-execution-resume-delivery.md`
- Java backend source and tests under `backend/src/main/java` and `backend/src/test/java`
- Archived phase plans under `docs/superpowers/plans/2026-06-14-java-*.md`, `docs/superpowers/plans/2026-06-15-java-*.md`, and `docs/superpowers/plans/2026-06-17-java-*.md`

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
| Audit visibility | J11A owner list, J16A pagination/filtering, J18A NDJSON export, J19A digest, J28A persisted integrity chain | Implemented baseline |
| Audit retention policy | J27A owner-visible report-only retention metadata, no destructive purge | Implemented baseline |
| Run event streaming | J17A SSE, J22A reconnect cursor and JDBC event sequence | Implemented baseline |
| Remote runner hardening | J20A HMAC result envelope, J21A production secret guard | Implemented baseline |
| Remote runner registry / lease | J30A workspace-scoped runner metadata API, heartbeat, exclusive lease, workspace audit | Implemented baseline |
| Identity hardening | J31A disables dev header and dev principal fallback for `prod` / `production` profiles | Implemented baseline |
| Bearer identity adapter | J32A `BearerTokenVerifier` SPI maps verified bearer token to `BackendPrincipal`; no real OIDC/JWKS | Implemented baseline |
| Provider credential internals | J24A schema, J24B repository, J24C scope guard, J24D descriptor, J24E run ref wiring | Implemented baseline |
| Public provider credential API | J25A workspace-scoped owner upsert/list API, env-backed metadata, redacted public response | Implemented baseline |
| Provider credential lifecycle | J26A owner-only disable API, disabled refs not resolved for runs, redacted audit | Implemented baseline |
| Provider secret injection | J29A resolver SPI + local worker per-run env injection for `secret://`; J33A local file resolver for configured-root `file://`; no raw secret in worker request | Implemented baseline |
| External secret manager / KMS | J33A local file adapter baseline exists; no production KMS, Keychain adapter, rotation, public secret registration, or remote runner distribution | Partially implemented baseline |
| Full OIDC/OAuth | J31A fail-closed guard and J32A bearer verifier SPI exist; no real OIDC discovery, JWKS validation, token introspection, SSO, sessions, or directory claim sync | Partially implemented baseline |
| Full user/team directory CRUD | Current team and member listing only; no full directory lifecycle | Not implemented |
| Persisted signed audit records | J28A persists SHA-256 record digest, previous chain digest, chain digest, and local `sha256-chain-v1` signature metadata | Implemented baseline |
| WebSocket / multi-node fanout | Bounded SSE replay only; no broker/multi-node/WebSocket fanout | Not implemented |
| Production remote runner platform | Contract/signature guards and J30A workspace-scoped registry/heartbeat/lease metadata exist; no real runner identity, job dispatch, artifact upload, remote cancellation, multi-node scheduler, or secret distribution | Partially implemented baseline |

## Latest Gate Resolution

J33A: `Java File Secret Resolver Baseline` is now implemented as a local file-backed provider secret adapter slice.

Plan:

- `docs/superpowers/plans/2026-06-17-java-file-secret-resolver-baseline.md`

Scope that required approval:

- Adds a conditional local `FileProviderSecretResolver`.
- Adds `my-workflow.backend.provider-secrets.file-root`.
- Extends the run path so non-`env://` refs can be resolved through existing `ProviderSecretResolver` beans.
- Requires secret-boundary tests, docs, and report updates.
- Touched more than five files.

SOP result:

- User asked to continue backend development after J32A.
- `file://...` provider credential refs can be resolved only when a backend file-root is configured.
- File refs are constrained to root-relative files under `my-workflow.backend.provider-secrets.file-root`.
- Path traversal, absolute paths, directories, missing files, oversized files, and blank file content fail closed before worker invocation.
- Resolved file secret values enter only `AgentWorkerSecretInjection` for workers that explicitly support secret injection.
- J33A is not production KMS, Keychain, Vault, public secret upload, secret rotation, encrypted storage, remote runner secret distribution, or real provider execution.
- Verification evidence is archived in `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Recommended Phase Order

1. J25A: public workspace-scoped provider credential metadata API. Completed baseline.
   - Owner can upsert/list env-backed metadata.
   - Public response never returns env names, `apiKeySecretRef`, token, Authorization, raw provider payload, internal path, or server storage ref.
   - Existing J24E `providerRuntimeRef = "credential.<ref>"` remains the run consumption path.

2. J26A: credential lifecycle guard. Completed baseline.
   - Disable credential metadata without deleting audit history.
   - Preserve run behavior: disabled refs are not resolved.
   - Still no raw secret or secret manager lookup.

3. J27A: audit retention policy baseline. Completed baseline.
   - Configurable retention metadata and owner-visible policy.
   - No destructive purge until a separate explicit phase defines retention execution and audit export safeguards.

4. J28A: persisted signed audit record baseline. Completed baseline.
   - Persist signature/digest metadata at repository append time.
   - Keep existing J19A digest semantics stable while adding hash-chain metadata.

5. J29A: provider secret injection baseline. Completed baseline.
   - Resolve `secret://...` via backend SPI when configured.
   - Inject raw secret only through per-run worker environment for supporting local workers.
   - Still no external KMS, public secret registration, rotation, or remote runner distribution.

6. J33A: local file secret resolver baseline. Completed baseline.
   - Configured-root `file://...` refs can be resolved through existing secret injection.
   - Still no KMS/Keychain/Vault, rotation, public secret registration, or remote runner distribution.

7. J30A: remote runner registration/lease design and baseline. Completed baseline.
   - Workspace-scoped registry, heartbeat, lease lifecycle, and workspace-owner guard.
   - No runner-scoped secret distribution until a dedicated secret phase.

8. J31A: identity hardening baseline. Completed baseline.
   - `prod` / `production` profiles fail closed instead of trusting dev identity.
   - Still no real OIDC/OAuth provider, token validation, session lifecycle, or user/team directory CRUD.

9. J32A: bearer identity adapter baseline. Completed baseline.
   - Injected verifier maps bearer token to backend principal.
   - Still no real OIDC discovery, JWKS validation, token introspection, session lifecycle, or directory sync.

10. J34A+: real OIDC/OAuth integration and directory lifecycle.
   - OIDC/OAuth adapter and claim mapping.
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

No J33A approval is pending. The next implementation slice should be selected explicitly because remaining backend phase-one gaps still include public/security-sensitive areas such as production KMS/Keychain/Vault integration, real OIDC/JWKS integration, real production runner identity/dispatch/artifact upload, directory lifecycle, and multi-node stream fanout.
