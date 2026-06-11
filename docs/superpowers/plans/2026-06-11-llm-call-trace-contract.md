# LLM Call Trace Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a provider-neutral LLM call trace layer so future real provider nodes can record Claude Code, DeepSeek, Xiaomi MiMo, and OpenAI-compatible calls without binding resume logic to provider raw logs.

**Architecture:** Domain artifacts remain the recovery truth. A new trace module writes canonical JSONL events under `.agent-runs/<runId>/traces/`, while provider normalizers map raw provider shapes into this canonical schema. Raw provider envelopes are optional, redacted debug attachments only.

**Tech Stack:** TypeScript, Node.js filesystem, Vitest, existing `AgentRunsStore`, existing mock agents.

---

## File Structure

- Create: `src/domain/llm-trace/trace-event.ts`
  - Canonical event types and helpers for redacted previews.
- Create: `src/domain/llm-trace/trace-writer.ts`
  - Append-only JSONL writer using `AgentRunsStore`.
- Create: `src/domain/llm-trace/provider-normalizers.ts`
  - Pure functions for Claude Code, OpenAI-compatible, DeepSeek, and MiMo local raw shapes.
- Modify: `src/agents/work-item-agent.ts`
- Modify: `src/agents/mock-note-agent.ts`
  - Emit canonical trace events while producing the same `PatchBundle`.
- Create: `tests/unit/llm-trace-writer.test.ts`
  - Validates JSONL append behavior and redaction preview.
- Create: `tests/unit/provider-normalizers.test.ts`
  - Validates provider shape mapping without calling real providers.
- Create: `tests/integration/mock-agent-trace.test.ts`
  - Runs the workflow and asserts trace artifacts are written.
- Modify: `docs/reports/runtime-checkpoint-resume-delivery.md`
  - Add a note that provider-neutral traces are the next boundary before real provider smoke.

## Task 1: Canonical Trace Types And Writer

**Files:**
- Create: `src/domain/llm-trace/trace-event.ts`
- Create: `src/domain/llm-trace/trace-writer.ts`
- Test: `tests/unit/llm-trace-writer.test.ts`

- [ ] **Step 1: Write the failing writer test**

```ts
import { readFile } from "node:fs/promises";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { appendLlmTraceEvent, previewText } from "../../src/domain/llm-trace/trace-writer.js";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "llm-trace-writer-"));
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("llm trace writer", () => {
  it("appends canonical JSONL trace events", async () => {
    const store = new AgentRunsStore(tempRoot, "run-trace");
    await appendLlmTraceEvent(store, "work-a", {
      schemaVersion: "llm-trace.v1",
      type: "llm.call.started",
      eventId: "event-1",
      runId: "run-trace",
      workItemId: "work-a",
      agentNode: "note",
      providerCallId: "call-1",
      provider: "fake",
      model: "fake-note-model",
      timestamp: "2026-06-11T00:00:00.000Z",
      request: {
        messagesSha: "sha-messages",
        temperature: 0,
        thinkingEnabled: false
      }
    });

    const content = await readFile(path.join(tempRoot, ".agent-runs/run-trace/traces/work-a.jsonl"), "utf8");
    expect(content.trim().split("\n")).toHaveLength(1);
    expect(JSON.parse(content)).toMatchObject({
      schemaVersion: "llm-trace.v1",
      type: "llm.call.started",
      provider: "fake"
    });
  });

  it("creates bounded previews without storing full text by default", () => {
    expect(previewText("abcdefghijklmnopqrstuvwxyz", 8)).toBe("abcdefgh");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- tests/unit/llm-trace-writer.test.ts`

Expected: FAIL with missing `src/domain/llm-trace/trace-writer.js`.

- [ ] **Step 3: Add canonical event types**

Create `src/domain/llm-trace/trace-event.ts`:

```ts
export type LlmTraceProvider =
  | "anthropic"
  | "claude-code"
  | "openai-compatible"
  | "deepseek"
  | "mimo-api"
  | "mimo-vllm"
  | "mimo-sglang"
  | "fake";

export type LlmAgentNode = "note" | "topic-index" | "moc" | "quality-review";

export interface LlmTraceBase {
  schemaVersion: "llm-trace.v1";
  eventId: string;
  runId: string;
  workItemId: string;
  agentNode: LlmAgentNode;
  providerCallId: string;
  provider: LlmTraceProvider;
  model: string;
  timestamp: string;
}

export interface LlmCallStarted extends LlmTraceBase {
  type: "llm.call.started";
  request: {
    messagesSha: string;
    systemSha?: string;
    toolSchemaSha?: string;
    temperature?: number;
    maxTokens?: number;
    reasoningEffort?: "low" | "medium" | "high" | "max";
    thinkingEnabled?: boolean;
  };
}

export interface LlmCallCompleted extends LlmTraceBase {
  type: "llm.call.completed";
  finishReason: string | null;
  outputTextSha?: string;
  reasoningTextSha?: string;
  usage?: {
    inputTokens?: number;
    outputTokens?: number;
    reasoningTokens?: number;
    cacheReadTokens?: number;
    cacheWriteTokens?: number;
    totalTokens?: number;
    costUsd?: number;
  };
}

export interface LlmToolCall extends LlmTraceBase {
  type: "llm.tool.call";
  toolCallId: string;
  toolName: string;
  argumentsSha: string;
  argumentsPreview: string;
}

export interface LlmToolResult extends LlmTraceBase {
  type: "llm.tool.result";
  toolCallId: string;
  status: "ok" | "error" | "denied";
  resultSha: string;
  resultPreview: string;
}

export interface LlmStreamDelta extends LlmTraceBase {
  type: "llm.stream.delta";
  deltaKind: "text" | "reasoning" | "tool_input" | "unknown";
  textSha: string;
  charCount: number;
}

export interface LlmCallFailed extends LlmTraceBase {
  type: "llm.call.failed";
  errorClass: "timeout" | "rate_limit" | "auth" | "provider" | "network" | "schema" | "unknown";
  retryable: boolean;
  message: string;
}

export interface LlmCompaction extends LlmTraceBase {
  type: "llm.context.compacted";
  trigger: "manual" | "auto" | "provider";
  beforeTokens?: number;
  afterTokens?: number;
  summarySha: string;
}

export interface LlmProviderRawRef extends LlmTraceBase {
  type: "llm.provider.raw_ref";
  requestPath?: string;
  responsePath?: string;
  redaction: "required" | "applied" | "not-stored";
}

export type LlmTraceEvent =
  | LlmCallStarted
  | LlmCallCompleted
  | LlmToolCall
  | LlmToolResult
  | LlmStreamDelta
  | LlmCallFailed
  | LlmCompaction
  | LlmProviderRawRef;
```

- [ ] **Step 4: Add append-only writer**

Create `src/domain/llm-trace/trace-writer.ts`:

```ts
import { appendFile, mkdir } from "node:fs/promises";
import path from "node:path";
import type { AgentRunsStore } from "../../storage/agent-runs-store.js";
import { stableJson } from "../../storage/json-schema.js";
import type { LlmTraceEvent } from "./trace-event.js";

export function previewText(value: string, maxChars = 160): string {
  return value.slice(0, maxChars);
}

export async function appendLlmTraceEvent(
  store: AgentRunsStore,
  workItemId: string,
  event: LlmTraceEvent
): Promise<void> {
  const tracePath = store.artifactPath(`traces/${workItemId}.jsonl`);
  await mkdir(path.dirname(tracePath), { recursive: true });
  await appendFile(tracePath, `${stableJson(event)}\n`, "utf8");
}
```

- [ ] **Step 5: Run focused test**

Run: `npm test -- tests/unit/llm-trace-writer.test.ts`

Expected: PASS.

