# Backend Platform Roadmap Spec

> 状态：planning spec。本文定义从当前 Agent SDK / backend adapter 推进到真实后端的完整路线。它不是单个 phase 的小任务计划；后续每个 implementation phase 必须再按 `runtime-phase-sop.md` 拆成可验证切片执行。

## 1. Current Truth

当前已具备：

- `runAgent(request)` / `createKnowledgeWorkflowAgent().runAgent(request)` 返回 `agent-sdk-run.v1`。
- `runBackendAgent(request)` 返回 `agent-backend-response.v1`，后端调用方不需要理解 fixed workflow、deterministic open-agent、OpenAgentGraph runtime result shape。
- `.agent-runs` 是当前 artifact-backed persistence：
  - fixed workflow run report / eval / trace；
  - open-agent report；
  - raw provider request/response refs；
  - redacted provider artifacts。
- OpenAgentGraph 已支持 provider-backed plan / action / synthesis，并通过 MiMo `ANSWER_ONLY` real smoke。
- Candidate patch 仍是 artifact-only proposal，不直接写 `knowledge-base`。
- 当前 repo 明确暂不做完整前后端平台，但现在需要从 SDK smoke 进入真实 backend boundary 规划。

当前缺口：

- 没有 HTTP server / backend process boundary。
- 没有 request validation、workspace allowlist、artifact read API。
- 没有 durable job state，`runAgent()` 仍是同步调用。
- 没有 approval API；candidate patch / route preview 只能在 SDK response 中表达。
- 没有 DB / auth / user / organization / permission model。
- 没有 provider credential management 的 backend 责任边界。
- 没有 backend-level audit log、idempotency、rate limit、timeout/cancel contract。

## 2. Goal

构建一个真实后端，但保持 agent-loop-first：

```text
HTTP / backend API
  -> request validation / workspace boundary
  -> backend adapter
    -> Agent SDK normalized envelope
      -> fixed workflow / deterministic open-agent / OpenAgentGraph
        -> .agent-runs artifacts
```

完成后，调用方应只理解 backend contract：

- request id / run id；
- status；
- output kind；
- display text；
- approval / confirmation requirements；
- artifact refs；
- workspace write evidence；
- provider evidence summary。

后端不应理解 runtime internals。

## 3. Non-goals

第一阶段不做：

- 前端 UI。
- 多用户组织系统。
- 复杂 RBAC。
- 外部 DB migration。
- 云部署配置。
- 长连接 streaming。
- 分布式队列。
- 直接发布 candidate patch。
- 任意 workspace 文件浏览器。

这些能力进入后续阶段，必须先有更明确的安全边界。

## 4. Target Architecture

### 4.1 Layering

```text
Client / future UI
  -> Backend HTTP API
    -> Request validation
    -> Workspace boundary policy
    -> Job/run service
    -> Backend adapter
      -> Public Agent SDK
        -> Runtime / provider / artifacts
```

### 4.2 Backend Modules

建议模块：

- `src/backend/server.ts`
  - Node HTTP server bootstrap；
  - routing；
  - JSON response formatting；
  - graceful error mapping。
- `src/backend/http-contract.ts`
  - request/response schemas；
  - endpoint-level types；
  - error envelopes。
- `src/backend/workspace-policy.ts`
  - workspace allowlist；
  - path normalization；
  - no traversal；
  - fixture/test workspace policy。
- `src/backend/run-service.ts`
  - calls `runBackendAgent()`；
  - maps backend request to adapter request；
  - stores lightweight run index if needed。
- `src/backend/artifact-service.ts`
  - reads allowed artifact refs under `.agent-runs` only；
  - never returns raw provider payload unless endpoint explicitly opts in and remains redacted。
- `src/backend/provider-credentials.ts`
  - resolves provider config from env / Keychain / injected test deps；
  - never accepts raw token in JSON API。

## 5. API Contract

### 5.1 Common Envelope

All backend responses:

```ts
interface BackendApiEnvelope<T> {
  schemaVersion: "backend-api.v1";
  ok: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    retryable: boolean;
  };
}
```

Rules:

- No stack traces in API response.
- No provider tokens in response.
- Error code must be stable enough for future UI.

### 5.2 Health

```http
GET /health
```

Response:

```json
{
  "schemaVersion": "backend-api.v1",
  "ok": true,
  "data": {
    "status": "ok",
    "service": "my-workflow-agent-backend"
  }
}
```

### 5.3 Run Agent

```http
POST /agent/run
```

Request:

```ts
interface RunAgentHttpRequest {
  workspaceRoot?: string;
  userMessage: string;
  runId?: string;
  mode?: "auto" | "deterministic-open-agent" | "llm-open-agent" | "fixed-workflow";
  execute?: boolean;
  autoApprove?: boolean;
  provider?: {
    runtime?: "fake" | "mimo-real" | "deepseek-real";
    allowRealProvider?: boolean;
  };
}
```

