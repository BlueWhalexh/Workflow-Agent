# Change: Open Agent Candidate Patch And Real Smoke

## Why

The previous open agent baseline could answer questions and produce draft artifacts, but it could not represent the important middle state: “the agent has a concrete write proposal, but the workspace is not modified yet.”

That middle state is required for a production-grade knowledge agent because open-ended user requests may evolve into write intents, while publish safety must remain deterministic.

## Decision

Add `CANDIDATE_PATCH` to open agent output policy.

The candidate patch:

- is stored only in `.agent-runs/open-agent/<taskId>.json`;
- is explicitly `publishable: false`;
- contains target paths, file contents, content sha, rationale, and fixed workflow handoff;
- does not become a `PatchBundle` artifact consumed by publisher/resume;
- does not write `knowledge-base/` files.

Add fixed workflow handoff metadata to confirmation responses so backend integration can turn ambiguous write requests into explicit confirmation before invoking fixed workflow execution.

Add MiMo open-agent real smoke/eval as an opt-in path using stdin/env secrets and `--execute-real`.

## Consequences

- Open agent can now express more useful write proposals without weakening workspace safety.
- Backend integration has a clearer contract for confirmation UI and workflow execution.
- Real provider verification can be run against MiMo without storing token material.
- The project still does not have a fully LLM-driven open agent loop; this phase creates the safety and eval envelope for that later upgrade.
