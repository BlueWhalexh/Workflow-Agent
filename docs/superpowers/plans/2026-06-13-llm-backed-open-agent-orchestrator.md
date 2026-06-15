# LLM-backed Open Agent Orchestrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first production-grade LLM-backed open agent graph for knowledge workspace tasks while preserving fixed workflow as the only publish path.

**Architecture:** Add a LangGraph-based `OpenAgentGraph` behind the public SDK. The graph plans, gathers context, runs a bounded read-only tool loop, synthesizes answer/draft/candidate patch outputs, self-checks safety/grounding, and writes auditable artifacts. Workspace writes remain impossible from open graph; confirmed writes hand off to fixed workflow.

**Tech Stack:** TypeScript, `@langchain/langgraph`, Vitest, existing OpenAI-compatible MiMo/DeepSeek adapters, `.agent-runs` filesystem artifacts.

---

## File Structure

- Create `src/runtime/open-agent/open-agent-state.ts`: graph state, status enum, budget, trace refs.
- Create `src/runtime/open-agent/open-agent-graph.ts`: LangGraph assembly and public graph runner.
- Create `src/runtime/open-agent/nodes/policy-gate-node.ts`: policy validation and budget setup.
- Create `src/runtime/open-agent/nodes/plan-node.ts`: LLM/fake provider plan generation with schema parsing.
- Create `src/runtime/open-agent/nodes/context-gather-node.ts`: deterministic workspace scan and scoped file reads.
- Create `src/runtime/open-agent/nodes/tool-loop-node.ts`: bounded agent tool loop.
- Create `src/runtime/open-agent/nodes/synthesize-node.ts`: output creation.
- Create `src/runtime/open-agent/nodes/self-check-node.ts`: grounding/schema/write-boundary validation.
- Create `src/runtime/open-agent/nodes/artifact-node.ts`: report/trace/raw ref artifact writer.
- Create `src/runtime/open-agent/nodes/handoff-node.ts`: confirmation/fixed workflow handoff.
- Create `src/runtime/open-agent/open-agent-artifacts.ts`: stable paths and artifact JSON writer.
- Create `src/runtime/open-agent/open-agent-provider.ts`: provider prompt + schema adapter for open graph.
- Modify `src/sdk/knowledge-workflow-agent.ts`: export `runOpenAgentGraph`.
- Modify `src/sdk/command-router.ts`: optional `openAgentMode: "deterministic" | "llm-graph"` integration.
- Modify `src/index.ts`: public exports.
- Create `tests/unit/open-agent-graph-policy.test.ts`.
- Create `tests/unit/open-agent-graph-nodes.test.ts`.
- Create `tests/integration/open-agent-graph.test.ts`.
- Create `tests/unit/open-agent-graph-real-smoke.test.ts`.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`.

## Milestone 1: Graph Contract And Policy Gate

Goal: establish state contract and block unsafe graph execution before provider calls.

- [ ] Add failing tests in `tests/unit/open-agent-graph-policy.test.ts`:
  - unknown methodology returns `FAILED_POLICY`;
  - allowed tools containing `patch.publish` returns `FAILED_POLICY`;
  - `maxIterations: 0` returns `FAILED_BUDGET`;
  - no provider call is made on policy failure.
- [ ] Create `src/runtime/open-agent/open-agent-state.ts` with:
  - `OpenAgentGraphStatus`;
  - `OpenAgentGraphState`;
  - `OpenAgentLoopBudget`;
  - `OpenAgentGraphStep`;
  - `OpenAgentGraphToolCall`.
- [ ] Create `policy-gate-node.ts`:
  - validate methodology via `getKnowledgeMethodology`;
  - reject `patch.publish`;
  - default budget `{ maxIterations: 3, maxToolCalls: 8, timeoutMs: 30000 }`;
  - mark `realExternalCall` from provider runtime.
- [ ] Create minimal `open-agent-graph.ts` runner that executes policy gate only.
- [ ] Export `runOpenAgentGraph` from SDK and root index.
- [ ] Run:

```text
node node_modules/.bin/vitest run tests/unit/open-agent-graph-policy.test.ts
node node_modules/.bin/tsc --noEmit
```

Expected: policy tests pass; typecheck passes.

## Milestone 2: Plan And Context Nodes

Goal: generate a schema-valid plan and gather scoped workspace context.

- [ ] Add failing tests in `tests/unit/open-agent-graph-nodes.test.ts`:
  - fake provider plan with invalid JSON returns `FAILED_VALIDATION`;
  - valid plan records `methodology.read` and `workspace.scan`;
  - context gather includes raw files, knowledge pages, and grounding candidates;
  - context gather never reads outside workspace contract.
- [ ] Create `open-agent-provider.ts`:
  - `generateOpenAgentPlan`;
  - parse JSON schema `{ objective, outputPolicy, steps, contextHints }`;
  - support fake provider dependency injection.
- [ ] Implement `plan-node.ts`:
  - call provider only after policy gate;
  - write provider call summary to state, not raw token data;
  - fall back to deterministic plan only when provider runtime is fake fallback.
- [ ] Implement `context-gather-node.ts`:
  - use `scanWorkspace`;
  - select top raw/knowledge paths from hints and deterministic fallback;
  - store file refs and short excerpts, not arbitrary full workspace dump.
- [ ] Run:

```text
node node_modules/.bin/vitest run tests/unit/open-agent-graph-policy.test.ts tests/unit/open-agent-graph-nodes.test.ts
```

Expected: policy + plan/context tests pass.

## Milestone 3: Bounded Tool Loop

Goal: make the multi-step agent loop explicit and auditable.

- [ ] Add failing tests:
  - tool loop stops at `maxIterations`;
  - tool loop stops when provider says `solved`;
  - tool loop returns `NEEDS_CONFIRMATION` when provider requests write with unclear target;
  - trace records tool call names and observation refs, not private chain-of-thought.
- [ ] Implement `tool-loop-node.ts`:
  - allowed tools: `workspace.scan`, `artifact.readEval`, `patch.validate`;
  - blocked tools: all `WORKSPACE_WRITE` and `INTERNAL_ONLY` publish tools;
  - each iteration records `iteration`, `action`, `toolName`, `observationRef`, `status`;
  - status `FAILED_BUDGET` when budget is exhausted without solved/confirmation.
- [ ] Add deterministic fake provider scripts for:
  - solved after one read;
  - needs more context then solved;
  - asks to publish and gets blocked.
- [ ] Run focused tests.

Expected: loop behavior is deterministic under fake provider and does not expose publish.

## Milestone 4: Synthesis And Self-check

Goal: produce useful outputs with grounding and reject fake success.

- [ ] Add failing tests:
  - `ANSWER_ONLY` includes `Sources` and non-empty `groundingRefs`;
  - `DRAFT_ARTIFACT` includes draft marker and source refs;
  - `CANDIDATE_PATCH` is `publishable: false` and target is under `knowledge-base/`;
  - empty grounding for workspace-based answer returns `FAILED_VALIDATION`;
  - target under `raw/` or `schema/` returns `FAILED_POLICY`.
- [ ] Implement `synthesize-node.ts`.
- [ ] Implement `self-check-node.ts`.
- [ ] Reuse existing `CandidatePatchProposal`, `DraftArtifact`, `FixedWorkflowHandoff` types where possible.
- [ ] Run:

```text
node node_modules/.bin/vitest run tests/unit/open-agent-graph-nodes.test.ts tests/integration/open-agent-graph.test.ts
```

Expected: answer/draft/candidate outputs pass; invalid grounding/write targets fail.

## Milestone 5: Artifact, Trace, Redaction

Goal: make runs inspectable and safe for backend/eval.

- [ ] Add failing tests:
  - report written to `.agent-runs/open-agent/<taskId>.json`;
  - trace written to `.agent-runs/open-agent/traces/<taskId>.jsonl`;
  - raw provider request/response refs are redacted;
  - API key string does not appear in artifact tree;
  - trace is not used as publish/resume truth source.
- [ ] Implement `open-agent-artifacts.ts`.
- [ ] Implement `artifact-node.ts`.
- [ ] Reuse `writeRawEnvelopeArtifacts` or extract shared writer if path shape needs open-agent root support.
- [ ] Update delivery report with artifact contract.
- [ ] Run focused artifact tests and `git diff --check`.

Expected: all open graph artifacts are parseable, redacted, and workspace-safe.

## Milestone 6: SDK And Router Integration

Goal: expose the graph without changing default behavior unexpectedly.

- [ ] Add failing SDK/router tests:
  - `createKnowledgeWorkflowAgent().runOpenAgentGraph` exists;
  - root index exports graph runner and types;
  - `handleCommand({ openAgentMode: "llm-graph" })` invokes graph for open tasks;
  - default `handleCommand` remains deterministic open runtime;
  - confirmation and fixed workflow routes preserve current behavior.
- [ ] Modify `RunOpenAgentGraphRequest` / `RunOpenAgentGraphResult` public types.
- [ ] Modify `HandleCommandRequest` with optional `openAgentMode`.
- [ ] Implement router integration behind explicit option only.
- [ ] Run:

```text
node node_modules/.bin/vitest run tests/unit/sdk.test.ts tests/unit/command-router.test.ts tests/integration/open-agent-graph.test.ts
```

Expected: SDK graph mode works; existing deterministic mode remains compatible.

## Milestone 7: MiMo Real Open Graph Smoke

Goal: prove the graph can make a real LLM call without leaking secrets.

- [ ] Add real smoke inspect tests:
  - missing env skips;
  - no `--execute-real` skips;
  - stdin key is not printed;
  - injected fetch pass covers MiMo URL, Authorization header, model, output artifact.
- [ ] Add CLI or extend `src/cli/open-agent-smoke.ts`:
  - `--mode llm-graph`;
  - `--api-key-stdin`;
  - `--execute-real`;
  - `--output-policy ANSWER_ONLY | CANDIDATE_PATCH`.
- [ ] Run injected-fetch tests.
- [ ] Run one opt-in real MiMo smoke against temp fixture workspace:

```text
read -rs MIMO_API_KEY
export MIMO_API_KEY
node --import tsx src/cli/open-agent-smoke.ts \
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
- `realExternalCall: true`;
- artifact path exists;
- no token in stdout/stderr/artifacts;
- no `knowledge-base/` file written by open graph.

## Milestone 8: Full Verification And Review

Goal: close the phase with evidence that can support implementation handoff.

- [ ] Run:

```text
node node_modules/.bin/vitest run
node node_modules/.bin/tsc --noEmit
git diff --check
```

- [ ] Update `docs/reports/runtime-work-item-execution-resume-delivery.md` with:
  - focused tests;
  - full tests;
  - typecheck;
  - diff check;
  - real MiMo smoke result;
  - boundaries and review focus.
- [ ] Self-review:
  - no direct publish from open graph;
  - no provider token persisted;
  - no default real external call;
  - candidate patches remain non-publishable;
  - traces are audit/eval only.

## Out Of Scope

- Vector database or embedding retrieval.
- Long-term conversational memory.
- Multi-user auth/permission model.
- Browser/frontend confirmation UI.
- Making LLM-backed graph the default path.
- Direct publish from open agent.

## Completion Criteria

This phase is complete only when:

- LLM-backed graph works with fake provider and MiMo real smoke.
- `handleCommand` can opt into graph mode.
- Existing deterministic open runtime remains compatible.
- All safety blockers have tests.
- Full verification passes.
