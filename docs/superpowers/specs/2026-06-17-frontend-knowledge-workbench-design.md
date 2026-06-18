# 前端知识工作台设计

> 状态：前端设计规格。本文定义从静态 HTML 原型走向可实现前端的目标、边界、架构和验收，不包含实现计划。

## 目标

将现有静态原型 `docs/prototypes/backend-console.html` 演进为用户侧知识工作台前端。第一屏服务知识阅读、AI 协作和审批保护的候选变更，而不是展示后端 API 或管理台能力。

完成本设计后，后续实现应能按阶段交付：

- 左侧工作区知识导航。
- 中间知识页阅读和来源展示。
- 右侧 AI 助手、运行状态和候选变更草稿。
- 基于 approval 的受保护写入确认。
- 管理能力作为次级入口，不干扰第一屏。

## 非目标

本阶段不设计完整平台后台：

- 不做用户、组织、权限 CRUD 管理台。
- 不做凭证管理主流程，只保留管理员次级入口。
- 不做博客发布、讨论区或深度富文本编辑器。
- 不把 Java 后端所有接口一比一暴露成菜单。
- 不把前端设计绑定到具体 LLM provider 或运行时 SDK 内部结构。

## 设计原则

1. 知识工作台优先：默认界面像 Obsidian / LLMWiki 式工作区，而不是 API 控制台。
2. 正文优先：中间栏保持阅读宽度，目录、来源和 AI 辅助信息不挤压正文。
3. AI 建议可审：AI 生成的写入建议必须先成为候选草稿，再进入审批链路。
4. 后端契约隔离：前端消费稳定 API 和 adapter 后的视图模型，不依赖 runtime 内部字段。
5. 安全 allowlist：公开 UI 只渲染明确允许的字段，禁止按原始响应全量展开。
6. 可验证切片：每个阶段都必须有可运行 UI 或 agent-readable 验收输出。

## 推荐技术栈

前端使用独立目录：

```text
frontend/
```

推荐栈：

- React：组件化知识工作台和交互面板。
- Vite：轻量本地开发和构建。
- TypeScript：类型化 API client、view model 和安全过滤。
- CSS Modules 或普通 CSS 变量：先沿用静态原型视觉语言，避免过早引入重型设计系统。
- TanStack Query 可作为第二阶段选择：当真实 API 接入、缓存和轮询复杂度上升后再引入。

第一阶段可以先用轻量 `fetch` wrapper 和本地 fixture，避免在设计落地前引入过多依赖。

## 信息架构

第一屏采用三栏工作台：

```text
┌───────────────┬──────────────────────────┬──────────────────┐
│ 知识导航       │ 知识页阅读                │ AI 助手           │
│ workspace      │ article                  │ copilot           │
│ tree/search    │ sources/backlinks         │ context/chat/run  │
│ recent/graph   │ candidate context         │ patch/approval    │
└───────────────┴──────────────────────────┴──────────────────┘
```

左侧：

- 当前 workspace。
- 搜索入口。
- 知识目录树。
- 最近变更。
- 主题图谱入口。
- 管理次级入口。

中间：

- 面包屑和页面状态。
- 知识页标题、摘要、正文。
- 顶部横向本页目录。
- 来源引用。
- 关联页面和待补链接。
- 候选变更上下文提示。

右侧：

- 当前页上下文 chips。
- AI 对话。
- run 状态和事件摘要。
- 来源命中。
- 候选 patch 草稿。
- approval 决策入口。

## 前端模块边界

建议文件结构：

```text
frontend/src/
  app/
    App.tsx
    routes.tsx
  features/
    workspace/
    knowledge/
    assistant/
    runs/
    artifacts/
    approvals/
  shared/
    api/
    components/
    layout/
    safety/
    types/
```

模块职责：

- `workspace`：加载当前用户可见 workspace，维护当前 workspace selection。
- `knowledge`：目录树、知识页阅读模型、来源、反链和页面状态。
- `assistant`：右侧 AI 面板、用户输入、回答展示和上下文绑定。
- `runs`：agent run 创建、查询、取消、事件轮询或 SSE。
- `artifacts`：报告、trace、candidate patch 和 artifact 引用展示。
- `approvals`：审批请求列表、批准、拒绝、状态同步。
- `shared/api`：`ApiEnvelope<T>` unwrap、错误标准化、请求封装。
- `shared/safety`：公开字段 allowlist、敏感字段过滤和 UI 渲染守卫。