Response data:

```ts
interface RunAgentHttpResponse {
  schemaVersion: "agent-backend-response.v1";
  runId: string;
  status: AgentSdkRunStatus;
  outputKind: AgentSdkOutputKind;
  displayText: string | null;
  requiresConfirmation: boolean;
  requiresApproval: boolean;
  artifactRefs: string[];
  wroteWorkspace: boolean;
  targetWorkspacePaths: string[];
  source: AgentSdkRunResult;
}
```

Rules:

- If `workspaceRoot` is omitted, use configured default workspace.
- If `workspaceRoot` is provided, it must pass workspace allowlist.
- `allowRealProvider` must be `true` for real provider runtime.
- API never accepts API keys.
- Candidate patch returns `requiresApproval=true` and `wroteWorkspace=false`.
- Fixed workflow preview returns `requiresApproval=true` and `wroteWorkspace=false`.
- Fixed workflow execution may return `wroteWorkspace=true` only when SDK evidence says so.

### 5.4 Inspect Run

```http
GET /agent/runs/:runId
```

First implementation can read existing SDK inspector for fixed workflow runs and minimal open-agent report metadata.

Response should include:

- run id；
- report path；
- artifact refs；
- status；
- workspace write evidence；
- provider evidence summary。

This endpoint must not re-execute runtime.

### 5.5 Read Artifact

```http
GET /agent/artifacts?ref=<artifactRef>
```

Rules:

- `ref` must be relative.
- `ref` must stay under `.agent-runs`.
- `ref` must not contain `..`.
- raw provider request/response artifacts are allowed only if already redacted.
- Future UI should prefer report/trace summaries over raw payload reads.

### 5.6 Approval

Approval is not first backend phase unless needed for an end-to-end UI. Target contract:

```http
POST /agent/runs/:runId/approval
```

Request:

```ts
interface ApprovalRequest {
  decision: "approve" | "reject";
  artifactRef?: string;
  targetWorkspacePaths?: string[];
  comment?: string;
}
```

Rules:

- Approving a candidate patch must not directly publish through open-agent.
- Approval should hand off to fixed workflow or a future deterministic patch application path.
- Approval event must be written as artifact before any workspace write.

## 6. State And Persistence

### Phase A: Artifact-backed only

- No DB.
- `.agent-runs` remains source of truth.
- Optional lightweight run index can be generated from artifacts, not manually mutated as truth.

### Phase B: Embedded DB index

Add SQLite only when needed for:

- run list performance；
- run search；
- job status；
- audit queries；
- multi-workspace history。

DB must index artifact facts; artifact remains canonical for runtime evidence until a migration spec says otherwise.

### Phase C: External DB / multi-user

Only after:

- auth is designed；
- workspace ownership model exists；
- artifact retention and privacy policy exist；
- migration and rollback are defined。

## 7. Execution Model

### Phase A: Synchronous HTTP

`POST /agent/run` blocks until `runBackendAgent()` returns.

Use for:

- deterministic answer；
- draft；
- candidate patch；
- confirmation；
- fixed workflow preview；
- small fake-provider fixed workflow smoke。

Limit:

- request timeout risk；
- no cancellation；
- not ideal for real provider or long fixed workflow。

### Phase B: In-process job runner

Add:

- `POST /agent/jobs`；
- `GET /agent/jobs/:jobId`；
- in-memory job state；
- artifact-backed result。

Use for:

- real provider；
- fixed workflow execution；
- longer runs。

### Phase C: Durable queue

Only after real usage shows need. Candidate options:

- SQLite job table；
- external worker；
- queue backend。

Do not introduce a queue before the API and artifact contract stabilize.

## 8. Security Boundary

### Workspace allowlist

Backend must not accept arbitrary filesystem paths. Recommended config:

```ts
interface BackendConfig {
  defaultWorkspaceRoot: string;
  allowedWorkspaceRoots: string[];
  allowRealProvider: boolean;
}
```

Rules:

- normalize with `path.resolve`;
- compare against allowlist roots;
- reject path traversal;
- never expose absolute paths in public response unless explicitly local-dev mode.

### Provider credentials

Allowed:

- process env；
- Keychain helper；
- ignored `.env.local` loaded by local dev wrapper；
- injected test dependencies。

Forbidden:

- API key in JSON request；
- API key in URL；
- API key in repo docs/report/fixtures；
- API key in artifacts。

### Artifact read

Artifact endpoint must:

- only read under `.agent-runs`;
- block `..`;
- block absolute paths;
- preserve redaction;
- avoid exposing raw provider payload as default UI data。

## 9. Provider Policy

Default provider:

- `fake` for local HTTP tests。

Real provider:

- disabled by default；
- requires backend config `allowRealProvider=true` and request `provider.allowRealProvider=true`；
- must record real external call evidence；
- must run token redaction checks in tests / smoke when used。

Real smoke:

- uses temp fixture workspace；
- does not write user workspace；
- command and report must not include token。

## 10. Testing Strategy

### Unit tests

- HTTP contract validation。
- workspace allowlist path normalization。
- artifact ref validation。
- provider credential gate。

### Integration tests

- `GET /health` returns stable envelope。
- `POST /agent/run` answer path returns `agent-backend-response.v1`。
- candidate patch returns `requiresApproval=true`, `wroteWorkspace=false`, target file absent。
- confirmation returns `requiresConfirmation=true`。
- fixed workflow preview returns no workspace write。
- artifact read blocks path traversal。

### Smoke tests

- local fake provider HTTP smoke。
- injected provider smoke for raw refs/redaction。
- optional real MiMo smoke only when explicitly requested。

### Full verification

Every backend phase:

```bash
npm test
npm run typecheck
git diff --check
rg -n "t[p]-[A-Za-z0-9]|Bearer t[p]-|MIMO_API_KEY=t[p]-" src tests docs
```

## 11. Roadmap

### Phase 39: HTTP Backend MVP

Goal:

- real HTTP process around `runBackendAgent()`。

Deliver:

- `GET /health`；
- `POST /agent/run`；
- workspace allowlist；
- stable error envelope；
- no new production dependency；
- fake-provider integration tests。

Acceptance:

- backend caller can run answer/candidate/confirmation/preview through HTTP；
- no runtime internal imports；
- no arbitrary workspace root；
- full verification passes。

### Phase 40: Artifact Read And Run Inspect

Goal:

- make backend useful for future UI and debugging without re-running workflows。

Deliver:

- `GET /agent/runs/:runId`；
- `GET /agent/artifacts?ref=<artifactRef>`；
- artifact ref validator；
- redacted raw provider artifact handling；
- path traversal tests。

Acceptance:

- run inspect returns artifact-backed state；
- artifact reads stay under `.agent-runs`；
- raw provider request includes `[REDACTED]` and no token pattern。

### Phase 41: Approval Boundary

Goal:

- formalize route preview / candidate patch / confirmation approval events。

Deliver:

- approval artifact schema；
- `POST /agent/runs/:runId/approval`；
- approval state read endpoint；
- no direct open-agent publish。

Acceptance:

- approval event is persisted before side effects；
- candidate patch still does not write workspace；
- rejected approval never writes workspace。

### Phase 42: Async Jobs

Goal:

- avoid long-running HTTP request coupling。

Deliver:

- in-process job runner；
- job id；
- job status；
- result artifact link；
- timeout/cancel baseline。

Acceptance:

- fixed workflow execution can run as job；
- job status survives within process lifetime；
- result remains artifact-backed。

### Phase 43: Provider Credentials And Real Smoke Gate

Goal:

- make real provider use explicit and auditable through backend。

Deliver:

- backend provider config loader；
- real provider opt-in gate；
- local Keychain/env support；
- backend-level real smoke command。

Acceptance:

- real provider disabled by default；
- no API token accepted via HTTP；
- redaction/no-write evidence recorded。

### Phase 44: Optional DB Index

Goal:

- add queryable run/job index only after artifact API stabilizes。

Deliver:

- SQLite index；
- migration-free local dev bootstrap or explicit migration files；
- index rebuild from `.agent-runs`；
- DB is index, not hidden runtime truth。

Acceptance:

- deleting DB and rebuilding from artifacts recovers run list；
- artifact remains audit source。

### Phase 45: Multi-user Boundary

Goal:

- prepare for real product backend only after local backend proves useful。

Deliver:

- auth model；
- workspace ownership；
- permission checks；
- provider credential ownership；
- audit retention。

Acceptance:

- no endpoint can access workspace outside ownership；
- no provider credential crosses user/org boundary；
- security tests exist before UI work。

## 12. Stop Conditions

Pause before implementation if:

- choosing a web framework or adding production dependencies；
- accepting arbitrary workspace paths；
- introducing DB schema；
- adding auth/session；
- adding provider token upload over HTTP；
- enabling real provider by default；
- making approval trigger workspace writes；
- changing `agent-sdk-run.v1` or `agent-backend-response.v1`。

## 13. Review Focus

- Backend must stay a thin boundary around SDK, not a second runtime。
- Workspace allowlist must be deterministic and test-covered。
- Artifact API must not become arbitrary file read。
- Real provider path must remain explicit opt-in。
- Approval semantics must not imply publish unless deterministic workflow actually writes。
- DB, auth, queue, frontend must not enter Phase 39 by accident。
