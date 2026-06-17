# Frontend Control Plane Design

> Status: draft prototype for review. This is not the production frontend implementation.

## Goal

Build a team-facing Agent Control Plane console for the Java backend baseline. The first frontend should help operators and workspace owners inspect workspaces, runs, approvals, artifacts, audit evidence, and provider credentials without exposing runtime-private result shapes or provider secrets.

## Product Shape

The first screen is an operational workspace console, not a marketing page.

Primary users:

- Workspace owner: manages members, provider credentials, approvals, audit export.
- Workspace editor: starts and monitors agent runs.
- Reviewer/operator: inspects evidence, artifacts, run events, and audit integrity.

Primary jobs:

- Pick a workspace and understand its current execution health.
- Start or inspect an agent run.
- Resolve pending approvals before workspace writes.
- Open artifact refs and audit evidence without seeing server filesystem paths.
- Confirm provider credential metadata and lifecycle status without seeing env names or secret refs.

## Navigation

Persistent left navigation:

- Workspaces
- Runs
- Approvals
- Artifacts
- Audit
- Credentials
- Team

Top bar:

- Current team/workspace selector.
- Environment marker: Local / Staging / Production.
- Principal display.
- Connection status.

## First Prototype Screen

The static prototype at `docs/prototypes/backend-console.html` represents the default workspace overview:

- Workspace summary band with status, branch, owner, and retention policy.
- Run queue table with status, mode, approval state, worker kind, and last event.
- Approval lane for candidate patch and route preview decisions.
- Artifact lane with public refs only.
- Audit lane with digest/chain metadata and export affordance.
- Provider credential lane with public metadata only.

The prototype intentionally uses static sample data. It does not call the backend, store secrets, or execute real provider calls.

## API Dependencies

Existing backend endpoints that map directly to the first UI:

- `GET /me`
- `GET /teams`
- `GET /teams/{teamId}/members`
- `GET /v1/workspaces`
- `POST /v1/workspaces`
- `GET /v1/workspaces/{workspaceId}`
- `GET /v1/workspaces/{workspaceId}/members`
- `POST /v1/workspaces/{workspaceId}/agent-runs`
- `GET /v1/agent-runs/{runId}`
- `POST /v1/agent-runs/{runId}/cancel`
- `GET /v1/agent-runs/{runId}/events`
- `GET /v1/agent-runs/{runId}/events/stream`
- `GET /v1/agent-runs/{runId}/artifacts`
- `GET /v1/artifacts/{artifactId}`
- `GET /v1/agent-runs/{runId}/approvals`
- `POST /v1/agent-runs/{runId}/approvals/{approvalId}/decision`
- `GET /v1/workspaces/{workspaceId}/audit-events`
- `GET /v1/workspaces/{workspaceId}/audit-events/export`
- `GET /v1/workspaces/{workspaceId}/audit-events/retention-policy`
- `GET /v1/workspaces/{workspaceId}/provider-credentials`
- `PUT /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}`
- `POST /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable`

## Data Safety

The frontend must not display or persist:

- Raw provider token values.
- `apiKeySecretRef`.
- Env var names from public credential responses.
- Server filesystem paths.
- Runtime-private `source` fields from SDK or worker internals.
- Authorization headers or cookies in logs.

The frontend may display:

- Public credential ref, provider, model, base URL, status.
- Artifact refs returned by backend.
- Audit `recordDigest`, `previousRecordDigest`, `chainDigest`, `signatureKind`, `signatureValue`.
- Run status, output kind, approval flags, worker kind, event history.

## Interaction Model

Run lifecycle:

1. User creates a run from a selected workspace and provider credential ref.
2. UI shows queued/running terminal status from polling or SSE.
3. If approval is required, UI routes the user to the approval lane.
4. After decision, UI keeps artifact and audit evidence visible.

Approval lifecycle:

1. Candidate patch or route preview appears as an approval card.
2. UI shows target workspace paths as proposed targets, not confirmed writes.
3. Approve/reject actions post a decision.
4. UI refreshes run events and audit evidence.

Credential lifecycle:

1. Owner adds credential metadata using provider/model/base URL and a secret reference mechanism controlled by backend.
2. Public list shows status and metadata only.
3. Disable action marks credential unavailable for future runs.

## Future Frontend Build Order

1. Static console prototype and design review.
2. Minimal API client with typed envelope handling.
3. Workspace and run list pages.
4. Run detail page with events, artifacts, approvals.
5. Credential and audit pages.
6. SSE stream integration.
7. OAuth-backed identity integration after backend J32.

## Review Focus

- Information architecture matches the Java backend API surface.
- Secret and runtime-private boundaries are preserved.
- First screen supports repeated operational work, not marketing.
- Prototype stays static until API client work is explicitly started.
