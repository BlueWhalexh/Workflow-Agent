# Knowledge-scoped Open Agent Runtime Spec

> 状态：Phase 29 candidate spec。目标是实现一个 Claude Code 风格的通用 agent loop baseline，但能力范围收敛在 knowledge workspace 内：能规划、读取知识库上下文、产出 answer 或 draft artifact，并且不能直接 publish workspace writes。

## 1. Background

Phase 28 的 `Hybrid Agent Command Router` 解决了入口问题：固定 workflow、开放 agent task、写入确认被分成不同 lane。但开放请求仍然只返回 envelope，没有真正执行通用 agent loop。

用户期望的方向更接近 Claude Code：

- 不是把场景枚举成 intent；
- 而是让 agent 读上下文、调用工具、规划步骤、产出结果；
- 在知识库产品里，这个能力应受 workspace/methodology/tool policy 约束。

## 2. Goal

新增 `OpenAgentRuntime`：

```text
OpenAgentTaskEnvelope
  -> PLAN
  -> GATHER_CONTEXT
  -> PRODUCE_OUTPUT
  -> SELF_CHECK
  -> WRITE_ARTIFACT
```

第一版不接真实 LLM，不新增依赖。它使用 deterministic runtime 产出可验证 artifact，锁定 contract 和安全边界。

## 3. Non-Goals

本阶段不做：

- 不接真实 LLM classifier 或 real provider。
- 不让 open agent 直接写 `knowledge-base/`。
- 不执行 `patch.publish`。
- 不做向量检索或数据库。
- 不实现完整多轮对话记忆。
- 不替代固定 organize workflow。

## 4. Runtime API

新增：

```ts
interface RunOpenAgentTaskRequest {
  workspaceRoot: string;
  taskId?: string;
  methodologyId?: string;
  objective: string;
  risk: "READ_ONLY" | "DRAFT_ONLY";
  outputPolicy: "ANSWER_ONLY" | "DRAFT_ARTIFACT";
  allowedToolNames: string[];
  blockedToolNames: string[];
}

interface RunOpenAgentTaskResult {
  taskId: string;
  status: "SUCCEEDED" | "FAILED_POLICY";
  methodologyId: string;
  artifactRoot: string;
  artifactPath: string;
  answer?: string;
  draftArtifact?: {
    title: string;
    content: string;
  };
  report: OpenAgentRunReport;
}
```

`handleCommand` 对 `OPEN_AGENT_TASK` 默认执行 runtime，并返回 `openAgent` result。

## 5. Artifact Contract

Artifact path:

```text
.agent-runs/open-agent/<taskId>.json
```

Report shape:

```ts
interface OpenAgentRunReport {
  schemaVersion: "open-agent-runtime.v1";
  taskId: string;
  methodologyId: string;
  objective: string;
  risk: "READ_ONLY" | "DRAFT_ONLY";
  outputPolicy: "ANSWER_ONLY" | "DRAFT_ARTIFACT";
  steps: Array<{
    name: "PLAN" | "GATHER_CONTEXT" | "PRODUCE_OUTPUT" | "SELF_CHECK";
    status: "SUCCEEDED" | "FAILED";
    summary: string;
  }>;
  context: {
    rawFiles: string[];
    knowledgePages: string[];
    methodology: {
      id: string;
      version: string;
    };
  };
  toolPolicy: {
    allowedToolNames: string[];
    blockedToolNames: string[];
  };
  outputRef: {
    kind: "answer" | "draft";
    path: string;
  };
}
```

## 6. Context Gathering

第一版 deterministic context：

- scan workspace using existing `scanWorkspace`;
- include raw file paths;
- include knowledge-base page paths;
- include methodology id/version.

The runtime does not read arbitrary files outside the workspace contract.

## 7. Output Rules

### READ_ONLY / ANSWER_ONLY

Return an answer that includes:

- objective summary;
- methodology id;
- raw count;
- knowledge page count;
- top context paths.

### DRAFT_ONLY / DRAFT_ARTIFACT

Return draft artifact with:

- title derived from objective;
- content containing source context summary;
- explicit marker that it is a draft and not published.

Draft artifact is stored only under `.agent-runs/open-agent/`, not under `knowledge-base/`.

## 8. Safety Rules

- If `patch.publish` appears in `allowedToolNames`, runtime returns `FAILED_POLICY`.
- Any `WORKSPACE_WRITE` request must not enter open runtime directly.
- Open runtime cannot call `publishBundle`.
- Candidate patch proposal is future work; Phase 29 only proves answer/draft and write-blocking baseline.

## 9. Acceptance

- SDK exports `runOpenAgentTask` and related types.
- `handleCommand` for read-only open request returns `openAgent.answer` and writes `.agent-runs/open-agent/<taskId>.json`.
- `handleCommand` for draft request returns `openAgent.draftArtifact` and writes `.agent-runs/open-agent/<taskId>.json`.
- Artifact report includes plan/context/output/self-check steps.
- Artifact report includes raw files, knowledge pages, methodology, allowed tools, blocked tools.
- `patch.publish` in allowed tools causes `FAILED_POLICY`.
- Ambiguous write request still returns `CONFIRMATION_REQUIRED`, not open runtime.
- Full tests / typecheck / diff check pass.

## 10. Review Focus

- Does this move toward a general problem-solving agent rather than an intent table?
- Does the first runtime produce useful, inspectable artifacts?
- Are write paths still blocked unless routed through confirmation/fixed workflow?
- Can a future LLM runtime replace deterministic output without changing artifact contract?
