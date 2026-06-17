# Java 团队后端平台技术方案

> 状态：J1 skeleton 已实现；J2 Workspace / Identity 基线已实现，包含 dev principal、workspace API、path guard、JDBC repository、Flyway migration 和 MySQL/Testcontainers 验证；J3A async AgentRun / AgentJob / local TS worker bridge 基线已实现；J4A artifact registry / list / safe text read 基线已实现；J5A approval request / decision metadata 基线已实现；J6A cancel-safe terminal guard、J6B retry policy、J6C stale lock recovery repository 和 J6D run event history 基线已实现；J7A local-dev SecurityContext、workspace role guard 和 append-only audit baseline 已实现；J8A remote HTTP worker contract spike 已实现；J9A provider runtime reference policy baseline 已实现；J10A public workspace member management baseline 已实现；J11A owner-only audit listing API baseline 已实现；J12A owner-only workspace member removal baseline 已实现；J13A current team discovery baseline 已实现；J14A backend-known team member listing baseline 已实现；J15A workspace owner transfer baseline 已实现；J16A audit pagination/filtering baseline 已实现；J17A run event SSE streaming baseline 已实现；J18A audit export baseline 已实现；J19A audit record digest baseline 已实现；J20A remote runner signature baseline 已实现；J21A remote runner production secret guard 已实现；J22A run event SSE replay cursor baseline 已实现；J23A provider runtime raw secret config guard 已实现；J23B provider runtime env-name guard 已实现；J24A provider credential metadata schema baseline 已实现；J24B provider credential metadata repository baseline 已实现；J24C provider credential service scope guard baseline 已实现；J24D provider credential runtime descriptor baseline 已实现；J24E agent-run DB-backed credential ref wiring baseline 已实现；J25A provider credential public metadata API baseline 已实现；J26A provider credential lifecycle guard baseline 已实现；J27A audit retention policy baseline 已实现；J28A audit signed record integrity baseline 已实现；剩余：WebSocket / multi-node stream fanout、完整 OIDC/OAuth、完整 user/team directory CRUD、secret manager / non-env credential secret injection 和生产级 remote runner platform。本文定义 Java 中心化团队后端的目标架构、技术选型、模块边界和分阶段路线。

## 1. 目标对齐

已确认的目标：

- 后端长期形态是一个中心服务，管理多个用户和多个 workspace。
- Phase 1 可以先本地启动，但架构方向不是个人本地 wrapper。
- Phase 1 采用服务端托管 workspace：workspace 文件由中心服务托管，agent 在服务端执行。
- 架构上保留未来 remote runner 扩展位，但第一版不实现 runner 注册、心跳、任务派发和回传协议。
- Java 后端是控制面，负责 API、用户、workspace、权限、run、approval、artifact、audit、job 和 secret 边界。
- TypeScript agent runtime 先保留为执行引擎，通过 worker/bridge 被 Java 调用。
- Java 后端只消费 `agent-backend-response.v1`，不解析 fixed workflow、deterministic open-agent 或 OpenAgentGraph 的 runtime result shape。

核心定位：

```text
Client / 未来 UI
  -> Java 中心化后端
    -> Identity / Workspace / Permission / Run / Approval / Artifact / Audit
    -> Agent Worker 桥接
      -> TypeScript Agent Worker
        -> Agent SDK / OpenAgentGraph / .agent-runs evidence
```

## 2. 目标

Java 后端第一阶段要建立的是团队化控制面，而不是替代 agent runtime。

目标：

- 提供一个中心化 API 层，所有调用方通过 `workspaceId`、`runId`、`artifactRef` 访问系统。
- 支持多用户、多 workspace 的数据模型和权限边界。
- 将 run metadata、approval、audit 写入 DB，将 execution evidence 保留为 artifact。
- 将 workspace 文件托管在服务端受控目录下，不接受任意本机路径作为 public API 输入。
- 通过稳定 worker contract 调用现有 TS agent 能力。
- 保留本地开发 profile，使初期可以在单机跑通，但不污染长期团队模型。
- 为后续 remote runner、SSE/WebSocket run updates、对象存储、SSO/RBAC 留出扩展点。

## 3. 非目标

第一版不做：

- 重写 TypeScript agent runtime 为 Java。
- 让 Java import 或理解 `src/runtime/*` 的内部 result shape。
- 前端 UI。
- 任意 workspace 文件浏览器。
- 分布式 runner 平台。
- 外部 queue / Temporal / Kafka。
- 多租户计费、组织套餐、配额结算。
- 直接发布 OpenAgent candidate patch。
- 让 API 请求携带 provider token。

## 4. 技术选型要求

技术选型必须满足这些需求：

- 支持中心化团队服务，而不是本地脚本。
- 支持清晰的模块化单体，后续可以按边界拆服务。
- 支持显式事务、状态机、审计日志和权限检查。
- 支持 server-hosted workspace 的路径隔离和 artifact 访问控制。
- 支持 run/job 异步化、取消、超时、重试和幂等。
- 支持 provider secret 安全注入，不把 token 放进 API、日志、artifact 或 DB 明文。
- 支持对外稳定 OpenAPI contract，未来前端只依赖 Java API。
- 支持 worker bridge，把 TS agent engine 当成可替换执行后端。
- 支持本地开发、CI、未来容器部署三种运行方式。

## 5. 技术选型决策

| 领域 | 决策 | 理由 | 重新评估条件 |
| --- | --- | --- | --- |
| 语言 | Java 21 LTS | 长期维护、虚拟线程可选、生态稳定 | 组织标准要求 Java 17 |
| 框架 | Spring Boot 3.x | API、配置、安全、观测和测试生态成熟 | 需要极轻量 runtime 或非 Spring 标准 |
| 构建 | Maven | 对 agent 和团队更可预测，Spring Boot 集成简单 | 团队已有 Gradle 规范 |
| HTTP | Spring Web MVC | 当前是 API + DB + job control plane，MVC 足够且易测 | 大量高并发 streaming 成为主路径 |
| 认证 | Spring Security | 先支持 dev token，后续接 OIDC/SSO/RBAC | 组织已有统一网关强制接入 |
| DB | MySQL first | 团队基础设施通常更容易落在 MySQL；metadata、权限、audit、job 只需要可靠关系型存储，不应依赖 PostgreSQL 专有能力 | 组织标准明确要求 PostgreSQL，或后续查询能力确实需要 PostgreSQL 特性 |
| 数据库变更 | Flyway | schema 变更可审计、可回滚、适合 CI | 组织已有 Liquibase 标准 |
| 数据访问 | Spring JDBC first | run 状态机和 audit 更适合显式 SQL，先避免 ORM 和 codegen 复杂度 | 查询复杂度上升且 schema 稳定后评估 jOOQ |
| API 文档 | springdoc-openapi | 给未来前端和 worker contract 提供机器可读 API | 使用公司内部 API 网关规范 |
| JSON | Jackson | Spring 默认，类型控制成熟 | 需要跨语言 schema-first codegen |
| 测试 | JUnit 5, AssertJ, Testcontainers | 可覆盖 DB migration、repository、HTTP integration | CI 不允许 Docker 时增加本地替代 |
| 可观测性 | Micrometer + structured logs | run/job/audit 需要可观测性 | 接入公司统一 tracing |
| Artifact 存储 | filesystem abstraction first | 当前 `.agent-runs` 已是事实源，先最小接入 | 团队部署需要 S3/MinIO |
| 异步执行 | Phase J3 DB-backed job table | agent run 从接入 worker 开始就可能慢、可失败、需轮询；J3 必须异步，不让 HTTP 请求阻塞执行完成 | 并发和吞吐超过单 DB job 能力 |
| Agent bridge | Java `AgentWorker` interface + local TS worker | 保持 Java control plane 和 TS execution engine 解耦 | TS runtime 被 Java runtime 替代 |

