# Change: Methodology Registry And Hybrid Agent Direction

> Date: 2026-06-12
> Status: accepted for next planning phase
> Scope: architecture direction, phase planning, documentation discipline

## Context

The project started with a fixed LangGraph workflow for organizing a knowledge workspace:

```text
inventory -> plan -> approval -> execute -> report
```

That workflow is still valid for standardized, high-risk write operations such as organizing or depositing content into a knowledge base.

The product direction has expanded:

- Imported files should trigger a compile/deposit workflow.
- The knowledge base should follow a three-layer structure:
  - raw source files, immutable by default;
  - deposited / organized knowledge;
  - rules or methodology files that define how deposition happens.
- Deposition rules should not be hard-coded. They should be selected from a registered methodology, such as an LMWiki-style methodology today and other methodologies later.
- The product should also expose stronger agent capabilities:
  - answer questions from the knowledge base;
  - analyze and expand topics;
  - generate study/interview question lists;
  - produce candidate patches when the user wants to persist generated outputs.

## Decision

Introduce two next-level architecture concepts:

1. **Knowledge Methodology Registry**
   - Fixed deposition workflows remain fixed.
   - The rules used by those workflows become replaceable methodology profiles.
   - The first default profile is `lmwiki-v1`.

2. **Hybrid Agent Capability Runtime**
   - Fixed workflows handle standardized write operations.
   - Flexible read-only or draft agents handle open-ended user requests.
   - Any workspace write still goes through `PatchBundle -> MergeGuard -> Validator -> Publisher`.

## Consequences

- Current `workspace-contract.md` remains the baseline, but it must be generalized from a single hard-coded layout to a methodology-driven layout.
- `sdk-tool-surface-spec.md` remains useful, but it is not enough by itself. The SDK must eventually accept commands/intents that choose either fixed workflows or flexible agent capabilities.
- The next implementation phase should prioritize methodology registry before broad agent capabilities, because deposition rules are the foundation for safe writes.

## Documentation Rule

When architecture direction changes, future agents must update or add a document under `docs/changes/` in the same phase as the spec/plan update.

Change records should include:

- context;
- decision;
- consequences;
- affected specs/plans;
- verification or follow-up phase.

## Affected Docs

- `docs/architecture/knowledge-methodology-registry-spec.md`
- `docs/superpowers/plans/2026-06-12-knowledge-methodology-registry.md`
- `docs/architecture/README.md`
- `docs/architecture/runtime-phase-sop.md`