## 后端 API 映射

前端不直接按接口分页面，而是把后端 API 组合成知识工作台语义。

第一阶段 API 顺序：

1. 健康检查：`GET /health`、`GET /ready`。
2. 身份和工作区：`GET /v1/me`、`GET /v1/workspaces`、`GET /v1/workspaces/{workspaceId}`。
3. 成员只作次级信息：`GET /v1/workspaces/{workspaceId}/members`。
4. 运行：`POST /v1/workspaces/{workspaceId}/agent-runs`、`GET /v1/workspaces/{workspaceId}/agent-runs`、`GET /v1/agent-runs/{runId}`、`POST /v1/agent-runs/{runId}/cancel`。
5. 事件：`GET /v1/agent-runs/{runId}/events`，先轮询，SSE 作为后续增强。
6. 产物：`GET /v1/agent-runs/{runId}/artifacts`、`GET /v1/artifacts/{artifactId}`。
7. 审批：`GET /v1/agent-runs/{runId}/approvals`、approval decision endpoint。
8. 审计和凭证：仅管理次级页使用，不进入第一屏主导航。

需要补齐或明确的前端契约：

- 知识页正文读取来源：可以先由 artifact 或 fixture 供给；生产版需要稳定的 workspace content read API 或 BFF adapter。
- 目录树来源：可以先根据 workspace artifact 或本地 fixture 生成；生产版需要明确目录、页面 metadata 和路径安全规则。
- SSE 事件契约：若后端 SSE 已稳定，替换轮询；否则保留轮询作为最小可验证方案。

## 视图模型

前端应把后端响应转换成面向 UI 的 view model。

```ts
type WorkspaceSummary = {
  id: string;
  name: string;
  defaultBranch: string;
  status: "ACTIVE" | "ARCHIVED" | string;
};

type KnowledgePageView = {
  workspaceId: string;
  path: string;
  title: string;
  summary: string;
  bodyMarkdown: string;
  sources: SourceRefView[];
  backlinks: PageLinkView[];
  pendingPatch?: CandidatePatchView;
};

type AssistantRunView = {
  runId: string;
  status: "QUEUED" | "RUNNING" | "WAITING_APPROVAL" | "SUCCEEDED" | "FAILED" | "CANCELLED" | string;
  displayText: string;
  events: RunEventView[];
  artifacts: ArtifactRefView[];
};

type CandidatePatchView = {
  artifactId: string;
  targetWorkspacePaths: string[];
  wroteWorkspace: boolean;
  approvalStatus: "NONE" | "PENDING" | "APPROVED" | "REJECTED";
};
```

`targetWorkspacePaths` 只能表示建议写入目标。只有后端稳定信封或 artifact 明确返回 `wroteWorkspace: true` 时，UI 才能展示真实写入成功。

## 安全与字段过滤

前端禁止展示、缓存到 localStorage 或写入日志：

- 原始 provider token。
- `apiKeySecretRef`。
- 公开凭证响应里的环境变量名。
- Authorization header、cookie。
- 服务端文件系统绝对路径。
- worker/runtime 私有 `source` 字段。
- 原始 provider payload。

前端允许展示：

- workspace id、名称、状态、默认分支。
- 知识页相对路径和公开页面 metadata。
- run id、run 状态、公开事件摘要。
- artifact id、artifact 类型、公开报告引用。
- approval 状态和候选 `targetWorkspacePaths`。
- 来源摘要、反向链接、审计证据入口。

实现时必须在 `shared/safety` 放置字段 allowlist。组件只能消费过滤后的 view model，不直接渲染原始 API response。

## 交互流程

### 阅读知识页

1. 用户进入工作台。
2. 前端加载当前身份和可见 workspace。
3. 用户选择 workspace 和目录节点。
4. 中间区域展示知识页正文、来源和关联页面。
5. 右侧 AI 面板继承当前页路径、标题、来源和 workspace 上下文。