具体小版本属于实现期决策。脚手架 Java 代码前，必须基于当前官方 release notes 和本地构建环境确认 Spring Boot、Testcontainers、Spring JDBC 与插件版本。

## 6. 目标架构

### 6.1 运行时视图

```text
Browser / CLI / API Client
  -> Java Backend API
    -> Auth Filter
    -> Workspace Permission Guard
    -> Application Services
      -> MySQL metadata
      -> Artifact storage
      -> Audit log
      -> AgentWorker
        -> Local TS worker process or local HTTP worker
          -> runBackendAgent()
            -> agent-backend-response.v1
```

### 6.2 模块视图

推荐的 Java package/module 边界：

- `bootstrap`
  - Spring Boot 应用启动、profile 加载、生命周期装配。
  - 不包含领域规则。
- `api`
  - Controller、request/response DTO、错误 envelope、OpenAPI 标注。
  - 不直接调用 TS worker。
- `identity`
  - User、team、membership、role、auth principal。
  - 第一阶段从 dev principal 开始，后续演进到 OIDC。
- `workspace`
  - Workspace 注册、存储位置、成员权限、服务端路径解析。
  - 负责 path traversal 防护和 workspace 级锁。
- `run`
  - AgentRun 聚合、RunAttempt、状态流转、幂等、超时和取消。
  - 负责将后端请求映射为 agent 执行 job。
- `approval`
  - 处理 candidate patch、route preview 和未来 publish 流程中的人工审批决策。
  - 任何副作用发生前先持久化 approval event。
- `artifact`
  - ArtifactRef 注册、artifact 读取策略、redaction metadata、retention hooks。
  - public API 中不暴露原始绝对文件路径。
- `agentbridge`
  - Java `AgentWorker` port 与 TS worker adapter 实现。
  - 只理解 `agent-backend-response.v1`。
- `providersecret`
  - Provider credential reference、secret 解析、per-run secret injection policy。
  - API 接收 credential reference，不接收 token 值。
  - J24A 后，DB 中仅保存 provider credential metadata 和 secret reference，不保存 raw secret value。
  - J24B 后，JDBC repository 只读写 metadata，并按 team/workspace scope 解析 ACTIVE credential ref，不读取 raw secret value。
  - J24C 后，内部 service 通过 `WorkspaceService` 做 editor 级 workspace guard 后再解析 credential metadata，避免绕过 workspace/team scope。
  - J24D 后，内部 service 可把已授权 credential metadata 转成 secret-safe runtime descriptor，包含 provider/model/baseUrl/apiKeySecretRef reference，但仍不读取 raw secret value。
  - J24E 后，agent-run 创建路径可通过现有 `providerRuntimeRef = "credential.<credentialRef>"` 解析 DB-backed credential metadata；只把 `env://NAME` 转成 worker `apiKeyEnvName`，不把 `apiKeySecretRef` 或 raw secret 传给 worker。
  - J25A 后，workspace owner 可通过 JDBC-profile public API upsert/list workspace-scoped env-backed credential metadata；public response 不返回 env name、`apiKeySecretRef`、raw secret、token 或 Authorization material。
  - J26A 后，workspace owner 可禁用 workspace-scoped credential metadata；禁用不删除 audit history，public response 仍不返回 env name 或 secret ref，disabled credential ref 不会被 agent-run 解析。
- `audit`
  - 记录用户操作、run 流转、approval 和 workspace 写入的 append-only audit event。
- `ops`
  - Health、readiness、build info、metrics、diagnostics。

## 7. 领域模型

团队化第一版需要的最小实体：

```text
User
  id
  externalSubject
  displayName
  status

Team
  id
  name

TeamMembership
  teamId
  userId
  role

Workspace
  id
  teamId
  name
  storageMode
  serverStorageRef
  defaultBranch
  status

WorkspaceMember
  workspaceId
  userId
  role

ProviderCredential
  id
  credentialRef
  teamId
  workspaceId
  provider
  model
  baseUrl
  apiKeySecretRef
  status
  createdAt
  updatedAt

AgentRun
  id
  workspaceId
  requestedByUserId
  userMessage
  mode
  status
  outputKind
  requiresApproval
  requiresConfirmation
  wroteWorkspace
  createdAt
  updatedAt

AgentJob
  id
  runId
  status
  priority
  availableAt
  lockedBy
  lockedUntil
  attemptCount
  maxAttempts
  createdAt
  updatedAt

RunAttempt
  id
  runId
  jobId
  workerKind
  startedAt
  finishedAt
  exitCode
  errorCode

RunEvent
  id
  eventSequence
  runId
  eventType
  status
  message
  createdAt

ApprovalRequest
  id
  runId
  requestedByUserId
  decidedByUserId
  decision
  status
  artifactRef
  targetWorkspacePaths

ArtifactRef
  id
  runId
  kind
  storageRef
  redactionStatus
  contentType

AuditEvent
  id
  actorUserId
  teamId
  workspaceId
  runId
  eventType
  eventPayload
  createdAt
```

规则：

- Public API 使用 opaque ID 和 ref，不使用绝对路径。
- `Workspace.serverStorageRef` 仅限内部使用。
- `ArtifactRef.storageRef` 仅限内部使用。
- `targetWorkspacePaths` are proposed paths for candidate patch; they are not write evidence.
- `wroteWorkspace=true` can only come from SDK/backend response evidence plus backend-side write audit.
- `RunEvent.eventSequence` is an internal durable append-order field for JDBC replay/cursor semantics; it is not exposed in public `RunEventResponse`.

## 8. API 设计

优先使用 versioned REST：

```http
GET  /health
GET  /ready

GET  /v1/me
GET  /v1/teams
GET  /v1/teams/{teamId}/members

POST /v1/workspaces
GET  /v1/workspaces
GET  /v1/workspaces/{workspaceId}
GET  /v1/workspaces/{workspaceId}/members
PUT  /v1/workspaces/{workspaceId}/members/{userId}
DELETE /v1/workspaces/{workspaceId}/members/{userId}
POST /v1/workspaces/{workspaceId}/owner-transfer
GET  /v1/workspaces/{workspaceId}/audit-events
GET  /v1/workspaces/{workspaceId}/audit-events/export
GET  /v1/workspaces/{workspaceId}/provider-credentials
PUT  /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}

POST /v1/workspaces/{workspaceId}/agent-runs
GET  /v1/agent-runs/{runId}
GET  /v1/workspaces/{workspaceId}/agent-runs
GET  /v1/agent-runs/{runId}/events
GET  /v1/agent-runs/{runId}/events/stream

GET  /v1/agent-runs/{runId}/artifacts
GET  /v1/artifacts/{artifactId}

POST /v1/agent-runs/{runId}/approvals
POST /v1/agent-runs/{runId}/cancel
```

通用响应 envelope：

```json
{
  "schemaVersion": "java-backend-api.v1",
  "ok": true,
  "data": {}
}
```

错误响应：

```json
{
  "schemaVersion": "java-backend-api.v1",
  "ok": false,
  "error": {
    "code": "WORKSPACE_FORBIDDEN",
    "message": "The current user cannot access this workspace.",
    "retryable": false
  }
}
```

API 规则：

