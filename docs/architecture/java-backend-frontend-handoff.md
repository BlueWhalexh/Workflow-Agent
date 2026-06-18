# Java Backend Frontend Handoff

> 状态：Phase A handoff contract。本文说明前端 API client 如何接入 MySQL-backed Java 后端一期。

## Base Contract

- API base path: `/v1`
- Envelope: `java-backend-api.v1`
- Capability endpoint: `GET /v1/ops/integration-contract`
- Auth diagnostics: `GET /v1/ops/auth-config`

前端必须先读取 `GET /v1/ops/integration-contract`，用返回的 `frontendRequiredEndpoints` 和 `capabilities` 判断 UI 是否可以开启对应功能。

## Credentialed Browser Requests

Session-cookie 模式下：

1. 前端用 `credentials: "include"` 请求后端。
2. mutating request 前先请求 `GET /v1/session/csrf`。
3. 后端返回 `data.token` 和 `data.headerName = "X-CSRF-Token"`，并写入 `MWA_CSRF` cookie。
4. 前端对 `POST`、`PUT`、`PATCH`、`DELETE` 请求加 `X-CSRF-Token: <token>`。

Bearer-token API client 不需要 CSRF header。

## Envelope Unwrap

前端只消费：

- `schemaVersion`
- `ok`
- `data`
- `error.code`
- `error.message`
- `error.retryable`

`ok=false` 时不要读 `data`。

## Run Flow

1. `POST /v1/workspaces/{workspaceId}/agent-runs`
2. `GET /v1/agent-runs/{runId}`
3. `GET /v1/agent-runs/{runId}/events`
4. Optional: `GET /v1/agent-runs/{runId}/events/stream`
5. `GET /v1/agent-runs/{runId}/artifacts`
6. `GET /v1/agent-runs/{runId}/approvals`

SSE EOF is not terminal evidence. The UI must use the run status or durable event list to decide terminal state.

## Artifact And Approval Rules

- `targetWorkspacePaths` means proposed targets, not confirmed writes.
- `wroteWorkspace=true` is required before the UI claims a workspace write happened.
- Artifact API responses expose artifact IDs and safe relative refs, not absolute paths.
- Approval decisions are explicit user actions through `POST /v1/agent-runs/{runId}/approvals`.

## Fields The Frontend Must Not Display

- raw provider token
- `apiKeySecretRef`
- environment variable names used for secrets
- `Authorization`
- cookie values
- absolute workspace paths
- raw provider payload
- Java exception stack traces

## Phase A Boundaries

This handoff supports local/team DB-only integration. It does not prove production OAuth login/callback, production secret manager, production remote runner identity, multi-node stream fanout, or deployed browser E2E.
