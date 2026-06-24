# Demo Vault: LLM Wiki Three-Layer Fixture

This fixture is the Phase 0 local-first Obsidian-compatible Demo Vault.

It follows the LLM Wiki three-layer structure defined by `docs/design/mvp-release/06-schema-layer.md`:

- `raw/`: human-imported source material. It is readable but not writable by the agent.
- `knowledge-base/`: LLM-maintained wiki layer. Updates here must sync `knowledge-base/index.md` and `log.md`.
- `schema/`: protected rules and methodology layer. Edits require schema approval.
- `daily/`, `projects/`, and `resources/`: writable workspace support roots.
- `log.md`: append-only operation log.

The six research clippings in `raw/clippings/` are the Phase 0 organization inputs for `_golden/assignment.json`.
The six meeting and scratch notes are intentionally merged into `raw/项目随手记.md` so the fixture includes a mixed raw capture file without frontmatter.

`_golden/` contains deterministic eval targets only. It is not a user workspace area.
