# Open Agent Provider-backed Synthesis Spec

> 状态：implemented；Phase 38 source materialization hardening implemented。目标是把 `OpenAgentGraph` 的最终 answer / draft / candidate patch content 从 deterministic synthesis 推进到 provider-backed synthesis，同时保留 schema validation、grounding self-check、no-publish boundary 和 fixed workflow handoff。

## 1. Current Truth

Phase 32 已完成：

- `OpenAgentGraph` 支持 provider-backed `plan()` 和 `nextAction()`；
- `mimo-real` / `deepseek-real` 可以通过 OpenAI-compatible adapter 进入 graph；
- graph-owned raw provider refs 会写入 `.agent-runs/open-agent/raw-provider/<taskId>/<providerCallId>/request.json` 和 `response.json`；
- MiMo provider-backed graph smoke 已通过：
  - realExternalCall: `true`；
  - providerCalls: `2`；
  - raw request `Authorization` redacted；
  - no workspace write。

当前缺口：

- 最终 `answer` 仍由 deterministic `synthesize-node.ts` 基于 grounding refs 生成；
- `draftArtifact` / `candidatePatch.content` 也仍是模板化输出；
- 因此 graph 已经由 provider 做 plan/action，但还不能证明真实模型能生成最终知识任务产物。

## 2. Goal

新增 provider-backed synthesis：

```text
OpenAgentGraph
  -> provider.plan()
  -> context gather
  -> provider.nextAction()
  -> provider.synthesize()
  -> schema parse
  -> self-check
  -> artifact writer
```

支持三种 output policy：

- `ANSWER_ONLY`
- `DRAFT_ARTIFACT`
- `CANDIDATE_PATCH`

Provider synthesis 输出必须经过结构化解析和 self-check，不允许直接写 workspace。

## 3. Product Scenarios

### Scenario A: Grounded Answer

用户问：

```text
根据知识库总结为什么 fixed workflow 和 open agent 要分层。
```

期望：

- provider synthesis 生成 answer；
- answer 带 `Sources`；
- grounding refs 非空；
- artifact 记录 provider call；
- workspace 不变。

### Scenario B: Draft Artifact

用户说：

```text
根据知识库生成一份 AI agent 八股文问题清单草稿。
```

期望：

- provider synthesis 生成 `{ title, content }`；
- content 带 `Draft only` marker；
- content 带 sources；
- draft 只存在 graph result/report，不写 `knowledge-base/`。

### Scenario C: Candidate Patch Proposal

用户说：

```text
准备一份可以落库的候选 AI agent 问题清单，但不要发布。
```

期望：

- provider synthesis 生成 candidate content；
- runtime 包装为 `CandidatePatchProposal`；
- `publishable: false`；
- target path under `knowledge-base/drafts/<taskId>.md`；
- content sha 由 deterministic code 计算；
- no workspace write。

## 4. Non-goals

- 不让 provider 直接生成 publishable `PatchBundle`。
- 不让 provider 调用 `patch.publish`。
- 不改变 `PatchBundle -> MergeGuard -> Validator -> Publisher` 的固定写入链路。
- 不引入 vector database、long-term memory、streaming 或前端确认 UI。
- 不把 provider raw response 内联到 graph report。

## 5. Provider Contract

扩展 `OpenAgentProvider`：

```ts
interface OpenAgentProvider {
  plan(input: { objective: string; outputPolicy: OpenAgentOutputPolicy }): Promise<OpenAgentPlan | string>;
  nextAction?(input: { iteration: number; plan: OpenAgentPlan; groundingRefs: string[] }): Promise<OpenAgentNextAction | string>;
  synthesize?(input: OpenAgentSynthesisInput): Promise<OpenAgentSynthesisOutput | string>;
}
```

### Input

```ts
interface OpenAgentSynthesisInput {
  objective: string;
  outputPolicy: "ANSWER_ONLY" | "DRAFT_ARTIFACT" | "CANDIDATE_PATCH";
  methodologyId: string;
  groundingRefs: string[];
  contextDigest: Array<{
    path: string;
    excerpt: string;
  }>;
}
```

### Output

For `ANSWER_ONLY`:

```json
{
  "kind": "ANSWER",
  "answer": "string",
  "groundingRefs": ["raw/agent/Agent Loop 失败复盘.md"]
}
```

For `DRAFT_ARTIFACT`:

```json
{
  "kind": "DRAFT_ARTIFACT",
  "title": "string",
  "content": "string",
  "groundingRefs": ["raw/agent/Agent Loop 失败复盘.md"]
}
```

For `CANDIDATE_PATCH`:

```json
{
  "kind": "CANDIDATE_PATCH",
  "title": "string",
  "content": "string",
  "targetPath": "knowledge-base/drafts/<taskId>.md",
  "groundingRefs": ["raw/agent/Agent Loop 失败复盘.md"]
}
```

Parser may accept fenced JSON. Invalid JSON returns `FAILED_VALIDATION`.

## 6. Runtime Rules

Hard blockers:

- synthesis output kind does not match requested `outputPolicy`；
- `groundingRefs` is empty；
- synthesis references paths outside gathered refs；
- `CANDIDATE_PATCH.targetPath` is outside `knowledge-base/`；
- draft content misses draft marker；
- answer/draft/candidate content is empty；
- provider HTTP/auth failure。

Fallback:

- fake / fixture provider without `synthesize()` keeps deterministic synthesis；
- real provider must use provider-backed synthesis by default；
- explicit injected test provider can omit `synthesize()` only when tests target earlier nodes。

### Phase 38 Source Materialization

真实 provider 可以用编号引用（例如 `[1]`）表达 citation，同时在结构化 `groundingRefs` 字段返回 exact path refs。Runtime 不能依赖 prompt 要求模型把 path 字符串逐字写入正文；最终 artifact 的可审计性必须由 deterministic code 保证。

规则：

- parser 继续要求 provider 返回非空 `groundingRefs`；
- self-check 继续要求 final content 中包含 exact grounding ref path；
- provider-backed synthesis 在进入 final answer / draft / candidate content 前，必须 deterministic append missing source refs；
- 如果 provider 已经包含 exact refs，不重复追加；
- draft 仍必须包含 `Draft only` marker；
- candidate patch target path、`publishable: false`、content sha 和 fixed workflow handoff 仍由 runtime deterministic 包装。

## 7. Artifact Contract

Graph report adds:

```ts
{
  synthesis: {
    providerBacked: boolean;
    providerCallId?: string;
    outputKind: "ANSWER" | "DRAFT_ARTIFACT" | "CANDIDATE_PATCH";
    groundingRefs: string[];
  }
}
```

Raw refs:

```text
.agent-runs/open-agent/raw-provider/<taskId>/open-agent-synthesize-<n>/request.json
.agent-runs/open-agent/raw-provider/<taskId>/open-agent-synthesize-<n>/response.json
```

The report records paths and summaries, not raw payload.

## 8. Real Smoke

MiMo `llm-graph` smoke should prove:

- graph provider calls include plan, nextAction, synthesize；
- `providerCalls >= 3`；
- status `PASSED`；
- graph status `SUCCEEDED`；
- answer contains model-generated text and sources；
- numbered citation provider output is normalized into exact source refs before self-check；
- raw synthesize request is redacted；
- no workspace target is written。

## 9. Acceptance

- Unit tests cover `parseOpenAgentSynthesisOutput` strict/fenced JSON and invalid JSON。
- Unit tests cover answer/draft/candidate synthesis contracts。
- Unit tests cover source materialization for answer, draft, and candidate provider outputs that only use numbered citations。
- Integration test covers provider-backed graph answer with injected fetch and three provider calls。
- Integration test covers candidate patch target safety and no workspace write。
- Smoke test covers MiMo `--mode llm-graph --output-policy ANSWER_ONLY` with provider-backed synthesis。
- Full tests/typecheck/diff pass。

## 10. Review Focus

- Provider-backed synthesis must not bypass self-check。
- Candidate patch content may be provider-generated, but target path, content sha, `publishable: false`, and handoff must be deterministic。
- Raw provider payload must stay redacted and out of graph report。
- Deterministic fallback must remain available for fake tests。