- `POST /agent-runs` 接收 `workspaceId`，不接收 `workspaceRoot`。
- `workspaceRoot` 只存在于 Java 服务内部，由 workspace 解析后得到。
- `POST /agent-runs` 创建 run 和异步 job，然后返回 run/job metadata，不等待 agent 执行完成。
- Phase J3 客户端通过 `GET /v1/agent-runs/{runId}` polling；Phase J6D 客户端可通过 `GET /v1/agent-runs/{runId}/events` 读取 durable lifecycle event history；Phase J17A 客户端可通过 `GET /v1/agent-runs/{runId}/events/stream` 接收同一批 backend-owned lifecycle events 的 SSE baseline，不改变 run identity；Phase J22A 客户端可在重连时传 `Last-Event-ID` 跳过已消费的 durable event，JDBC 顺序由 `run_events.event_sequence` 保证，不依赖 timestamp/random id tie-break。
- Phase J18A 客户端可通过 `GET /v1/workspaces/{workspaceId}/audit-events/export` 以 NDJSON 导出已有 audit metadata；该导出仍只面向 workspace owner，字段与 audit list public response 一致。
- Phase J19A audit list/export public response 包含 `recordDigest`，格式为 `sha256:<64 lowercase hex chars>`；Phase J28A 后该 digest 在 append 时持久化，并增加 `previousRecordDigest`、`chainDigest`、`signatureKind`、`signatureValue` 本地完整性字段。
- Phase J20A remote-http worker 可通过 `remote-http.signature-secret` 校验 signed remote runner result envelope；该校验使用 fixed field name + UTF-8 length-prefixed canonical payload，不改变 public API response，也不让 Java 解析 runtime-private result shape。
- Phase J21A 在 `prod` / `production` profile 下要求 remote-http worker 配置非空 `remote-http.signature-secret`，避免生产误用 unsigned local spike。
- run 创建需要通过 `Idempotency-Key` 支持幂等。
- API response 可以包含 `displayText`、`status`、`outputKind`、`artifactRefs`、approval flags 和 workspace write evidence。
- API response 不得包含 provider token、raw Authorization header、本地绝对路径或 TS runtime-private result field。
- Run event response 只暴露后端拥有的事件 metadata，不内联 provider payload、worker raw output、workspace internal path 或 runtime-private source。

## 9. Agent Worker 契约

Java 拥有这个 port：

```java
interface AgentWorker {
  AgentWorkerResponse run(AgentWorkerRequest request);
}
```

概念请求：

```text
AgentWorkerRequest
  runId
  workspaceInternalPath
  userMessage
  mode
  execute
  autoApprove
  providerRuntimeRef
  secretInjectionPolicy
```

概念响应：

```text
AgentWorkerResponse
  schemaVersion = agent-backend-response.v1
  runId
  status
  outputKind
  displayText
  requiresConfirmation
  requiresApproval
  artifactRefs
  wroteWorkspace
  targetWorkspacePaths
```

规则：

- Java adapter 只解析 `agent-backend-response.v1`。
- TS adapter 返回的 `source` 可以作为 redacted diagnostic evidence 存储，但 Java 业务逻辑不得根据其中 runtime-private field 分支。
- Worker log 必须 redaction provider credential 和 raw provider payload。
- J9A/J23A/J23B/J24E/J25A/J26A provider runtime reference policy 是当前 secret-safe baseline：public run request 只接受 `providerRuntimeRef`；Java 根据 backend config 或 `credential.<credentialRef>` DB metadata 解析 provider metadata 和 `apiKeyEnvName`；workspace owner 可用 public metadata API 写入 env-backed credential reference，也可禁用 workspace-scoped credential metadata；请求只接受 env var name，不接受 raw API key/token/Authorization value；worker request 可以包含 env var name，但不能包含 raw API key/token/Authorization value；configured refs 如果包含 `api-key`、`token` 或 `authorization` raw secret config key，会 fail closed；`api-key-env-name` 必须是 env-var-style identifier；DB-backed credential refs 只有 ACTIVE metadata 和 `env://NAME` 会被转成 worker env-name metadata，disabled refs 和其他 secret ref scheme 会在 worker 执行前拒绝。
- Provider secret 通过受控环境变量、process stdin、权限受限的本地 secret file 或 worker 侧 secret provider 注入，不通过 JSON API body 传递。
- local TS worker 是 Phase 1 adapter；未来 remote runner 实现同一个逻辑契约。

## 10. Workspace 与 Artifact 边界

### Workspace

服务端托管 workspace 的初始目录布局可以是：

```text
<dataRoot>/teams/<teamId>/workspaces/<workspaceId>/content
<dataRoot>/teams/<teamId>/workspaces/<workspaceId>/artifacts
```

当前 TS runtime 会在 workspace 下写 `.agent-runs`。Phase 1 可以保留这个行为，但 Java 只对外暴露逻辑 artifact ref。

规则：

- Workspace root 由 DB 和 server config 解析得到。
- Public API 不能提交任意绝对路径。
- 调用 worker 前必须拒绝 path traversal。
- 同一 workspace 的并发 run 必须有明确策略：
  - read-only answer 可以并发；
  - candidate patch 是 artifact-only，可以并发；
  - workflow execution 或未来 publish 需要 workspace-level write lock。

### Artifact

Artifact 服务职责：

- 注册 worker 返回的 artifact ref。
- 将 ref 解析为内部 storage path。
- 读取前检查 workspace/run 权限。
- 阻断绝对路径和 `..` traversal。
- 只有在显式请求且已标记安全时，才返回 redacted raw provider artifact。
- artifact retention 与 DB metadata retention 分开管理。

## 11. Run 状态机

即使 SDK 内部状态演进，后端 run status 也应保持稳定：

```text
CREATED
QUEUED
RUNNING
WAITING_CONFIRMATION
WAITING_APPROVAL
SUCCEEDED
SUCCEEDED_WITH_WARNINGS
FAILED
CANCELED
```

SDK/backend response 到 Java run status 的初始映射：

| Agent backend status | Java run status |
| --- | --- |
| `SUCCEEDED` | `SUCCEEDED` |
| `SUCCEEDED_WITH_WARNINGS` | `SUCCEEDED_WITH_WARNINGS` |
| `WAITING_APPROVAL` | `WAITING_APPROVAL` |
| `NEEDS_CONFIRMATION` | `WAITING_CONFIRMATION` |
| `FAILED_ROUTE` | `FAILED` |
| `FAILED_PROVIDER` | `FAILED` |
| `FAILED_POLICY` | `FAILED` |
| `FAILED` | `FAILED` |

规则：

- `requiresApproval=true` 创建或更新 approval record。
- `requiresConfirmation=true` 创建 confirmation state，不创建 approval decision。
- `wroteWorkspace=true` 必须记录 audit，包含 run id、actor、workspace id 和 affected paths。
- DB-backed `AgentJob` 在第一个 worker bridge phase 就引入，不后置。
- 失败的 worker attempt 存为 `RunAttempt`；retry policy 耗尽后，父 `AgentRun` 才进入最终状态。
- J6A 仅支持 queued/running run 的 cancel-safe baseline：`POST /v1/agent-runs/{runId}/cancel` 将可取消 run/job/open attempt 持久化为 `CANCELED`，迟到的 worker `complete()` / `fail()` 不得覆盖 `CANCELED`。
- J6B retry policy 是 in-process baseline：worker exception 触发最多 `AgentJob.maxAttempts` 次立即重试；每个失败 attempt 都关闭为 `FAILED`，成功重试才写最终 response，耗尽后父 run 进入 `FAILED`。
- J6C stale lock recovery 是 repository baseline：`failStaleRunningJobs(staleBefore, errorCode, now)` 将 `lockedUntil <= staleBefore` 的 running job、run 和 open attempt deterministic fail；scheduler/ops endpoint 后置。
- J6D run event history 是 durable metadata baseline：`AgentRunService` append `RUN_QUEUED`、`RUNNING`、`RETRY_QUEUED`、`COMPLETED`、`FAILED`、`CANCELED`；`GET /v1/agent-runs/{runId}/events` 复用 run/workspace guard 后按创建顺序返回事件。J17A 在此基础上补充 `GET /v1/agent-runs/{runId}/events/stream` SSE baseline，payload 仍只包含 public `RunEventResponse` 字段。J22A 补充 `Last-Event-ID` reconnect cursor：若 cursor 命中当前 run event list，则 stream 跳过该 event 及其之前 events；若 cursor 未知，则回退为完整 replay。J22A 同时为 JDBC `run_events` 增加 `event_sequence` 持久 append 序，避免同 timestamp 下 random id tie-break 破坏 cursor 语义。J6D/J17A/J22A 不等同于 WebSocket、多节点 fanout、broker-backed streaming、remote runner live channel 或 audit log。

