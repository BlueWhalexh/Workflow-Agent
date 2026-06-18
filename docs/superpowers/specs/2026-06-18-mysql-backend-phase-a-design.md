# MySQL 后端 Phase A 完整收口设计

> 状态：设计规格。本文定义 MySQL-backed 后端一期如何收口到前端和 runtime 可联调状态，不包含具体实现计划。

## 目标

Phase A 的目标是把当前 Java 后端从多个已实现 baseline 收口为一个 DB-only 后端一期。完成后，前端和 runtime 可以基于稳定的 Java API 进行真实联调，后端能力声明和验收报告不会把 mock、本地 resolver 或 baseline 能力包装成完整生产能力。

Phase A 使用 MySQL 作为唯一强依赖持久化设施。它不引入 MQ、Redis、MinIO 或 ES。

## 当前事实

当前 Java 后端已经具备这些基线能力：

- Spring Boot 后端进程。
- MySQL/Flyway `jdbc` profile。
- 用户、团队、workspace、workspace member、team invite、directory sync。
- DB-backed agent run、agent job、run attempt、run event。
- local TS worker bridge。
- remote HTTP worker、remote runner registry、heartbeat、lease、dispatch baseline。
- artifact registry 和 safe artifact read。
- approval request / decision。
- audit event、pagination、export、retention、digest/integrity baseline。
- provider credential metadata、env/file/http secret resolver、per-run secret injection baseline。
- OIDC/JWKS bearer verifier、OAuth token introspection、OAuth session cookie auth、CSRF guard 和 `GET /v1/session/csrf`。
- credentialed CORS 和 frontend integration contract。

因此 Phase A 不是从零开发后端，而是做能力声明纠偏、MySQL profile 收口、前端接入契约收口、runtime DB-backed smoke 和交付报告归档。

## 非目标

Phase A 不做：

- MQ、Redis、MinIO、ES。
- 完整 OAuth authorization-code login、callback、refresh-token rotation 或 IdP logout。
- 生产级 cross-site cookie policy 自动化配置。
- SCIM、LDAP 或 IdP directory connector/scheduler。
- Vendor KMS、Keychain adapter、secret rotation 或 public secret registration。
- Remote runner mTLS、runner identity token、runner-scoped secret distribution。
- Multi-node SSE fanout。
- 生产部署、监控、限流、账务、配额或灾备。
- 前端 UI 实现。

## 基础设施决策

### MySQL

MySQL 是 Phase A 唯一必须的外部基础设施。它保存：

- identity/team/workspace。
- run/job/attempt/event。
- artifact metadata。
- approval。
- audit。
- provider credential metadata。
- remote runner metadata。

MySQL 不保存 raw provider token。credential metadata 中只能保存 secret reference。

### MQ

Phase A 不引入 MQ。异步执行继续使用 DB-backed job table、status、lease 和 worker dispatch。重新评估条件：

- 单 DB job table 无法满足吞吐。
- 需要跨服务队列隔离。
- 需要独立消费者组、死信队列或复杂优先级。

### Redis

Phase A 不引入 Redis。重新评估条件：

- 需要集中 session store。
- 需要分布式 rate limit。
- 需要多节点 SSE fanout 或 pub/sub。
- 需要低延迟分布式锁。

### MinIO

Phase A 不引入 MinIO。artifact 继续以现有 artifact registry 和受控文件引用为主。重新评估条件：

- remote runner 上传大 artifact 成为主路径。
- 需要跨节点 artifact 存储。
- 需要对象存储生命周期和权限策略。

### ES

Phase A 不引入 ES。搜索不是当前后端一期主链路。重新评估条件：

- 知识页、论坛、博客或审计需要全文检索。
- DB 查询无法满足搜索体验。
- 需要独立搜索索引和重建流程。

## Phase A 能力定义

完成后可以声明：

- 后端具备 MySQL-backed 控制面。
- 前端可以使用 `java-backend-api.v1` envelope 接入身份、workspace、run、events、artifacts、approval、audit、provider credential metadata 和 auth diagnostics。
- runtime 可以通过 Java API 和 worker bridge 创建、执行、查询 agent run。
- remote runner baseline 支持 registered online runner 的显式 dispatch。
- CSRF 和 credentialed CORS 支持本地/浏览器 session-cookie 写请求前置保护。
- 后端只消费 `agent-backend-response.v1`，不解析 runtime-specific result shape。

完成后不能声明：

- 完整生产 OAuth 登录闭环。
- 完整生产 secret manager/KMS。
- 完整生产 remote runner platform。
- 多节点 stream fanout。
- 部署级真实浏览器 E2E。
- MQ/Redis/MinIO/ES 基础设施完成。

## API 和契约收口

`GET /v1/ops/integration-contract` 是前端和 runtime 的能力入口。Phase A 必须确保：

- capability 名称和实际实现一致。
- local/baseline 能力不能被命名成完整生产能力。
- `productionSecretManager` 只在语义上代表 HTTP external secret resolver baseline 时，必须改名或在 contract 中明确等级。
- `remoteRunnerArtifactUpload` 只有当 endpoint、auth、storage 和 tests 都真实存在时才能为 true。
- `oauthLoginSession` 必须保持 false，直到 login/callback/refresh/logout 全链路完成。
- `multiNodeStreamFanout` 必须保持 false。