### AI 问答

1. 用户在右侧输入问题。
2. 前端创建 agent run 或复用当前页上下文创建任务。
3. 右侧展示 queued/running 状态。
4. 前端轮询 events 或订阅 SSE。
5. AI 输出以回答、来源命中、artifact 或 candidate patch 形式展示。

### 候选变更审批

1. AI 生成候选 patch。
2. UI 展示 patch 摘要和 `targetWorkspacePaths`。
3. UI 明确标注“待审批，尚未写入”。
4. 用户进入 approval decision。
5. approval 完成后，UI 根据后端状态刷新 run、artifact 和写入标记。

## 错误和空状态

必须覆盖：

- 未选择 workspace：显示工作区选择空状态。
- workspace 加载失败：展示可重试错误，不暴露底层 stack trace。
- 知识页缺失：展示路径和可恢复动作。
- run 失败：展示公开错误码和可读消息，保留 artifact/report 入口。
- approval 不存在：提示当前 run 没有待审批草稿。
- 权限不足：展示只读或无权限状态，不暴露内部权限规则。
- 后端不可用：顶部状态提示 degraded，不阻塞静态阅读 fixture。

## 响应式策略

桌面优先保证三栏：

- 左栏固定窄宽，支持折叠。
- 中间栏占主要宽度，正文最大宽度受控。
- 右栏固定辅助宽度，支持抽屉化。

窄屏：

- 左侧目录变为抽屉。
- 右侧 AI 面板变为底部或全屏面板。
- 中间正文保持单栏阅读。
- 顶部保留 workspace、页面标题和 AI 入口。

## 阶段验收

### Phase 1：静态原型组件化

验收：

- React/Vite 前端能启动。
- 三栏布局和现有视觉语言保持一致。
- 页面内容来自本地 fixture。
- 无真实 API 依赖。

### Phase 2：类型化 API Client

验收：

- `ApiEnvelope<T>` unwrap 可测试。
- API 错误标准化可测试。
- 安全过滤对禁止字段有单元测试。

### Phase 3：Workspace 和知识阅读

验收：

- 能加载 `/v1/me` 和 `/v1/workspaces`。
- 能选择 workspace。
- 能展示知识页阅读视图。
- 正文来源可先使用 fixture 或 artifact adapter。

### Phase 4：AI Run 面板

验收：

- 能创建 run。
- 能展示 run 状态。
- 能轮询或订阅事件。
- run 失败、取消和等待审批都有明确 UI。

### Phase 5：Artifact 和 Approval

验收：

- 能展示 candidate patch artifact。
- 能展示 approval pending/approved/rejected。
- UI 不把 `targetWorkspacePaths` 误表达为真实写入。
- 审批后能刷新 run 和 artifact 状态。

## 验证策略

前端实现阶段应至少包含：

- unit/mock：API unwrap、view model adapter、安全字段过滤。
- component/mock：三栏布局、空状态、错误状态、审批状态。
- fake integration：fixture 驱动的 workspace -> page -> run -> artifact -> approval 链路。
- local backend integration：后端本地启动后跑 workspace、run、events、approval 的最小链路。
- visual smoke：桌面和窄屏截图检查，不允许文本重叠、正文被挤压或 AI 面板遮挡主内容。

不得把 mock/fake 测试描述成真实 provider 链路。真实 provider 或真实 worker 链路必须单独标注。

## 评审重点

- 正确性：run、artifact、approval 和写入状态是否一致。
- 边界：前端是否只消费 view model，不直接依赖 runtime 内部结构。
- 安全性：secret、环境变量名、server path 和 provider payload 是否被过滤。
- 可用性：首屏是否像知识工作台，而不是后端管理台。
- 响应式：窄屏是否仍可阅读、提问和处理审批。
- 验证：是否区分 mock/fake/local backend/real external call。

## 后续实现入口

用户确认本设计后，再创建独立 implementation plan。实现计划应从 Phase 1 开始，保持每个 phase 可运行、可测试、可回退。
