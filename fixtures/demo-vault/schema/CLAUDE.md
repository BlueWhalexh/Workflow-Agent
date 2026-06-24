# Demo Vault Constitution

This vault is a Phase 0 fixture for a local-first Obsidian-compatible Knowledge Workspace Agent.

## Boundaries

- `raw/` is source input and must not be edited by the agent.
- `knowledge-base/` is the structured wiki layer maintained through changesets.
- `schema/` is protected and requires `schema-edit` approval.
- `log.md` is append-only.

## Required Behavior

- Preserve source content from `raw/`.
- Cite source notes when creating or updating wiki notes.
- Keep atomic wiki notes linked upward to a topic MOC.
- Sync `knowledge-base/index.md` and `log.md` for every knowledge-base update.
- Follow the repository constitution at `docs/constitution.md`.
