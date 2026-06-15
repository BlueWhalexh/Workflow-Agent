# Open Agent Provider-backed Synthesis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `OpenAgentGraph` use real provider synthesis for answer, draft, and candidate patch outputs while preserving schema validation, grounding, redaction, and no-publish safety.

**Architecture:** Extend `OpenAgentProvider` with `synthesize()`. `SynthesizeNode` calls provider synthesis when available, parses structured JSON, then deterministically wraps output into SDK result types. Fake/fixture runtimes keep deterministic synthesis. Real MiMo/DeepSeek graph runs must call plan, nextAction, and synthesize through the provider adapter.

**Tech Stack:** TypeScript, Vitest, existing OpenAI-compatible provider adapter, existing `.agent-runs/open-agent` artifacts, existing redaction helpers.

---

## File Structure

- Modify `src/runtime/open-agent/open-agent-state.ts`: add synthesis input/output types, synthesis report refs, and context digest.
- Modify `src/runtime/open-agent/open-agent-provider.ts`: add `synthesize()`, `parseOpenAgentSynthesisOutput`, prompts, provider call ids.
- Modify `src/runtime/open-agent/nodes/context-gather-node.ts`: store short context digest excerpts for synthesis.
- Modify `src/runtime/open-agent/nodes/synthesize-node.ts`: call provider-backed synthesis when available; preserve deterministic fallback.
- Modify `src/runtime/open-agent/nodes/self-check-node.ts`: validate provider-backed output.
- Modify `src/runtime/open-agent/open-agent-artifacts.ts`: include synthesis metadata in report.
- Modify `src/runtime/open-agent/open-agent-graph.ts`: pass provider into synthesize node and expose synthesis metadata.
- Modify `src/runtime/provider/open-agent-real-smoke.ts`: expect graph provider calls include synthesis.
- Test `tests/unit/open-agent-provider.test.ts`.
- Test `tests/unit/open-agent-graph-nodes.test.ts`.
- Test `tests/integration/open-agent-graph.test.ts`.
- Test `tests/unit/open-agent-real-smoke.test.ts`.
- Update `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Task 1: Synthesis Parser Tests

**Files:**
- Modify: `tests/unit/open-agent-provider.test.ts`
- Modify: `src/runtime/open-agent/open-agent-provider.ts`

- [ ] **Step 1: Add failing parser tests**

Add tests for:

```ts
parseOpenAgentSynthesisOutput({
  kind: "ANSWER",
  answer: "Answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
  groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
})
```

and fenced JSON:

```ts
"```json\n{\"kind\":\"DRAFT_ARTIFACT\",\"title\":\"AI Agent Questions\",\"content\":\"Draft only. Provider-generated draft content.\\n\\nSources:\\n- raw/agent/Agent Loop 失败复盘.md\",\"groundingRefs\":[\"raw/agent/Agent Loop 失败复盘.md\"]}\n```"
```

Expected red run:

```text
TypeError: parseOpenAgentSynthesisOutput is not a function
```

- [ ] **Step 2: Implement parser**

Add:

```ts
export type OpenAgentSynthesisOutput =
  | { kind: "ANSWER"; answer: string; groundingRefs: string[] }
  | { kind: "DRAFT_ARTIFACT"; title: string; content: string; groundingRefs: string[] }
  | { kind: "CANDIDATE_PATCH"; title: string; content: string; targetPath: string; groundingRefs: string[] };
```

Parser rules:

- parse object or string;
- strip ```json fences;
- require non-empty content;
- require non-empty `groundingRefs`;
- throw `OpenAgentProviderValidationError` on invalid shape.