## 12. Approval 模型

Approval 是后端拥有的边界。

流程：

- `candidate-patch`：需要 approval；approval 不会自动通过 OpenAgent publish。
- `route-preview`：fixed workflow execution 前需要 approval。
- `confirmation`：需要用户补充信息，不是 approval。
- fixed workflow execution：只有后端发起 execution path 且 SDK evidence 表明已写入时，才允许写 workspace。

Approval event 规则：

- 任何副作用前先持久化 decision。
- rejection 不能写 workspace。
- approval 必须包含被批准的 artifact ref 或 route preview。
- 后续 patch application 可以作为单独的 deterministic publisher path，并拥有自己的 audit。

## 13. 安全模型

Phase 1 local profile 可以运行在 trusted mode，但代码结构必须已经支持团队安全模型。

当前实现状态：J7A 已实现 Spring Security local-dev baseline。请求会通过 dev-header filter 写入 `SecurityContext`，支持 `X-Dev-User-Id`、`X-Dev-Team-Id`、`X-Dev-Display-Name` 覆盖；没有 dev header 时回退到配置里的 dev principal。workspace membership 是 role truth source，服务层按 role hierarchy 执行 guard，并为敏感动作追加 audit metadata。J7A 不是完整 OIDC/OAuth。

J9A 已实现 provider runtime reference policy baseline。`POST /v1/workspaces/{workspaceId}/agent-runs` 可以传 `providerRuntimeRef`，但不能传 raw `providerRuntime` object 或 token value。`ProviderRuntimePolicy` 只读取 backend config metadata，并把 `apiKeyEnvName` 传给 worker；Java 不读取 secret value，不把 token 写入 DB、日志、artifact 或 API response。J23A 后续补充 raw secret config guard：configured provider ref 中出现非空 `api-key`、`token` 或 `authorization` 会直接拒绝解析，避免把 secret 值沉入 backend config 作为 metadata 使用。J23B 后续补充 `api-key-env-name` format guard：该值必须像环境变量名，避免把 secret 值伪装成 env-name metadata。

J24A 已实现 provider credential metadata schema baseline。Flyway V9 新增 `provider_credentials`，用于保存 `credential_ref`、`team_id`、可选 `workspace_id`、provider/model/base_url、`api_key_secret_ref`、status 和时间戳。该表不包含 `api_key`、`token`、`authorization`、`secret_value` 或 `raw_secret` 列。J24B 已实现 provider credential metadata repository baseline：`ProviderCredentialRepository` 可保存 metadata，并按 `team_id`、`workspace_id`、`credential_ref` 查找 ACTIVE credential；team-scoped credential 使用 `workspace_id IS NULL`，workspace-scoped credential 只在匹配 workspace 时返回，disabled/cross-team/cross-workspace 记录不会被解析。J24C 已实现 provider credential service scope guard baseline：`ProviderCredentialService` 校验 credential ref，要求当前 principal 对 workspace 具备 `WORKSPACE_EDITOR` 权限，再使用 workspace record 中的 `teamId` / `workspaceId` 调 repository；viewer、跨 team principal 或非法 ref 不会触发 credential metadata lookup。J24D 已实现 provider credential runtime descriptor baseline：同一 service 可将已授权 metadata 转成 `ProviderCredentialRuntimeDescriptor`，保留 provider/model/baseUrl 和 `apiKeySecretRef` reference，并拒绝 unsupported provider id、空白或非 URI 形态的 plain secret reference；支持的 reference scheme 当前限制为 `env://`、`secret://`、`keychain://` 和 `file://`。J24E 已实现 agent-run DB-backed credential ref wiring baseline：`POST /v1/workspaces/{workspaceId}/agent-runs` 继续复用现有 `providerRuntimeRef` 字段，值为 `credential.<credentialRef>` 时通过 `ProviderCredentialService` 解析已授权 metadata；只有 `env://NAME` 会被转换成 worker `apiKeyEnvName`，非 `env://` secret ref 在 worker 执行前 fail closed；worker request 不包含 `apiKeySecretRef` 或 raw secret。J25A 已实现 provider credential public metadata API baseline：`PUT /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}` 允许 workspace owner 写入 workspace-scoped env-backed metadata，`GET /v1/workspaces/{workspaceId}/provider-credentials` 允许 owner 列出 workspace-scoped public metadata；请求显式拒绝 `apiKey`、`token`、`authorization`、`Authorization`、`apiKeySecretRef` 等 raw-secret alias，public response 不返回 `apiKeyEnvName`、`apiKeySecretRef` 或 secret material。J26A 已实现 provider credential lifecycle guard baseline：`POST /v1/workspaces/{workspaceId}/provider-credentials/{credentialRef}/disable` 允许 workspace owner 把 workspace-scoped credential metadata 标记为 `DISABLED`；禁用不会物理删除 metadata 或 audit history，owner list 仍可看到 public metadata，run path 因只解析 ACTIVE credential 而拒绝 disabled ref。该 service、repository 与 controller 一样只在 `jdbc` profile 下装配。J24A/J24B/J24C/J24D/J24E/J25A/J26A 不是 secret manager、KMS、key rotation、non-env secret injection、真实 provider 调用、team-scoped credential API 或 remote runner secret distribution。

J10A 已实现 public workspace member management baseline。`GET /v1/workspaces/{workspaceId}/members` 允许 workspace viewer 或更高角色读取成员列表；`PUT /v1/workspaces/{workspaceId}/members/{userId}` 只允许 owner 授予 `WORKSPACE_VIEWER` 或 `WORKSPACE_EDITOR`，拒绝 public owner grant，并要求 request `teamId` 与 workspace team 一致。成员响应只暴露 `workspaceId`、`userId`、`teamId`、`role`，不暴露 `workspaceRoot`、`serverStorageRef`、provider payload 或 token。成员 grant 会追加 `WORKSPACE_MEMBER_GRANTED` workspace-level audit event。J10A 不是完整 user/team directory、邀请、移除成员、owner 转移或 audit listing API；J12A 后续补充成员移除，J15A 后续补充 owner transfer。

J11A 已实现 owner-only audit listing API baseline。`GET /v1/workspaces/{workspaceId}/audit-events` 要求 `WORKSPACE_OWNER`，返回 audit metadata：`auditEventId`、`actorUserId`、`teamId`、`workspaceId`、`runId`、`eventType`、`message`、`createdAt`。响应不内联 workspace internal path、provider payload、Authorization header、token 或 runtime-private source。J11A 不是 audit export、public audit write API 或 signed audit log；J16A 后续补充 audit pagination/filtering baseline。

