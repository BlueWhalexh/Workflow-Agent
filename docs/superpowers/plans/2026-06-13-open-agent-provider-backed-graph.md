# Open Agent Provider-backed Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire real OpenAI-compatible providers into `OpenAgentGraph` plan/action execution while preserving no-publish safety and redacted artifacts.

**Architecture:** Add a provider adapter in `src/runtime/open-agent/open-agent-provider.ts`. `runOpenAgentGraph` selects this adapter from `providerRuntime` when no explicit fake provider is supplied. Raw provider envelopes are written by the graph under `.agent-runs/open-agent/raw-provider`.

**Tech Stack:** TypeScript, Vitest, existing ProviderRuntimeConfig, existing redaction helpers, MiMo/DeepSeek OpenAI-compatible chat completions.

---

## Tasks

### Task 1: Provider Adapter Tests

- [ ] Add tests in `tests/unit/open-agent-provider.test.ts`:
  - request maps to `/chat/completions` with model and bearer auth;
  - plan parses strict JSON;
  - plan parses fenced JSON;
  - invalid JSON throws validation error;
  - raw envelope passed to callback is redacted.

### Task 2: Graph Provider Selection Tests

- [ ] Extend `tests/unit/open-agent-graph-nodes.test.ts`:
  - `providerRuntime.provider = "mimo-real"` with injected fetch drives graph plan/action;
  - graph result `providerCalls` is `2`;
  - graph writes raw provider request/response artifacts;
  - artifact does not contain test API key.

### Task 3: Implement Adapter

- [ ] Add `createOpenAiCompatibleOpenAgentProvider`.
- [ ] Add `selectOpenAgentProvider`.
- [ ] Add `parseOpenAgentNextAction`.
- [ ] Support MiMo/DeepSeek real runtime config.
- [ ] Preserve deterministic provider for fake/fixture runtimes.

### Task 4: Wire Graph Raw Artifacts

- [ ] Extend graph state/result with raw provider refs.
- [ ] Have provider raw callback write refs through `writeOpenAgentRawProviderArtifacts`.
- [ ] Ensure report includes refs but not raw payload.

### Task 5: Update Smoke

- [ ] Change `runOpenAgentRealSmoke({ mode: "llm-graph" })` so the graph itself performs provider-backed plan/action.
- [ ] Remove the graph-mode pre-call workaround.
- [ ] Keep deterministic smoke path compatible.

### Task 6: Verify

- [ ] Run focused tests:

```text
node node_modules/.bin/vitest run tests/unit/open-agent-provider.test.ts tests/unit/open-agent-graph-nodes.test.ts tests/unit/open-agent-real-smoke.test.ts
```

- [ ] Run full verification:

```text
node node_modules/.bin/vitest run
node node_modules/.bin/tsc --noEmit
git diff --check
```

- [ ] Run one real MiMo llm-graph smoke and verify:
  - status `PASSED`;
  - graph `providerCalls >= 2`;
  - raw request contains `[REDACTED]`;
  - no workspace write.
