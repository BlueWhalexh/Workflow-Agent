# Backend Platform Roadmap Change Note

Date: 2026-06-14

## Context

Agent SDK Phase 1 and Phase 37 backend adapter smoke proved that callers can consume `agent-sdk-run.v1` and `agent-backend-response.v1` without understanding fixed workflow, deterministic open-agent, or OpenAgentGraph runtime result shapes.

The next architectural question is whether to build a real backend. A full platform would introduce HTTP API, workspace access policy, approval, job state, provider credential handling, artifact reads, and eventually DB/auth boundaries.

## Decision

Define a broad backend platform roadmap before implementation:

- Start with a real HTTP backend MVP around `runBackendAgent()`。
- Keep Phase 39 artifact-backed and local/single-user first。
- Add workspace allowlist before accepting any workspace path over HTTP。
- Keep DB/auth/queue/frontend out of the first backend phase。
- Treat `.agent-runs` as canonical runtime evidence until a later migration spec says otherwise。
- Real provider remains explicit opt-in and cannot receive tokens through HTTP JSON requests。

## Consequences

- Backend work can move in larger coordinated phases without losing safety boundaries。
- The first backend implementation can be useful while still avoiding platform scope creep。
- Future DB/auth/queue work has clear prerequisites。
- API and workspace security boundaries are now part of current architecture discussion, not ad hoc implementation details。

## Affected Docs

- `docs/architecture/backend-platform-roadmap-spec.md`
- `docs/changes/2026-06-14-backend-platform-roadmap.md`

## Follow-up Phase

Phase 39 should implement the HTTP Backend MVP:

- `GET /health`；
- `POST /agent/run`；
- workspace allowlist；
- stable error envelope；
- fake-provider integration tests；
- no new production dependency unless explicitly approved。