J12A 已实现 owner-only workspace member removal baseline。`DELETE /v1/workspaces/{workspaceId}/members/{userId}` 要求 `WORKSPACE_OWNER`，只允许移除非 owner 成员；viewer 调用会得到 `WORKSPACE_FORBIDDEN`，删除 owner 会得到 `VALIDATION_ERROR`。成功删除返回被移除成员的 `workspaceId`、`userId`、`teamId`、`role`，删除后该成员不再能读取 workspace，并追加 `WORKSPACE_MEMBER_REMOVED` audit event。J12A 不是邀请、team/user directory CRUD 或跨团队成员管理；J15A 后续补充 owner transfer。

J13A 已实现 current team discovery baseline。`GET /v1/teams` 返回当前 authenticated principal 所在团队的稳定 metadata：`teamId`、`name`、`status`。当前实现只用于本地/dev profile 和未来 UI/team selector 的最小 discovery，不读取全局 team directory，不暴露 internal path、provider payload 或 token。J13A 不是完整 user/team directory CRUD、邀请、跨团队 discovery 或 team member listing；J14A 后续补充了 backend-known team member listing。

J14A 已实现 backend-known team member listing baseline。`GET /v1/teams/{teamId}/members` 只允许当前 principal 查询自己的 team，跨 team 请求返回 `TEAM_FORBIDDEN`。响应返回 `teamId`、`userId`、`role`，其中 workspace 创建会记录 owner 为 `TEAM_ADMIN`，workspace grant 会记录目标用户为 `TEAM_MEMBER`；workspace member removal 只撤销 workspace access，不删除 team membership。J14A 不返回 display profile、email、邀请状态、全局 user directory、跨团队 discovery、team CRUD 或生产 OIDC 组织角色。

J15A 已实现 workspace owner transfer baseline。`POST /v1/workspaces/{workspaceId}/owner-transfer` 要求当前 principal 是 `WORKSPACE_OWNER`，目标必须是同 workspace/team 的既有非 owner 成员；self-transfer、missing target 和 already-owner target 返回 `VALIDATION_ERROR`，viewer 调用返回 `WORKSPACE_FORBIDDEN`。成功后目标升为 `WORKSPACE_OWNER`，旧 owner 降为 `WORKSPACE_EDITOR`，响应复用 workspace member response 且不暴露 `workspaceRoot`、`serverStorageRef`、provider payload 或 token。J15A 不改变 team membership，不把新 workspace owner 升为 `TEAM_ADMIN`，并写入 `WORKSPACE_OWNER_TRANSFERRED` audit event。

J16A 已实现 audit pagination/filtering baseline。`GET /v1/workspaces/{workspaceId}/audit-events` 保持 owner-only guard 和 `data` 数组响应，同时支持 query params：`limit`（1..100，默认 100）、`offset`（默认 0）、`eventType`、`runId`。过滤和分页在 in-memory/JDBC repository 层共用 contract，响应仍不暴露 internal path、provider payload、Authorization header、token 或 runtime-private source。J16A 不是 audit export、signed audit record 或 public audit write API。

J17A 已实现 run event SSE streaming baseline。`GET /v1/agent-runs/{runId}/events/stream` 在请求打开时复用现有 run/workspace guard，成功后以 `text/event-stream` 发送 durable run lifecycle events；每个 SSE event 的 `id` 来自 backend event id，`event` 来自 backend event type，`data` 复用 public `RunEventResponse`。未授权或缺失 run 在 stream 打开前返回 HTTP 403/404 且不启动异步流。stream 观察到终态 lifecycle event 后结束；如果 bounded stream window 到期而未观察到终态 event，EOF 只表示本次窗口结束，客户端应重连或回退 polling，不能把 EOF 当作 run terminal evidence。J22A 已补充 `Last-Event-ID` reconnect cursor：命中时跳过已消费 event，未知 cursor 回退完整 replay；JDBC event list 使用 `event_sequence` append 序，避免同 timestamp 下 random id tie-break 破坏 replay 范围。SSE payload 不暴露 internal path、provider payload、Authorization header、token 或 runtime-private source。J17A/J22A 不是 WebSocket、broker fanout、多节点 stream routing、remote runner live streaming 或生产级 backpressure/heartbeats。

J18A 已实现 audit export baseline。`GET /v1/workspaces/{workspaceId}/audit-events/export` 要求 `WORKSPACE_OWNER`，使用 `application/x-ndjson` 返回已有 workspace audit metadata；每一行都是与 audit list 相同的 public `AuditEventResponse` 字段：`auditEventId`、`actorUserId`、`teamId`、`workspaceId`、`runId`、`eventType`、`message`、`createdAt`。导出复用 J16A 的 `limit`、`offset`、`eventType`、`runId` query params 和校验边界；非法 query、未授权或 workspace 缺失会在导出前返回 `java-backend-api.v1` JSON error envelope。J18A 不导出 raw audit payload、workspace internal path、server storage ref、provider payload、Authorization header、token 或 runtime-private source；也不包含 signed audit records、async export job、object storage 或 public audit write API。

J19A 已实现 audit record digest baseline。audit list 和 audit export 的 public `AuditEventResponse` 增加 `recordDigest`，格式为 `sha256:<64 lowercase hex chars>`。digest input 只包含 public audit metadata：`auditEventId`、`actorUserId`、`teamId`、`workspaceId`、`runId`、`eventType`、`message`、`createdAt`；不包含 raw audit payload、provider payload、Authorization header、token、workspace internal path、server storage ref 或 runtime-private source。J27A 已实现 audit retention policy baseline：owner 可读取 `GET /v1/workspaces/{workspaceId}/audit-events/retention-policy`，响应仅包含 workspaceId、retentionDays、`REPORT_ONLY` mode、`destructivePurgeEnabled=false` 和 policySource；该阶段不删除或修改 audit 记录。J28A 已实现 audit signed record integrity baseline：新写入 audit event 会持久化 `recordDigest`、`previousRecordDigest`、`chainDigest`、`signatureKind="sha256-chain-v1"`、`signatureValue=chainDigest`，list/export 直接返回这些持久化字段。J28A 是本地 SHA-256 hash-chain 完整性基线，不是外部 KMS、私钥签名、non-repudiation、历史记录回填、retention execution 或 public audit write API。

安全规则：

- 每个非 health API 请求都解析 authenticated principal。
- 每个 workspace API 都检查 membership 和 role。
- 默认不返回 workspace internal path。
- API request 不接受 provider token value。
- Provider credential reference 按 team/workspace 限定范围。
- Artifact read 需要 workspace/run permission。
- 错误响应不包含 stack trace、token fragment、本地路径或 raw provider payload。
- Audit 在应用层保持 append-only。

建议角色：

- `TEAM_ADMIN`：管理 team、user、credential 和 workspace。
- `WORKSPACE_OWNER`：管理 workspace config 和 approval。
- `WORKSPACE_EDITOR`：可以发起 run，并审批被允许的变更。
- `WORKSPACE_VIEWER`：可以读取 run 和 artifact，不能执行写入。

## 14. 本地开发配置

Local-first 应该是部署配置，而不是另一套架构。

本地配置：

- 启动一个 Java backend process。
- 使用 Docker local MySQL 或 Testcontainers-backed dev setup。
- 自动创建 dev user 和 dev team。
- 在配置的 data root 下注册一个或多个 server-hosted workspace。
- 默认使用 fake provider。
- 只有显式配置并采用 secret-safe injection 时，才允许 real provider。
- 调用 local TS worker。

这样可以保持 API、DB model 和 worker boundary 与团队部署一致。

