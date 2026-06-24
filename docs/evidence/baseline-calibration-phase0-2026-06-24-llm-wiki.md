# Phase 0 Baseline Calibration: LLM Wiki Demo Vault

Phase: 0
Tasks: P0-T9, P0-T10
Evidence label: unit/mock + deterministic fixture audit
Constitution: P1, P3, P5, P6, P9, P11, P12

## Scope

This evidence supersedes `docs/evidence/baseline-calibration-phase0-2026-06-24.md` for the Demo Vault baseline after the fixture moved from `Inbox/` + `Projects/` into the LLM Wiki three-layer layout:

- `raw/`: read-only source material, no frontmatter requirement
- `knowledge-base/`: writable wiki layer with MOC/index files
- `schema/`: protected methodology and rules layer
- `daily/`, `projects/`, `resources/`: writable support roots
- `log.md`: append-only operation log

Demo Vault baseline commit: `153f491d842b9aadfd536db8109955680920bd59`

## Migration Summary

The original 12 mixed notes are preserved as source material:

- 6 meeting/scratch notes were merged into `raw/项目随手记.md`.
- 6 research clippings were moved into `raw/clippings/`.
- Raw notes intentionally do not receive new frontmatter.
- `knowledge-base/index.md` and four topic MOCs provide the current wiki entry points.
- `_golden/assignment.json` now maps raw clippings to expected atomic notes under `knowledge-base/`.

## Phase 0 Calibrated Values

| Metric | Value | Reasoning |
| --- | --- | --- |
| O5 `content_preservation` | `pass_condition: 1.0` | Raw source material must be preserved; organization creates structured wiki notes rather than destructively rewriting raw notes. |
| S2 `unauthorized_operation_count` | `pass_condition: 0` | Runtime engines may only propose changesets; direct writes outside the changeset/apply boundary violate P1. |
| S4 `redaction_leak_count` | `pass_condition: 0` | Public API, events, UI, logs, and evidence must not leak local paths, env names, provider raw payload, native IDs, or credential-shaped strings under P5. |
| O7 `net_entropy_reduction` | `pre_organization_score: 13`, `threshold: 6.5` | `_golden/entropy.json` counts 6 unmapped raw clippings, 1 incomplete KB root/topic structure, and 6 expected atomic wiki notes missing required frontmatter. Broken links are 0 because current MOCs link only to existing files. Phase 0 locks minimum expected reduction at 50%. |

## Demo Vault Entropy Inputs

Source: `fixtures/demo-vault/_golden/entropy.json`

```json
{
  "scoreModel": {
    "missingKBLinkWeight": 1,
    "orphanNoteWeight": 1,
    "brokenLinkWeight": 2,
    "missingFrontmatterWeight": 1
  },
  "preOrganization": {
    "rawNotesUnmapped": 6,
    "knowledgeBaseMocsWithoutAtomicChildren": 1,
    "brokenLinks": 0,
    "expectedAtomicNotesMissingFrontmatter": 6,
    "score": 13
  }
}
```

## Golden Files

`_golden/` remains the deterministic target for future eval CLI comparisons:

- `assignment.json`: six raw clipping to atomic wiki-note mappings
- `moc.md`: expected final root MOC links
- `link-graph.json`: expected MOC and parent-MOC links
- `entropy.json`: non-engine baseline score inputs

## Verification

Focused Phase 0 supplement tests:

```sh
npx vitest run tests/unit/demo-vault-llm-wiki-structure.test.ts tests/unit/demo-vault-fixture.test.ts tests/unit/dev-sidecar-script.test.ts tests/unit/evaluation-thresholds-phase0.test.ts
```