- [ ] **Step 6: Run typecheck**

Run: `npm run typecheck`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/domain/llm-trace/trace-event.ts src/domain/llm-trace/trace-writer.ts tests/unit/llm-trace-writer.test.ts
git commit -m "feat: add llm trace writer"
```

## Task 2: Provider Normalizers

**Files:**
- Create: `src/domain/llm-trace/provider-normalizers.ts`
- Test: `tests/unit/provider-normalizers.test.ts`

- [ ] **Step 1: Write failing normalizer tests**

```ts
import { describe, expect, it } from "vitest";
import {
  normalizeClaudeCodeResult,
  normalizeDeepSeekChatCompletion,
  normalizeMimoVllmOutput
} from "../../src/domain/llm-trace/provider-normalizers.js";

describe("provider normalizers", () => {
  it("maps Claude Code result message to canonical completion", () => {
    const event = normalizeClaudeCodeResult({
      runId: "run-a",
      workItemId: "work-a",
      providerCallId: "call-a",
      timestamp: "2026-06-11T00:00:00.000Z",
      model: "claude-sonnet-4-6",
      message: {
        type: "result",
        subtype: "success",
        result: "done",
        session_id: "session-a",
        usage: { input_tokens: 10, output_tokens: 5 },
        total_cost_usd: 0.01,
        stop_reason: "end_turn"
      }
    });

    expect(event).toMatchObject({
      type: "llm.call.completed",
      provider: "claude-code",
      finishReason: "end_turn",
      usage: { inputTokens: 10, outputTokens: 5, costUsd: 0.01 }
    });
  });

  it("maps DeepSeek reasoning and usage fields", () => {
    const event = normalizeDeepSeekChatCompletion({
      runId: "run-a",
      workItemId: "work-a",
      providerCallId: "call-b",
      timestamp: "2026-06-11T00:00:00.000Z",
      response: {
        model: "deepseek-v4-pro",
        choices: [
          {
            finish_reason: "stop",
            message: {
              content: "final",
              reasoning_content: "reasoning",
              role: "assistant"
            }
          }
        ],
        usage: {
          prompt_tokens: 11,
          completion_tokens: 7,
          total_tokens: 18,
          completion_tokens_details: { reasoning_tokens: 3 }
        }
      }
    });

    expect(event.provider).toBe("deepseek");
    expect(event.usage).toMatchObject({
      inputTokens: 11,
      outputTokens: 7,
      totalTokens: 18,
      reasoningTokens: 3
    });
    expect(event.reasoningTextSha).toBeDefined();
  });

  it("maps MiMo local vLLM generated text without tool calls", () => {
    const event = normalizeMimoVllmOutput({
      runId: "run-a",
      workItemId: "work-a",
      providerCallId: "call-c",
      timestamp: "2026-06-11T00:00:00.000Z",
      model: "XiaomiMiMo/MiMo-7B-RL-0530",
      output: {
        generated_text: "answer",
        finish_reason: "stop",
        prompt_token_ids: [1, 2],
        output_token_ids: [3, 4, 5]
      }
    });

    expect(event).toMatchObject({
      type: "llm.call.completed",
      provider: "mimo-vllm",
      model: "XiaomiMiMo/MiMo-7B-RL-0530",
      usage: { inputTokens: 2, outputTokens: 3, totalTokens: 5 }
    });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- tests/unit/provider-normalizers.test.ts`

Expected: FAIL with missing normalizer module.

- [ ] **Step 3: Implement pure normalizers**

Create `src/domain/llm-trace/provider-normalizers.ts`:

```ts
import { sha256 } from "../../storage/sha.js";
import type { LlmCallCompleted } from "./trace-event.js";

interface NormalizerBase {
  runId: string;
  workItemId: string;
  providerCallId: string;
  timestamp: string;
}

export function normalizeClaudeCodeResult(input: NormalizerBase & {
  model: string;
  message: {
    type: "result";
    subtype: string;
    result?: string;
    session_id?: string;
    usage?: { input_tokens?: number; output_tokens?: number };
    total_cost_usd?: number;
    stop_reason?: string | null;
  };
}): LlmCallCompleted {
  return {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${input.providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: "claude-code",
    model: input.model,
    timestamp: input.timestamp,
    finishReason: input.message.stop_reason ?? input.message.subtype,
    outputTextSha: input.message.result ? sha256(input.message.result) : undefined,
    usage: {
      inputTokens: input.message.usage?.input_tokens,
      outputTokens: input.message.usage?.output_tokens,
      costUsd: input.message.total_cost_usd
    }
  };
}

export function normalizeDeepSeekChatCompletion(input: NormalizerBase & {
  response: {
    model: string;
    choices: Array<{
      finish_reason: string | null;
      message: {
        content?: string | null;
        reasoning_content?: string | null;
        role: string;
      };
    }>;
    usage?: {
      prompt_tokens?: number;
      completion_tokens?: number;
      total_tokens?: number;
      completion_tokens_details?: { reasoning_tokens?: number };
    };
  };
}): LlmCallCompleted {
  const choice = input.response.choices[0];
  const output = choice?.message.content ?? undefined;
  const reasoning = choice?.message.reasoning_content ?? undefined;
  return {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${input.providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: "deepseek",
    model: input.response.model,
    timestamp: input.timestamp,
    finishReason: choice?.finish_reason ?? null,
    outputTextSha: output ? sha256(output) : undefined,
    reasoningTextSha: reasoning ? sha256(reasoning) : undefined,
    usage: {
      inputTokens: input.response.usage?.prompt_tokens,
      outputTokens: input.response.usage?.completion_tokens,
      totalTokens: input.response.usage?.total_tokens,
      reasoningTokens: input.response.usage?.completion_tokens_details?.reasoning_tokens
    }
  };
}

export function normalizeMimoVllmOutput(input: NormalizerBase & {
  model: string;
  output: {
    generated_text?: string;
    finish_reason?: string | null;
    prompt_token_ids?: number[];
    output_token_ids?: number[];
  };
}): LlmCallCompleted {
  const inputTokens = input.output.prompt_token_ids?.length;
  const outputTokens = input.output.output_token_ids?.length;
  return {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${input.providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItemId,
    agentNode: "note",
    providerCallId: input.providerCallId,
    provider: "mimo-vllm",
    model: input.model,
    timestamp: input.timestamp,
    finishReason: input.output.finish_reason ?? null,
    outputTextSha: input.output.generated_text ? sha256(input.output.generated_text) : undefined,
    usage: {
      inputTokens,
      outputTokens,
      totalTokens: inputTokens !== undefined && outputTokens !== undefined ? inputTokens + outputTokens : undefined
    }
  };
}
```

- [ ] **Step 4: Run focused test**

Run: `npm test -- tests/unit/provider-normalizers.test.ts`

Expected: PASS.

- [ ] **Step 5: Run typecheck**

Run: `npm run typecheck`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/domain/llm-trace/provider-normalizers.ts tests/unit/provider-normalizers.test.ts
git commit -m "feat: normalize provider trace events"
```

## Task 3: Mock Agent Trace Integration

**Files:**
- Modify: `src/agents/work-item-agent.ts`
- Modify: `src/agents/mock-note-agent.ts`
- Test: `tests/integration/mock-agent-trace.test.ts`

- [ ] **Step 1: Write failing integration test**

```ts
import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "mock-agent-trace-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("mock agent trace", () => {
  it("writes canonical llm trace events for note agent work items", async () => {
    await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-trace",
      autoApprove: true
    });

    const tracePath = path.join(
      tempRoot,
      ".agent-runs/run-trace/traces/rewrite-tools-skill-vs-cli-tool-决策.jsonl"
    );
    const lines = (await readFile(tracePath, "utf8")).trim().split("\n").map((line) => JSON.parse(line));

    expect(lines.map((line) => line.type)).toEqual(["llm.call.started", "llm.call.completed"]);
    expect(lines[0]).toMatchObject({ provider: "fake", schemaVersion: "llm-trace.v1" });
    expect(lines[1].usage).toMatchObject({ inputTokens: 1, outputTokens: 1, totalTokens: 2 });
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- tests/integration/mock-agent-trace.test.ts`

Expected: FAIL because trace file does not exist.

- [ ] **Step 3: Extend agent input with optional trace store**

Modify `src/agents/work-item-agent.ts`:

```ts
import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import type { WorkItem } from "../domain/planning/work-item.js";
import type { AgentRunsStore } from "../storage/agent-runs-store.js";

export interface WorkItemAgentInput {
  runId: string;
  workItem: WorkItem;
  sourceContent: string;
  store?: AgentRunsStore;
}

export type WorkItemAgent = (input: WorkItemAgentInput) => Promise<PatchBundle>;
```

- [ ] **Step 4: Add trace writes to mock note agent**

Replace `src/agents/mock-note-agent.ts` with:

```ts
import type { AgentRunsStore } from "../storage/agent-runs-store.js";
import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import { appendLlmTraceEvent } from "../domain/llm-trace/trace-writer.js";
import { sha256 } from "../storage/sha.js";
import type { WorkItemAgentInput } from "./work-item-agent.js";

async function writeFakeNoteTraceStarted(input: WorkItemAgentInput & { store: AgentRunsStore }): Promise<void> {
  const providerCallId = `${input.workItem.id}:fake-note`;
  const timestamp = "2026-06-11T00:00:00.000Z";

  await appendLlmTraceEvent(input.store, input.workItem.id, {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.started",
    eventId: `${providerCallId}:started`,
    runId: input.runId,
    workItemId: input.workItem.id,
    agentNode: "note",
    providerCallId,
    provider: "fake",
    model: "fake-note-model",
    timestamp,
    request: {
      messagesSha: sha256(input.sourceContent),
      temperature: 0,
      thinkingEnabled: false
    }
  });
}

async function writeFakeNoteTraceCompleted(input: WorkItemAgentInput & { store: AgentRunsStore; content: string }): Promise<void> {
  const providerCallId = `${input.workItem.id}:fake-note`;
  const timestamp = "2026-06-11T00:00:00.000Z";

  await appendLlmTraceEvent(input.store, input.workItem.id, {
    schemaVersion: "llm-trace.v1",
    type: "llm.call.completed",
    eventId: `${providerCallId}:completed`,
    runId: input.runId,
    workItemId: input.workItem.id,
    agentNode: "note",
    providerCallId,
    provider: "fake",
    model: "fake-note-model",
    timestamp,
    finishReason: "stop",
    outputTextSha: sha256(input.content),
    usage: {
      inputTokens: 1,
      outputTokens: 1,
      totalTokens: 2
    }
  });
}

export async function runMockNoteAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const sourcePath = input.workItem.sourcePaths[0];
  const sourceSha = input.workItem.baseShas[sourcePath] ?? "unknown-source-sha";
  const baseSha = input.workItem.baseShas[targetPath] ?? null;

  if (input.store) {
    await writeFakeNoteTraceStarted({ ...input, store: input.store });
  }

  const content = `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - ${sourcePath}
sourceShas:
  ${sourcePath}: ${sourceSha}
lastRunId: ${input.runId}
contentSha: pending
-->

## 摘要

这篇 note 沉淀 skill 与 CLI tool 的适用边界，帮助后续判断能力应进入流程指导还是确定性工具。

## 来源追踪

- ${sourcePath}

## 关键决策

- Skill 适合流程、判断标准、上下文组织和人机协作约束。
- CLI tool 适合可重复、确定性、可测试、可组合的机械动作。
- Agent loop 不能只依赖 prompt，关键写入和质量边界必须由确定性代码验证。

## 取舍

Skill 更容易表达意图，但不能替代验证。CLI 更适合进入 CI 和本地自动化，但不适合承载模糊判断。

## 相关链接

暂无相关链接。
`;
  const contentSha = sha256(content);
  const finalizedContent = content.replace("contentSha: pending", `contentSha: ${contentSha}`);

  if (input.store) {
    await writeFakeNoteTraceCompleted({ ...input, store: input.store, content: finalizedContent });
  }

  return {
    workItemId: input.workItem.id,
    status: "SUCCEEDED",
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: baseSha ? "MODIFIED" : "CREATED",
        baseSha,
        contentSha: sha256(finalizedContent),
        content: finalizedContent
      }
    ],
    eval: {
      rawFilesSeen: [sourcePath],
      rawMirrorConverted: input.workItem.type === "REWRITE_TOPIC_NOTE",
      placeholderIntroduced: false,
      wikilinksCreated: 0
    }
  };
}
```

- [ ] **Step 5: Pass trace context from execute phase**

Modify `src/runtime/langgraph/nodes/execute-phase-node.ts` at the `runMockNoteAgent` call:

```ts
const bundle = await runMockNoteAgent({
  runId: state.runId,
  store,
  workItem: noteItem,
  sourceContent
});
```

- [ ] **Step 6: Run focused test**

Run: `npm test -- tests/integration/mock-agent-trace.test.ts`

Expected: PASS.

- [ ] **Step 7: Run existing agent tests**

Run: `npm test -- tests/unit/mock-agents.test.ts tests/integration/langgraph-workflow.test.ts tests/integration/cli-smoke.test.ts`

Expected: PASS.

- [ ] **Step 8: Run typecheck**

Run: `npm run typecheck`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/agents/work-item-agent.ts src/agents/mock-note-agent.ts src/runtime/langgraph/nodes/execute-phase-node.ts tests/integration/mock-agent-trace.test.ts
git commit -m "feat: write mock agent llm traces"
```

## Task 4: Delivery Report And Final Verification

**Files:**
- Modify: `docs/reports/runtime-checkpoint-resume-delivery.md`

- [ ] **Step 1: Update delivery report**

Append this section to `docs/reports/runtime-checkpoint-resume-delivery.md`:

```md
## Next Runtime Boundary: LLM Trace

Provider-neutral LLM trace is now the next boundary before real provider smoke. The trace contract records canonical JSONL events and optional redacted raw provider envelopes, with explicit support for Claude Code Agent SDK, OpenAI-compatible APIs, DeepSeek reasoning/tool fields, and Xiaomi MiMo API or local vLLM/SGLang inference shapes.

Trace remains audit/debug/eval data only. Resume decisions continue to rely on `.agent-runs` artifacts, workspace current SHA, `PatchBundle` content SHA, and Validator results.
```

- [ ] **Step 2: Run full verification**

Run: `npm test`

Expected: PASS, with all unit and integration tests passing.

Run: `npm run typecheck`

Expected: PASS.

Run: `git diff --check`

Expected: no output and exit 0.

- [ ] **Step 3: Commit**

```bash
git add docs/reports/runtime-checkpoint-resume-delivery.md
git commit -m "docs: report llm trace boundary"
```

## Self-Review Checklist

- The canonical trace schema covers started, delta, tool call, tool result, completed, failed, compaction, and raw ref events.
- Provider normalizers are pure functions and do not call external APIs.
- DeepSeek-specific `reasoning_content` and `reasoning_tokens` are preserved as SHA/usage, not treated as visible answer text.
- MiMo support does not assume only one hosting mode; API and local vLLM/SGLang are separate provider values.
- Trace artifacts cannot decide resume or publish.
- No API key, token, cookie, Authorization header, or full prompt is stored by default.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-11-llm-call-trace-contract.md`.

Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. Inline Execution - execute tasks in this session using executing-plans, batch execution with checkpoints.