前端必需 endpoint 至少包括：

- `GET /v1/me`
- `GET /v1/session/csrf`
- `GET /v1/teams`
- `GET /v1/workspaces`
- `POST /v1/workspaces/{workspaceId}/agent-runs`
- `GET /v1/agent-runs/{runId}`
- `GET /v1/agent-runs/{runId}/events`
- `GET /v1/agent-runs/{runId}/events/stream`
- `GET /v1/agent-runs/{runId}/artifacts`
- `GET /v1/artifacts/{artifactId}`
- `GET /v1/agent-runs/{runId}/approvals`
- `POST /v1/agent-runs/{runId}/approvals`
- `POST /v1/agent-runs/{runId}/cancel`
- `GET /v1/workspaces/{workspaceId}/audit-events`
- `GET /v1/workspaces/{workspaceId}/provider-credentials`
- `GET /v1/ops/auth-config`
- `GET /v1/ops/integration-contract`

Runtime 必需 endpoint 至少包括：

- `POST /v1/workspaces/{workspaceId}/agent-runs`
- `GET /v1/agent-runs/{runId}`
- `GET /v1/agent-runs/{runId}/events`
- `GET /v1/agent-runs/{runId}/artifacts`
- `GET /v1/artifacts/{artifactId}`
- `GET /v1/workspaces/{workspaceId}/remote-runners`
- `PUT /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}`
- `POST /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat`
- `POST /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease`

## 切片顺序

### Slice 1: Contract Truth Cleanup

目标：纠正 `IntegrationCapabilities` 和 auth/secret/runner 能力声明，确保 contract 不过度承诺。

验收：

- Focused test 证明 contract 与真实 Phase A 能力一致。
- Token scan 无 secret-shaped match。
- Delivery report 记录 RED/GREEN。

### Slice 2: MySQL Profile Readiness Smoke

目标：增加一个 MySQL/JDBC profile 下的后端核心链路 smoke，证明核心 API 在 MySQL 上可运行。

建议覆盖：

- workspace create/list。
- agent run create/poll。
- event list。
- artifact list 或 approval list 中至少一个跟随 run 产生。
- API response 不泄露 token、secret ref、absolute workspace path。

验收：

- Testcontainers MySQL focused test 通过。
- Java full tests 通过。
- Report 明确这是 MySQL integration，不是部署级 E2E。

### Slice 3: Frontend Handoff Contract

目标：为前端 API client 固化最小接入说明和运行前置条件。

内容：

- API base URL。
- credentialed request / CSRF flow。
- envelope unwrap。
- SSE EOF 语义。
- artifact/approval/run 状态映射。
- 禁止前端展示的字段。

验收：

- 文档和 contract test 对齐。
- 不新增生产依赖。

### Slice 4: Runtime DB-backed Handoff Smoke

目标：证明 runtime 侧通过 Java 后端和 MySQL-backed run/event/artifact/approval 链路可联调。

建议覆盖：

- local TS worker DB-backed run。
- remote runner dispatch baseline 的 contract-level test。
- provider credential ref 只通过 metadata/secret resolver，不进入 API response。

验收：

- Focused tests。
- Java full tests。
- TypeScript typecheck。
- Static scan。
- Token scan。
- Delivery report 明确不等于真实 provider E2E 或生产 remote runner。

## 测试策略

每个切片遵守 TDD：

1. 先写 RED test。
2. 确认失败原因是缺失目标行为或 contract drift。
3. 最小实现。
4. Focused GREEN。
5. 必要时跑 Java full。
6. 更新 report。
7. `git diff --check`。
8. merge marker / trailing whitespace scan。
9. strict token scan。

Full verification 收口至少包含：

- `/Applications/IntelliJ\ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn -f backend/pom.xml test`
- `npm run typecheck`
- `git diff --check`
- touched-file merge marker / trailing whitespace scan
- touched-file token scan

## 验收边界

Phase A 完成时可以说：

> 后端 DB-only 一期已可供前端和 runtime 开始真实联调。

Phase A 完成时不能说：

> 后端已经生产可用。

必须继续明确剩余风险：

- OAuth login/callback/refresh/logout 未完成。
- 生产 cookie policy 未完成。
- Vendor KMS/secret manager 未完成。
- Remote runner production identity、mTLS、runner-scoped secret distribution 未完成。
- Multi-node fanout 未完成。
- 部署级浏览器 E2E 未完成。

## Review 重点

- Capability 命名是否准确，是否过度承诺。
- MySQL smoke 是否真的使用 JDBC/Testcontainers，而不是 in-memory。
- API response 是否泄露 token、secret ref、env name 或绝对路径。
- Remote runner dispatch 是否仍保持 baseline 语义，没有冒充生产 runner 平台。
- Frontend contract 是否足够让 API client 开始联通。
- Delivery report 是否清楚区分 unit/fake/integration/E2E/real external call。
