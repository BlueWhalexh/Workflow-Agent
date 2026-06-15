# Change: LLM-backed Open Agent Orchestrator

## Why

The current open agent baseline is safe but still mostly deterministic. It proves the contracts for answer, draft, candidate patch, confirmation, and MiMo smoke, but it does not yet provide a strong general agent experience.

The next product target is a knowledge-scoped agent that can solve open-ended tasks with multiple context reads and tool calls, closer to Claude Code-style problem solving, while still preserving fixed workflows for writes.

## Decision

Introduce a LangGraph-based `OpenAgentGraph` behind the SDK.

The graph owns:

- policy gate;
- LLM-backed plan node;
- deterministic context gather node;
- bounded agent tool loop node;
- synthesize node;
- self-check node;
- artifact/trace writer;
- confirmation/fixed workflow handoff.

The graph does not own:

- direct publish;
- workspace write semantics;
- methodology rules;
- provider secrets;
- resume/publish truth source.

## Technical Selection

- Keep TypeScript and LangGraph as the orchestration runtime.
- Keep Domain Core as the safety and file-contract authority.
- Keep MiMo/DeepSeek/OpenAI-compatible providers behind adapter interfaces.
- Keep fake provider as default test path.
- Keep real provider execution behind explicit opt-in and secret-safe stdin/env.

## Consequences

- Open agent becomes a real orchestrated runtime rather than a deterministic helper.
- Backend can opt into graph mode without changing default `handleCommand` behavior.
- The architecture supports more scenarios without enumerating every intent.
- Tests and artifacts must become stricter because LLM-backed output introduces more failure modes.
