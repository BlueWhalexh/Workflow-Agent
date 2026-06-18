# Local MySQL Browser E2E

> 状态：本地联调脚手架。本文说明如何在本机用 Docker MySQL、Java backend `jdbc` profile 和 Vite 前端复现用户侧 recent-run/artifact preview 链路。

## 目标

验证浏览器刷新后，用户侧工作台仍能从 MySQL-backed Java backend 读取最近 run，并打开该 run 的 artifact preview。

这不是生产部署验收，也不是真实外部 provider smoke。默认 worker 是本地 TS worker，默认 provider 行为仍按项目本地配置执行。

## 启动

1. 启动本地 MySQL：

```bash
docker compose -f docker-compose.local-mysql.yml up -d mysql
```

2. 启动 Java backend：

```bash
./scripts/dev-mysql-backend.sh
```

默认 backend 地址是 `http://127.0.0.1:18081`，默认 MySQL 端口是 `3307`。

3. 启动前端：

```bash
./scripts/dev-frontend.sh
```

默认前端地址是 `http://127.0.0.1:5173`，Vite 会把 `/v1`、`/health`、`/ready` 代理到 backend。

## 可覆盖的链路

1. 打开 `http://127.0.0.1:5173/`。
2. 确认页面显示 `后端已连接`。
3. 创建或选择 workspace。
4. 从右侧 AI 面板提交一次 run。
5. 等待 run 到达终态或等待审批态。
6. 刷新浏览器。
7. 在右侧 `最近运行` 中点击刚才的 run。
8. 确认页面展示 run status、durable events、artifact ref、`wroteWorkspace` 和 artifact preview。

## 自动化 smoke

可以先跑 frontend-origin/API 级本地 smoke，确认 Docker MySQL、Java backend、Vite proxy、workspace、run history 和 artifact readback 这条链路可重复：

```bash
./scripts/smoke-local-mysql-frontend-origin.sh
```

该脚本默认使用：

- MySQL: `127.0.0.1:3308`
- backend: `http://127.0.0.1:18081`
- frontend origin: `http://127.0.0.1:5173`
- logs: `/private/tmp/my-workflow-agent-local-mysql-smoke`

脚本会启动本地 MySQL、backend 和 frontend，通过前端 origin 调用公开 API，创建 workspace 和 deterministic run，轮询到终态，验证最近 run、events、artifact registry、artifact readback、`wroteWorkspace=false` 和 token-shaped scan。

该脚本不替代浏览器 DOM 检查；用户侧页面刷新和点击 `最近运行` 仍按上一节手动确认。

## 完整本地验收

Phase A 本地验收入口：

```bash
npm run verify:backend-phase-a-local
```

该命令会串行执行：

- 脚本语法检查
- Docker Compose 配置解析
- 后端 Phase A focused tests
- 前端 API/assistant/workbench focused tests
- TypeScript typecheck
- 前端 production build
- local MySQL frontend-origin smoke
- `git diff --check`
- token-shaped scan

如果只想跑静态和 focused tests，不启动本地 MySQL/backend/frontend smoke：

```bash
RUN_MYSQL_FRONTEND_SMOKE=0 npm run verify:backend-phase-a-local
```

## 可调环境变量

- `MY_WORKFLOW_MYSQL_PORT`：本地 MySQL 映射端口，默认 `3307`。
- `MY_WORKFLOW_MYSQL_DATABASE`：默认 `my_workflow_agent`。
- `MY_WORKFLOW_MYSQL_USER`：默认 `my_workflow_agent`。
- `MY_WORKFLOW_MYSQL_PASSWORD`：默认 `my_workflow_agent_dev`。
- `MY_WORKFLOW_BACKEND_PORT`：默认 `18081`。
- `MY_WORKFLOW_FRONTEND_PORT`：默认 `5173`。
- `MY_WORKFLOW_BACKEND_DATA_ROOT`：默认 `/private/tmp/my-workflow-agent-local-mysql-backend`。
- `MY_WORKFLOW_AGENT_WORKER_REPO_ROOT`：默认当前 repo root。

## 停止和清理

停止服务：

```bash
docker compose -f docker-compose.local-mysql.yml stop mysql
```

删除本地 MySQL 数据：

```bash
docker compose -f docker-compose.local-mysql.yml down -v
```

## 验收边界

- 该脚手架证明 local browser + Vite + Java backend + Docker MySQL 可以复现 frontend/backend 联通。
- 该脚手架不证明 OAuth login/callback、生产 secret manager、真实 provider、remote runner 多节点调度、MinIO artifact storage 或生产部署可用。
- 本地 MySQL 密码只用于开发默认值；不要把真实 token、API key、cookie 或私钥写入本文件、脚本参数、日志或 git 跟踪文件。
