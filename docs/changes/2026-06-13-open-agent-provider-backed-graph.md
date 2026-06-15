# Change: Open Agent Provider-backed Graph

## Why

The graph runner exists, but the real MiMo smoke still used a provider call outside the graph before running deterministic graph logic. That is not strong enough evidence for production-grade open agent behavior.

## Decision

Move real provider calls into the graph provider boundary:

- `runOpenAgentGraph` selects an OpenAI-compatible open-agent provider from `providerRuntime`;
- provider-backed graph calls `plan()` and `nextAction()` through that adapter;
- graph owns raw provider artifact refs and redaction;
- smoke harness becomes a thin opt-in runner instead of a provider pre-call workaround.

## Safety

- No API key is stored in code, docs, traces, reports, raw artifacts, stdout, or stderr.
- Provider output is parsed through schema-like validation before graph state advances.
- Provider cannot publish workspace writes.
- Candidate patch remains `publishable: false`.