## 15. Remote Runner 扩展

Remote runner 不进入 Phase 1，但设计需要保留这些扩展位：

当前实现状态：J8A 已实现 remote runner contract spike。`AgentWorker` 暴露 `workerKind()`，默认 local TS worker 仍是 `LOCAL_TS_WORKER`；配置 `my-workflow.backend.agent-worker.kind=remote-http` 时，Java 使用 `RemoteHttpAgentWorker` POST 现有 `AgentWorkerRequest`，验证 `agent-remote-runner-result.v1` 传输 envelope，并把嵌套 `agent-backend-response.v1` 交给既有 run 状态机。J20A 后续补充 remote runner signature baseline：配置 `my-workflow.backend.agent-worker.remote-http.signature-secret` 后，Java 要求 `signatureKind=hmac-sha256` 和 `signature=hmac-sha256:<base64>`，并用该 secret 对 envelope identity 与嵌套 public `agent-backend-response.v1` 字段做 HMAC-SHA256 校验；canonical payload 使用 fixed field name + UTF-8 byte length prefix，避免换行或 list separator 导致字段边界碰撞。J21A 后续补充 production secret guard：当 active profile 包含 `prod` 或 `production` 时，remote-http worker 构造阶段要求非空 `remote-http.signature-secret`；未配置 secret 的 `signatureKind=unsigned-local-spike` 只保留为本地/default profile spike 路径。J20A/J21A 不等同于 runner 身份体系、key rotation、KMS/secret manager、mTLS、runner registration、heartbeat 或 lease。

- `AgentWorker` 是 interface，不是 concrete local process dependency。
- `RunAttempt.workerKind` 区分 `LOCAL_TS_WORKER` 和未来的 `REMOTE_RUNNER`。
- Workspace storage model 后续可以增加 `REMOTE_MOUNT`、`GIT_CHECKOUT` 或 `RUNNER_OWNED`。
- Run 状态机已经支持 queued/running/canceled/failed attempt。
- Secret injection policy 属于 worker request context，不硬编码在 TS process launch 中。

未来 runner 能力：

- runner 注册；
- heartbeat；
- capability advertisement；
- job lease；
- artifact upload；
- signed result envelope；
- cancellation；
- runner-scoped credential access。

## 16. 阶段路线

### Phase J1: Java 平台骨架

交付：

- Spring Boot app skeleton。
- health/readiness endpoint。
- common API envelope。
- profile config。
- 空模块边界。
- test harness。

验收：

- Java tests pass。
- OpenAPI 包含 health/readiness。
- 暂不接 agent execution。

### Phase J2: Workspace 与 Identity 基线

交付：

- dev principal。
- Team/User/Workspace schema。
- workspace registration。
- server path resolution。
- workspace permission guard。

验收：

- API 使用 `workspaceId`，不使用绝对路径。
- unauthorized workspace access 被拒绝。
- path traversal tests pass。

### Phase J3: 异步 Agent Run 与 Local TS Worker 桥接

当前实现状态：J3A 已实现 async run baseline，包括 `POST /v1/workspaces/{workspaceId}/agent-runs`、`GET /v1/agent-runs/{runId}`、DB-backed run/job/attempt schema、in-process executor、local TS worker adapter 和 `agent-backend-response.v1` 顶层字段映射。J3A 不包含 cancel/retry policy 深化、approval decision API、artifact registry 或 remote runner。

交付：

- AgentRun/RunAttempt schema。
- AgentJob schema 和 DB-backed job state。
- `POST /v1/workspaces/{workspaceId}/agent-runs`。
- `GET /v1/agent-runs/{runId}` polling 基线。
- in-process job executor。
- local TS worker adapter。
- `agent-backend-response.v1` 到 Java run state 的映射。

验收：

- `POST /agent-runs` 返回 run/job metadata，不等待 worker 完成。
- fake-provider answer run 可以通过 Java API 和 polling 异步完成。
- candidate patch 返回 approval-required，且不写 workspace。
- Java code 不解析 runtime-private result field。

### Phase J4: Artifact 与 Run 检视

当前实现状态：J4A 已实现 artifact registry 基线，包括 `artifact_refs` schema、worker artifact ref 注册、`GET /v1/agent-runs/{runId}/artifacts`、`GET /v1/artifacts/{artifactId}`、workspace path guard 解析和 public response 不暴露 server absolute path。J4A 不包含对象存储、retention、binary streaming、raw provider unredacted access 或审批发布。

交付：

- artifact registry。
- run inspect endpoint。
- artifact read endpoint。
- redaction 和 traversal guard。

验收：

- artifact ref 只能在 authorized workspace/run 范围内解析。
- raw provider ref 保持 redacted。
- public response 中不出现 server absolute path。

### Phase J5: Approval 边界

当前实现状态：J5A 已实现 approval metadata 基线，包括 `approval_requests` schema、approval-required worker response 自动创建 pending request、`GET /v1/agent-runs/{runId}/approvals`、`POST /v1/agent-runs/{runId}/approvals` decision metadata。J5A 不执行 candidate patch publish，不在 approval 后触发 fixed workflow execution，也不包含 audit/RBAC。

交付：

- approval schema。
- approval API。
- route preview approval。
- candidate patch approval record。

验收：

- execution 前已持久化 approval event。
- rejection 永远不写 workspace。
- candidate patch 保持 artifact-only。

### Phase J6: Job 可靠性与 Run 更新

当前实现状态：J6A 已实现 cancel-safe baseline，包括 `POST /v1/agent-runs/{runId}/cancel`、in-memory/JDBC repository `cancel(runId, now)`、queued/running run/job/open attempt 的 `CANCELED` 持久化，以及迟到 worker success/failure 不覆盖 `CANCELED` 的终态保护。J6B 已实现 retry policy baseline，包括 in-process executor 按 `AgentJob.maxAttempts` 立即重试 worker exception、repository `retry(runId, jobId, errorCode, now)` 关闭 failed attempt 并重新入队、transient failure 后成功完成、耗尽后最终 `FAILED`。J6C 已实现 stale lock recovery repository baseline，包括 `failStaleRunningJobs(staleBefore, errorCode, now)` deterministic fail stale running run/job/open attempt。J6D 已实现 run event history baseline，包括 `run_events` schema、in-memory/JDBC event repository、`GET /v1/agent-runs/{runId}/events` 和 backend-owned lifecycle events。J17A 后续补充 run event SSE streaming baseline，J22A 后续补充 `Last-Event-ID` reconnect cursor baseline 和 JDBC `event_sequence` append ordering。J6A/J6B/J6C/J6D/J17A/J22A 不中断或 kill in-flight local worker process，不实现 delayed retry scheduler、scheduler wiring、WebSocket、多节点 fanout、audit 或 remote runner live channel。

交付：

- retry policy。
- timeout/cancel hardening。
- stale lock recovery。
- run progress event。
- optional SSE/WebSocket update channel。

验收：

- long-running run 仍使用 J3 async path。
- canceled job 可以停止或安全进入 terminal state。
- stale running job 可以 deterministic recover 或 fail。
- backend clients can inspect durable run lifecycle events without parsing worker/runtime internals.

### Phase J7: Team Auth 与 RBAC

当前实现状态：J7A 已实现 local-dev SecurityContext principal、workspace role guard 和 append-only audit baseline。已覆盖 `GET /v1/me` dev header override/default fallback、viewer 只读边界、editor/owner run/approval 写权限、workspace/run/artifact/approval 关键动作 audit metadata，以及 JDBC audit persistence。J7A 不包含完整 OIDC/OAuth、public user/team management API、member removal/owner transfer、remote runner authorization、SSE/WebSocket authorization 或 provider secret policy。J11A 后续补充 owner-only audit listing API baseline，J12A 后续补充 member removal，J15A 后续补充 owner transfer。

