# Java Remote Runner Registry Lease Baseline

> 日期：2026-06-17
> 范围：J30A
> 状态：已归档

## 目标

为 Java 后端补一个 workspace-scoped remote runner registry / heartbeat / lease baseline，先证明控制面可以保存 runner metadata、记录在线状态并 claim exclusive lease。

## 边界

- 不实现真实远程任务派发。
- 不实现 artifact upload、remote cancellation、remote workspace mount 或 multi-node scheduler。
- 不实现 runner identity token、mTLS、runner auth handshake 或 key rotation。
- 不分发 provider secret、runner-scoped credential 或 J29A 的 raw secret injection。
- 不改变 `AgentWorkerRequest`、`agent-backend-response.v1` 或 existing remote HTTP worker result envelope。
- J30A 当前是 workspace-scoped，由 `WORKSPACE_OWNER` 管理并写 workspace audit；team-level runner directory 和 team-level audit 后置。

## 设计

1. 新增 `remote_runners` Flyway schema，按 `workspace_id + runner_ref` 唯一。
2. 新增 `RemoteRunnerRepository`，支持 save/list/heartbeat/claimLease。
3. 新增 `RemoteRunnerService`，复用 `WorkspaceService.requireWorkspaceRole(..., WORKSPACE_OWNER)`。
4. 新增 `RemoteRunnerController`：
   - `GET /v1/workspaces/{workspaceId}/remote-runners`
   - `PUT /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}`
   - `POST /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/heartbeat`
   - `POST /v1/workspaces/{workspaceId}/remote-runners/{runnerRef}/lease`
5. Endpoint URL 只接受 http/https，拒绝 userinfo credentials。
6. Public response 和 audit message 不包含 runner token、signature secret、provider token、Authorization material 或 raw secret alias。

## 验收

- RED：focused Java test 在现有代码上因缺 runner repository/record/status/controller 编译失败。
- GREEN：focused Java tests 覆盖 registry、heartbeat、lease、viewer forbidden、OpenAPI path、schema/repository。
- Full：`mvn -f backend/pom.xml test`。
- Static：diff check、conflict/trailing whitespace scan、token/redaction scan、unchecked plan scan。

## 归档证据

- RED：`mvn -f backend/pom.xml test -Dtest=RemoteRunnerControllerTest,RemoteRunnerRepositoryTest` 编译失败，缺 `RemoteRunnerRepository`、`RemoteRunnerRecord` 和 `RemoteRunnerStatus`。
- Focused GREEN：同一 focused 命令通过，5 个 Java tests passed。
- Full Java、static 和 token scan 证据见 `docs/reports/runtime-work-item-execution-resume-delivery.md` 的 J30A section。
