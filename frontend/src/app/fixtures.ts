import type { WorkbenchViewModel } from "./types.js";

export const workbenchFixture: WorkbenchViewModel = {
  workspaceName: "Agent Loop Core",
  breadcrumb: ["Agent Loop Core", "Agent Loop", "工作台前端控制面"],
  treeItems: [
    { id: "loop", icon: "▾", label: "Agent Loop", count: "24" },
    { id: "frontend", icon: "•", label: "工作台前端控制面", count: "打开", depth: true, active: true },
    { id: "approval", icon: "•", label: "候选补丁与审批边界", count: "6", depth: true },
    { id: "events", icon: "•", label: "运行事件与恢复", count: "9", depth: true },
    { id: "inventory", icon: "▸", label: "Workspace Inventory", count: "11" },
    { id: "backend", icon: "▸", label: "Java Backend", count: "37" },
    { id: "provider", icon: "▸", label: "Provider Runtime", count: "15" },
  ],
  recentItems: [
    { id: "design", icon: "•", label: "frontend-knowledge-workbench-design", count: "刚刚" },
    { id: "secret", icon: "•", label: "provider secret injection baseline", count: "今天" },
    { id: "audit", icon: "•", label: "audit retention policy", count: "今天" },
  ],
  graphSummary: "当前页连接 workspace、run、artifact、approval 和安全过滤规则。",
  article: {
    chips: [
      { label: "知识页", tone: "green" },
      { label: "workspace: agent-loop-core" },
      { label: "approval: pending", tone: "gold" },
      { label: "尚未写入", tone: "clay" },
    ],
    title: "工作台前端控制面",
    subtitle:
      "这是目标态预览：前端第一屏围绕知识页阅读、AI 协作和审批保护的候选变更展开。后端接口被组合成工作台语义，而不是直接暴露成管理菜单。",
    toc: ["摘要", "状态", "边界", "来源", "候选变更"],
    summary: [
      "工作台默认展示知识页，而不是后端控制台。左侧负责 workspace 和知识目录，中间负责正文阅读、来源和反链，右侧负责 AI 对话、运行状态、产物引用和审批草稿。",
      "前端需要通过 view model 隔离后端 API 形状。组件只消费过滤后的公开字段，不能直接渲染原始供应商负载、密钥引用、服务端路径或 runtime 私有字段。",
    ],
    boundaryNote:
      "`targetWorkspacePaths` 只表示 AI 建议写入目标，不表示已经写入。只有后端稳定信封或 artifact 明确返回写入成功时，UI 才能展示“已写入”。",
    metrics: [
      { label: "前端阶段", value: "Phase 1 预览" },
      { label: "数据来源", value: "fixture / artifact adapter" },
      { label: "写入状态", value: "等待审批" },
    ],
    boundaries: [
      "第一屏只做知识工作台，不做完整平台后台。",
      "供应商凭证、审计和成员管理进入次级页面。",
      "AI 输出先成为候选草稿，再进入 approval 链路。",
      "run、artifact、approval 状态必须分开展示，不能互相替代。",
      "组件只能消费 view model，不能依赖 Java controller 或 runtime 内部结构。",
    ],
    sources: [
      {
        title: "前端设计规格",
        path: "docs/superpowers/specs/2026-06-17-frontend-knowledge-workbench-design.md",
      },
      { title: "现有静态原型", path: "docs/prototypes/backend-console.html" },
      {
        title: "后端 API 控制面",
        path: "/v1/workspaces, /v1/agent-runs, /v1/artifacts, /v1/approvals",
      },
    ],
    patch: {
      label: "候选补丁摘要",
      status: "pending approval",
      summary: "建议把“安全字段过滤”和“targetWorkspacePaths 不是写入证据”加入页面摘要。",
      targetPath: "knowledge-base/topics/frontend/工作台前端控制面.md",
    },
    links: [
      { title: "候选补丁与审批边界", subtitle: "approval guard" },
      { title: "Open Agent Runtime", subtitle: "run event and artifact" },
      { title: "Provider Secret Policy", subtitle: "safety allowlist" },
    ],
  },
  assistant: {
    contextChips: ["当前页", "3 个来源", "1 个候选补丁", "禁止直接写入"],
    run: {
      title: "Run 正在等待审批",
      id: "run_2f91c0",
      progress: 72,
      events: [
        { time: "10:18:21", label: "context gathered from current page" },
        { time: "10:18:24", label: "candidate patch artifact created" },
        { time: "10:18:27", label: "approval request opened" },
      ],
    },
    messages: [
      { author: "你", kind: "user", text: "按当前设计，前端第一屏应该长什么样？" },
      {
        author: "助手",
        kind: "ai",
        text: "第一屏应该以知识页为中心：左侧目录，中间正文，右侧 AI。管理接口只作为次级入口，不抢占主流程。",
      },
      {
        author: "助手",
        kind: "ai",
        text: "我建议把候选补丁直接显示在右侧，并用明确状态标注“待审批，尚未写入”。这样不会把建议路径误认为真实写入。",
      },
      {
        author: "安全检查",
        kind: "ai",
        text: "当前预览不展示 token、secret ref、环境变量名、服务端绝对路径或原始供应商负载。",
      },
    ],
    approval: {
      title: "审批草稿",
      summary: "将安全过滤规则和写入边界补充到当前知识页摘要。",
      artifact: "patch_frontend_workbench_001",
      target: "knowledge-base/topics/frontend/工作台前端控制面.md",
      wroteWorkspace: false,
    },
    composerText: "把当前页整理成实现计划的 Phase 1",
  },
};