交付：

- Spring Security integration。
- OIDC-ready principal mapping。
- role-based workspace permission。
- user action 的 audit coverage。

验收：

- team member role 控制 run/artifact/approval access。
- audit 为敏感事件记录 actor 和 workspace。

### Phase J8: Remote Runner 设计与 Spike

当前实现状态：J8A 已实现 configurable worker implementation spike：默认 `local-ts` 保持现有 local TS worker；`remote-http` 使用 JDK `HttpClient` 调用配置的 remote endpoint；run attempt 会记录 `REMOTE_RUNNER`；contract tests 证明 Java 仍只把嵌套 `agent-backend-response.v1` 映射进 run state，不解析 runtime-private `source`。J20A 后续补充可选 HMAC remote result envelope verification；signed envelope 使用 `signatureKind=hmac-sha256` 和 `signature=hmac-sha256:<base64>`，签名输入只包含 remote envelope identity 和 nested public backend response fields，并以 fixed field name + UTF-8 length-prefix 编码。J21A 后续补充 production profile guard：`prod` / `production` active profile 下 `remote-http.signature-secret` 不能为空。J8A/J20A/J21A 不包含 runner 注册、heartbeat、lease、artifact upload、remote cancellation、remote workspace mount、runner authorization、runner-scoped secret distribution、key rotation、KMS/secret manager 或 mTLS。

交付：

- runner protocol design。
- local runner-compatible contract tests。
- signed result envelope concept。

验收：

- Java service 可以通过 config 选择 worker implementation。
- existing local worker 仍通过同一组 contract tests。

### Phase J9: Provider Secret Policy

当前实现状态：J9A 已实现 provider runtime reference policy baseline。public run API 接受 `providerRuntimeRef`，拒绝 raw `providerRuntime` object 和 token value；Java 只向 worker 传安全 provider metadata 和 `apiKeyEnvName`。J23A 后续补充 provider runtime raw secret config guard：configured refs 会拒绝非空 `api-key`、`token` 或 `authorization`。J23B 后续补充 `api-key-env-name` format guard：只有 env-var-style identifier 会被转成 worker metadata。J24A 后续补充 provider credential metadata schema：DB 有 `provider_credentials` 元数据表和 `api_key_secret_ref`，但没有 raw secret value 列。J24B 后续补充 provider credential metadata repository：后端可按 team/workspace scope 读写 ACTIVE credential metadata。J24C 后续补充 provider credential service scope guard：内部 service 必须先通过 workspace editor guard，再使用 workspace 的 authoritative team/workspace scope 解析 credential metadata。J24D 后续补充 provider credential runtime descriptor：已授权 metadata 可转为 secret-safe descriptor，且 plain secret-looking ref/unsupported provider 会 fail closed。J24E 已补充 agent-run DB-backed credential ref wiring：`providerRuntimeRef = "credential.<credentialRef>"` 会解析 DB credential metadata，并仅把 `env://NAME` 映射成 worker `apiKeyEnvName`；非 env secret ref 会在 worker 执行前拒绝，避免把 `apiKeySecretRef` 或 raw secret 传入 worker。J25A 已补充 workspace-scoped provider credential public metadata API：owner 可以 upsert/list env-backed metadata，public response 只返回 credential ref、workspace scope、provider/model/baseUrl/status，不返回 env name 或 secret reference。J26A 已补充 workspace-scoped provider credential disable lifecycle guard：owner 可以禁用 metadata，disabled ref 不会被 run path 解析，disable audit 不包含 env name 或 secret reference。J9A/J23A/J23B/J24A/J24B/J24C/J24D/J24E/J25A/J26A 不包含 secret manager、真实 provider 调用、non-env secret injection、team-scoped credential API 或 remote runner secret distribution。

### Phase J10: Workspace Member Management

当前实现状态：J10A 已实现 public workspace member management baseline。owner 可以通过 `PUT /v1/workspaces/{workspaceId}/members/{userId}` 授予 viewer/editor；viewer 或更高角色可以通过 `GET /v1/workspaces/{workspaceId}/members` 读取成员列表；public API 不支持 owner grant，owner transfer 由 J15A 单独 API 处理。成员 grant 会写入 `WORKSPACE_MEMBER_GRANTED` audit metadata，JDBC/in-memory repository 共用成员列表 contract。J12A 后续补充了 owner-only 非 owner 成员移除。

### Phase J11: Audit Listing API

当前实现状态：J11A 已实现 owner-only audit listing API baseline。owner 可以通过 `GET /v1/workspaces/{workspaceId}/audit-events` 读取 workspace audit metadata；viewer 会得到 `WORKSPACE_FORBIDDEN`；missing workspace 保持 `WORKSPACE_NOT_FOUND`。J11A 不包含 audit export、signed audit record 或 public audit write API；J16A 后续补充 pagination/filtering baseline，J18A 后续补充 audit export baseline，J27A 后续补充 retention policy metadata baseline。

### Phase J12: Workspace Member Removal

当前实现状态：J12A 已实现 owner-only workspace member removal baseline。owner 可以通过 `DELETE /v1/workspaces/{workspaceId}/members/{userId}` 移除 viewer/editor 成员；viewer 删除会得到 `WORKSPACE_FORBIDDEN`；删除 owner 会得到 `VALIDATION_ERROR`；被移除成员随后失去 workspace 读取权限。J12A 复用 workspace member response，不暴露 internal path、provider payload 或 token，并写入 `WORKSPACE_MEMBER_REMOVED` audit metadata。J12A 不包含邀请、public user/team directory CRUD 或跨团队成员管理；J15A 后续补充 owner transfer。

### Phase J13: Current Team Discovery

当前实现状态：J13A 已实现 current team discovery baseline。`GET /v1/teams` 返回当前 principal 的单个 team：`teamId`、`name`、`status`。该 API 只解决 client 发现当前 team id 的最小需求，不实现用户目录、团队 CRUD、邀请或跨团队 discovery。J14A 后续补充了 backend-known team member listing。

### Phase J14: Team Member Listing

当前实现状态：J14A 已实现 backend-known team member listing baseline。`GET /v1/teams/{teamId}/members` 返回当前 team 的 backend-known members：`teamId`、`userId`、`role`。当前 principal 只能查询自己的 team；跨 team 查询返回 `TEAM_FORBIDDEN`。Team membership 独立于 workspace membership：删除 workspace 成员不会删除 team membership。J14A 不包含完整用户资料目录、团队 CRUD、邀请、跨团队 discovery 或生产 OIDC team role 同步。

### Phase J15: Workspace Owner Transfer

当前实现状态：J15A 已实现 workspace owner transfer baseline。owner 可以通过 `POST /v1/workspaces/{workspaceId}/owner-transfer` 将 workspace ownership 转移给同 workspace/team 的既有非 owner 成员；旧 owner 降为 `WORKSPACE_EDITOR`，新 owner 升为 `WORKSPACE_OWNER`。viewer 调用返回 `WORKSPACE_FORBIDDEN`，self-transfer、missing target 和 already-owner target 返回 `VALIDATION_ERROR`。J15A 复用 workspace member response，不暴露 internal path、provider payload 或 token，并写入 `WORKSPACE_OWNER_TRANSFERRED` audit metadata。J15A 不包含邀请、public user/team directory CRUD、team admin transfer、完整 OIDC/OAuth team role sync 或跨团队成员管理。

### Phase J16: Audit Pagination And Filtering