- [ ] **Step 3: Run parser tests**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts
```

Expected: provider tests pass.

## Task 2: Provider Synthesize Adapter

**Files:**
- Modify: `src/runtime/open-agent/open-agent-provider.ts`
- Modify: `tests/unit/open-agent-provider.test.ts`

- [ ] **Step 1: Add failing adapter test**

Test `createOpenAiCompatibleOpenAgentProvider().synthesize()`:

- sends `/chat/completions`;
- call id is `open-agent-synthesize-1` when synthesize is first call on a fresh provider;
- response JSON is parsed into `ANSWER`;
- raw envelope callback is redacted.

Expected red run:

```text
TypeError: provider.synthesize is not a function
```

- [ ] **Step 2: Implement `synthesize()`**

Prompt must tell provider:

- return only JSON;
- answer must include sources;
- draft must include `Draft only`;
- candidate target must be under `knowledge-base/`;
- no publish action.

Provider call id kind should be `synthesize`.

- [ ] **Step 3: Run provider tests**

Expected: all provider tests pass and no test artifact contains API key.

## Task 3: Context Digest

**Files:**
- Modify: `src/runtime/open-agent/open-agent-state.ts`
- Modify: `src/runtime/open-agent/nodes/context-gather-node.ts`
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`

- [ ] **Step 1: Add failing test**

In graph node tests, assert result/report has context digest entries:

```ts
expect(result.contextDigest[0]).toMatchObject({
  path: expect.stringContaining("raw/"),
  excerpt: expect.any(String)
});
expect(result.contextDigest[0].excerpt.length).toBeLessThanOrEqual(800);
```

Expected red run: `contextDigest` is undefined.

- [ ] **Step 2: Implement digest**

Read gathered refs with `readWorkspaceFile`; store:

```ts
contextDigest: Array<{ path: string; excerpt: string }>
```

Excerpt max length: 800 chars.

- [ ] **Step 3: Run graph node tests**

Expected: digest exists and no paths outside `raw/`, `schema/`, `knowledge-base/` are read.

## Task 4: Provider-backed Synthesize Node

**Files:**
- Modify: `src/runtime/open-agent/nodes/synthesize-node.ts`
- Modify: `src/runtime/open-agent/open-agent-graph.ts`
- Modify: `src/runtime/open-agent/open-agent-state.ts`
- Modify: `tests/unit/open-agent-graph-nodes.test.ts`

- [ ] **Step 1: Add failing graph tests**

Add injected provider with `synthesize()` returning:

```ts
{
  kind: "ANSWER",
  answer: "Provider answer\n\nSources:\n- raw/agent/Agent Loop 失败复盘.md",
  groundingRefs: ["raw/agent/Agent Loop 失败复盘.md"]
}
```

Assert:

```ts
expect(result.answer).toContain("Provider answer");
expect(result.providerCalls).toBe(3);
expect(result.synthesis?.providerBacked).toBe(true);
```

Expected red run: answer still deterministic or providerCalls is 2.

- [ ] **Step 2: Modify graph runner**

Pass provider into `runSynthesizeNode(state, provider)`.

- [ ] **Step 3: Modify synthesize node**

If provider has `synthesize()`:

- call it;
- increment `providerCalls`;
- parse provider output;
- set answer/draft/candidate fields;
- set synthesis metadata.

If provider lacks `synthesize()`, keep deterministic synthesis and set `providerBacked: false`.

- [ ] **Step 4: Run graph node tests**

Expected: provider-backed answer path passes; existing deterministic tests still pass.

## Task 5: Draft And Candidate Safety

**Files:**
- Modify: `tests/integration/open-agent-graph.test.ts`
- Modify: `src/runtime/open-agent/nodes/self-check-node.ts`
- Modify: `src/runtime/open-agent/nodes/synthesize-node.ts`

- [ ] **Step 1: Add failing tests**

Draft test:

```ts
expect(result.draftArtifact?.content).toContain("Provider draft");
expect(result.draftArtifact?.content).toContain("Draft only");
expect(await fileExists(path.join(tempRoot, "knowledge-base/drafts/provider-draft.md"))).toBe(false);
```

Candidate test:

```ts
expect(result.candidatePatch?.publishable).toBe(false);
expect(result.candidatePatch?.files[0].content).toContain("Provider candidate");
expect(result.candidatePatch?.files[0].contentSha).toMatch(/^[a-f0-9]{64}$/);
expect(await fileExists(path.join(tempRoot, result.candidatePatch!.targetPaths[0]))).toBe(false);
```

Invalid target test:

```ts
targetPath: "raw/unsafe.md"
expect(result.status).toBe("FAILED_POLICY");
```

- [ ] **Step 2: Implement safety handling**

Rules:

- draft must contain `Draft only`;
- candidate target must start with `knowledge-base/`;
- candidate patch always uses deterministic `publishable: false`;
- content sha computed by deterministic code;
- no target file write.

- [ ] **Step 3: Run integration graph tests**

Expected: draft/candidate pass, invalid target fails safely.

## Task 6: Smoke Harness Update

**Files:**
- Modify: `tests/unit/open-agent-real-smoke.test.ts`
- Modify: `src/runtime/provider/open-agent-real-smoke.ts`

- [ ] **Step 1: Update tests**

Injected fetch should now return three responses:

1. plan JSON;
2. nextAction JSON;
3. synthesis JSON.

Assert:

```ts
expect(requests).toHaveLength(3);
expect(result.openAgentGraph?.providerCalls).toBe(3);
expect(result.openAgentGraph?.answer).toContain("Provider smoke answer");
expect(result.openAgentGraph?.rawProviderRefs).toHaveLength(3);
```

- [ ] **Step 2: Update smoke result expectations**

`runOpenAgentRealSmoke({ mode: "llm-graph" })` should pass only when graph status is `SUCCEEDED` and provider calls include synthesize.

- [ ] **Step 3: Run smoke unit test**

Expected: smoke test passes without real network.

## Task 7: Real MiMo Smoke

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Run real MiMo smoke**

Use temp fixture workspace and hidden stdin token:

```text
read -rs MIMO_API_KEY
export MIMO_API_KEY
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node --import tsx src/cli/open-agent-smoke.ts \
  --provider mimo-open-agent-smoke \
  --mode llm-graph \
  --workspace-root /private/tmp/<fixture> \
  --base-url https://token-plan-cn.xiaomimimo.com/v1 \
  --model mimo-v2.5 \
  --output-policy ANSWER_ONLY \
  --execute-real
```

Expected:

- status `PASSED`;
- graph status `SUCCEEDED`;
- `providerCalls >= 3`;
- answer contains provider-generated content and `Sources`;
- raw provider request artifacts contain `[REDACTED]`;
- token search under `.agent-runs/open-agent` has no matches;
- no `knowledge-base/drafts/mimo-open-agent-smoke.md`.

- [ ] **Step 2: If real smoke fails**

Use systematic debugging:

- read redacted response artifact;
- classify failure as schema drift, budget, provider HTTP, or self-check;
- add regression test before changing parser/prompt/self-check;
- rerun focused tests before retrying real smoke.

## Task 8: Full Verification

**Files:**
- Modify: `docs/reports/runtime-work-item-execution-resume-delivery.md`

- [ ] **Step 1: Run focused tests**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts tests/unit/open-agent-real-smoke.test.ts
```

- [ ] **Step 2: Run full tests**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/vitest run
```

- [ ] **Step 3: Run typecheck**

```text
/Users/didi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node node_modules/.bin/tsc --noEmit
```

- [ ] **Step 4: Run diff check**

```text
git diff --check
```

- [ ] **Step 5: Update delivery report**

Record:

- focused test count;
- full test count;
- typecheck;
- diff check;
- real MiMo smoke result;
- provider calls;
- redaction evidence;
- no workspace write evidence;
- remaining boundary.

## Completion Criteria

Phase 33 is complete only when provider-backed synthesis works for at least `ANSWER_ONLY`, tests cover draft/candidate safety, real MiMo smoke passes with synthesize call, and full verification passes.
