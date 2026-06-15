# Change: Java 团队后端平台方向

> 状态：设计草案。本文记录从 thin SDK/backend smoke 规划扩展到 Java 中心化团队后端的方向变化。

## 背景

当前 Agent SDK 已经收敛出后端可消费的 normalized envelope：

- `runAgent(request)` 返回 `agent-sdk-run.v1`。
- `runBackendAgent(request)` 返回 `agent-backend-response.v1`。
- 后端调用方不需要理解 fixed workflow、deterministic open-agent 或 OpenAgentGraph runtime result shape。

现有 `backend-platform-roadmap-spec.md` 主要描述从 TypeScript SDK 进入 thin HTTP backend 的路线，适合验证最小后端边界。但产品方向已经进一步明确为：

- 一个中心服务管理多个用户；
- 一个中心服务管理多个 workspace；
- Phase 1 可以先本地运行；
- Phase 1 执行采用服务端托管 workspace；
- 架构保留 future remote runner 扩展位。

## 决策

新增 `docs/architecture/java-team-backend-platform-spec.md` 作为 Java 中心化团队后端的设计草案。

关键决策：

- Java 后端定位为中心化 control plane。
- TypeScript agent runtime 暂时保留为 execution engine。
- Java 后端只消费 `agent-backend-response.v1`。
- Public API 使用 `workspaceId`、`runId`、`artifactRef`，不暴露服务端绝对路径。
- 团队化 metadata 使用 MySQL 作为目标库。
- Phase 1 使用服务端托管 workspace。
- agent run 从 Phase J3 开始走 DB-backed async job，不设计同步等待 worker 完成的主路径。
- Remote runner 后置，但通过 `AgentWorker` interface、run attempt、worker kind 和 secret injection policy 保留扩展位。

## 影响

- Java 后端第一阶段不应只是本地 wrapper。
- DB、identity、workspace、permission、run、approval、artifact、audit 应作为一等模块设计。
- 本地开发 profile 可以简化认证和部署，但不改变 API 和数据模型。
- `backend-platform-roadmap-spec.md` 仍可指导 TypeScript thin backend smoke；Java 团队后端方向以新 spec 为准。
- 后续进入实现前，需要把 Java Phase J1 拆成 implementation plan，并按 TDD/verification SOP 执行。

## 影响文档

- `docs/architecture/java-team-backend-platform-spec.md`
- `docs/architecture/backend-platform-roadmap-spec.md`
- `docs/architecture/agent-sdk-mvp-phase1-spec.md`
- `docs/architecture/sdk-tool-surface-spec.md`
- `docs/architecture/runtime-phase-sop.md`

## 后续阶段

下一步不是直接实现业务功能，而是先 review Java 平台设计草案。设计确认后再进入：

```text
Phase J1: Java Platform Skeleton
```

Phase J1 应只交付 Spring Boot skeleton、health/readiness、API envelope、profile config、模块边界和 test harness，不接真实 agent execution。