当前实现状态：J16A 已实现 audit pagination/filtering baseline。owner 可以通过 `GET /v1/workspaces/{workspaceId}/audit-events?limit=...&offset=...&eventType=...&runId=...` 对 workspace audit metadata 做有限分页和过滤；viewer 仍得到 `WORKSPACE_FORBIDDEN`，missing workspace 仍保持 `WORKSPACE_NOT_FOUND`。`limit` 范围为 1..100，`offset` 不能为负数；非法 query 返回 `VALIDATION_ERROR`。J16A 保持 `java-backend-api.v1` envelope 和 `data` 数组响应，不暴露 internal path、provider payload 或 token。J16A 不包含 audit export、signed audit record 或 public audit write API；J18A 后续补充 audit export baseline。

### Phase J17: Run Event SSE Streaming

当前实现状态：J17A 已实现 run event SSE streaming baseline，J22A 已实现 run event SSE replay cursor baseline。已授权客户端可以通过 `GET /v1/agent-runs/{runId}/events/stream` 接收同一 run 的 backend-owned durable lifecycle events；payload 与 `GET /v1/agent-runs/{runId}/events` 的 public response 字段一致，不解析 worker/runtime-private result shape。未授权请求返回 403，缺失 run 返回 404，且不会启动异步 stream。终态 event 会结束 stream；bounded window 到期也会关闭连接，但该 EOF 不代表 run 已终结。客户端重连时可传 `Last-Event-ID`，命中时 stream 跳过该 event 及其之前 events，未知 cursor 回退完整 replay；JDBC event list 通过 `event_sequence` 保持 append order，而不是依赖 `created_at` 和 random id。J17A/J22A 不包含 WebSocket、broker-backed fanout、多节点 stream routing、remote runner live event channel、生产级 heartbeat/backpressure 或 audit stream。

### Phase J18: Audit Export

当前实现状态：J18A 已实现 owner-only audit metadata NDJSON export baseline。owner 可以通过 `GET /v1/workspaces/{workspaceId}/audit-events/export?limit=...&offset=...&eventType=...&runId=...` 导出既有 workspace audit metadata；响应为 `application/x-ndjson`，每行使用与 audit list 相同的 public `AuditEventResponse` 字段。viewer 调用返回 `WORKSPACE_FORBIDDEN`，非法 query 返回 `VALIDATION_ERROR`，这些导出前错误仍使用 `java-backend-api.v1` JSON envelope。J18A 不包含 signed audit record、async export job、object storage、public audit write API 或 raw provider/runtime payload export。

### Phase J19: Audit Record Digest

当前实现状态：J19A 已实现 audit record digest baseline。`GET /v1/workspaces/{workspaceId}/audit-events` 和 `GET /v1/workspaces/{workspaceId}/audit-events/export` 都会返回 `recordDigest`，格式为 `sha256:<64 lowercase hex chars>`，digest 只基于 public audit metadata 字段计算。J27A 已补充 audit retention policy baseline：owner 可以读取 workspace 的 report-only retention metadata，默认由 `my-workflow.backend.audit.retention-days` 配置，响应明确 `destructivePurgeEnabled=false`，不触发删除、purge scheduler 或 repository mutation。J28A 已补充 persisted integrity baseline：新 audit event 在 repository append 时持久化 `recordDigest`、`previousRecordDigest`、`chainDigest`、`signatureKind` 和 `signatureValue`，其中 `signatureKind` 固定为 `sha256-chain-v1`，`signatureValue` 等于 `chainDigest`。J28A 不包含 key rotation、external KMS/secret manager、私钥签名、历史记录回填、retention execution 或 public audit write API。

### Phase J28: Audit Signed Record Integrity Baseline

当前实现状态：J28A 已实现本地 audit signed record integrity baseline。In-memory 和 JDBC audit repository 在 append 时生成并持久化 public metadata digest、workspace 内上一条 chain digest、当前 chain digest 以及 `sha256-chain-v1` signature metadata；audit list/export 通过 owner-only API 返回这些持久化字段。该阶段只证明 append-time persisted integrity metadata 和 hash-chain baseline，不提供 KMS-backed non-repudiation、多节点链路锁、历史数据补签或删除/retention 执行。

## 17. 验证策略

每个 implementation phase 在改代码前必须先定义 focused evidence。

推荐验证梯度：

- Unit：
  - status mapping；
  - path normalization；
  - artifact ref validation；
  - permission decision；
  - secret redaction helper。
- Integration：
  - HTTP envelope 和 error；
  - DB migration；
  - workspace registration；
  - run creation；
  - artifact read authorization。
- Contract：
  - Java `AgentWorker` adapter 消费 fixture `agent-backend-response.v1`；
  - Java 不要求 runtime-private field；
  - skeleton 存在后，用 TS worker smoke 证明真实 bridge。
- Security：
  - token pattern scan；
  - public JSON 不出现绝对路径；
  - path traversal denied；
  - raw provider ref 保持 redacted。

Real provider smoke 仍然 opt-in，必须遵守现有 token handling SOP。

## 18. 与现有文档的关系

本文不替代当前 TypeScript Agent SDK contract，而是依赖它。

Runtime 行为仍以这些文档为准：

- `docs/architecture/agent-sdk-mvp-phase1-spec.md`
- `docs/architecture/sdk-tool-surface-spec.md`
- `docs/architecture/backend-platform-roadmap-spec.md`
- `docs/architecture/runtime-phase-sop.md`

本文改变的是长期 backend 目标：

- `backend-platform-roadmap-spec.md` 对 thin Node/SDK backend smoke 仍然有参考价值。
- Java central backend 在 team service、DB、auth、multi-workspace、approval、artifact 和 audit 设计上应取代它作为长期方向。

## 19. 风险与取舍

- 早期引入 MySQL 会增加 setup 成本，但可以避免未来团队化时重写所有 run/workspace API。
- 保留 TS runtime 作为 worker 可以延续当前 agent 进展，但需要 process/HTTP bridge 和 contract tests。
- 服务端托管 workspace 简化团队权限和 audit，但需要严格的 path isolation 和 write lock。
- Remote runner 后置可以避免过早分布式复杂度，但 worker interface 一开始就必须干净。
- 避免 JPA 可以减少隐式持久化行为，但需要更多显式 SQL 和 repository code。
- Spring JDBC 让 Phase J1-J3 更简单；只有 typed query generation 的收益超过 build 复杂度时，才引入 jOOQ。
- Phase J3 引入 async 增加早期复杂度，但符合真实 agent execution 形态，避免先设计一个很快会废弃的同步 API。

## 20. 评审重点

需要重点 review 的决策：

- 后端定位：Java 是否只做中心化 control plane，TS runtime 是否继续作为 execution engine。
- Phase J1 范围：是否只交付 skeleton、health/readiness、API envelope、profile config、模块边界和测试 harness。
- 团队化底座：是否确认从第一天使用 `workspaceId`，不把 `workspaceRoot` 暴露为 public API。
- 存储策略：是否确认 MySQL-first，run metadata/approval/audit 进 DB，execution evidence 保留 artifact。
- 异步边界：是否确认 Phase J3 起使用 DB-backed async job，HTTP 不等待 worker 完成。
- Worker bridge：Phase J3 先用 local process 还是 local HTTP worker，需要在 implementation plan 前拍板。
- Approval/write 边界：candidate patch 是否始终先进入 approval，`wroteWorkspace=true` 是否必须有 SDK evidence 和 backend audit。
- 安全边界：provider token 是否严格禁止通过 API JSON 传入，artifact read 是否必须按 workspace/run 权限检查。
