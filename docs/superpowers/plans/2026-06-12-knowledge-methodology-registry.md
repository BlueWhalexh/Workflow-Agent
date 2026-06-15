# Knowledge Methodology Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a default `lmwiki-v1` methodology profile so deposition rules are data-driven instead of hard-coded in note quality and validation logic.

**Architecture:** Add a methodology registry in the domain layer. The first implementation slice preserves current behavior while moving section aliases and placeholder blockers into `lmwiki-v1`. Fixed workflows continue to run unchanged, but future workflows can select a profile.

**Tech Stack:** TypeScript, Vitest, existing domain/agent modules, filesystem workspace contract.

---

## File Structure

- Create `src/domain/methodology/knowledge-methodology.ts`: profile types, `lmwiki-v1`, registry helpers.
- Modify `src/agents/note-quality-loop.ts`: use methodology aliases/placeholders instead of hard-coded lists.
- Add `tests/unit/methodology.test.ts`: registry behavior and `lmwiki-v1` contract.
- Modify `tests/unit/note-quality-loop.test.ts`: assert repairs are driven by default methodology behavior.
- Modify `docs/reports/runtime-work-item-execution-resume-delivery.md`: append Phase 25 delivery result after implementation.

## Task 1: Methodology Registry Contract

**Files:**
- Create: `src/domain/methodology/knowledge-methodology.ts`
- Test: `tests/unit/methodology.test.ts`

- [ ] **Step 1: Write RED tests**

Create `tests/unit/methodology.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import {
  getKnowledgeMethodology,
  listKnowledgeMethodologies
} from "../../src/domain/methodology/knowledge-methodology.js";

describe("knowledge methodology registry", () => {
  it("returns lmwiki-v1 as the default methodology", () => {
    const methodology = getKnowledgeMethodology();

    expect(methodology.id).toBe("lmwiki-v1");
    expect(methodology.layout.rawDir).toBe("raw");
    expect(methodology.layout.rulesDir).toBe("schema");
    expect(methodology.layout.knowledgeBaseDir).toBe("knowledge-base");
    expect(methodology.noteSchema.requiredSections).toContain("摘要");
    expect(methodology.noteSchema.acceptedSectionAliases["来源追踪"]).toContain("Source Tracking");
  });

  it("lists registered methodologies without exposing mutable profile objects", () => {
    expect(listKnowledgeMethodologies()).toEqual([
      { id: "lmwiki-v1", displayName: "LMWiki", version: "1" }
    ]);
  });

  it("rejects unknown methodology ids", () => {
    expect(() => getKnowledgeMethodology("unknown")).toThrow("Unknown knowledge methodology: unknown");
  });
});
```

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/methodology.test.ts
```

Expected: FAIL because methodology registry does not exist.

- [ ] **Step 2: Implement registry**

Create:

```ts
export interface KnowledgeMethodology { ... }
export interface KnowledgeMethodologySummary { ... }
export function getKnowledgeMethodology(id = "lmwiki-v1"): KnowledgeMethodology
export function listKnowledgeMethodologies(): KnowledgeMethodologySummary[]
```

The default profile must include:

- layout for `raw`, `schema`, `knowledge-base`, `knowledge-base/topics`, `knowledge-base/moc.md`;
- required sections;
- aliases listed in `knowledge-methodology-registry-spec.md`;
- placeholder blockers;
- planner defaults matching current behavior.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/methodology.test.ts
```

Expected: PASS.

## Task 2: Note Quality Loop Uses Methodology

**Files:**
- Modify: `src/agents/note-quality-loop.ts`
- Modify: `tests/unit/note-quality-loop.test.ts`

- [ ] **Step 1: Write RED assertion**

Add a test proving `runNoteQualityLoop` accepts methodology alias data:

```ts
const methodology = getKnowledgeMethodology("lmwiki-v1");
expect(methodology.noteSchema.acceptedSectionAliases["摘要"]).toContain("Overview");
const result = runNoteQualityLoop({
  workItemId: "overview-heading",
  draftContent: "# Topic\n\n## Overview\n\nbody\n\n## Source\n\n- raw/a.md\n\n## Key Points\n\n- point\n"
});
expect(result.content).toContain("## 摘要");
expect(result.content).toContain("## 来源追踪");
expect(result.content).toContain("## 关键概念");
```

Expected: FAIL until normalization reads profile aliases.

- [ ] **Step 2: Refactor normalization**

Rules:

- derive heading alias replacement from default methodology;
- do not change current public function signature;
- preserve current repair issues:
  - `TOPIC_NOTE_WEAK_RELATIONS`;
  - `TOPIC_NOTE_NON_CANONICAL_HEADINGS`;
  - `TOPIC_NOTE_PLACEHOLDER_RELATED_LINKS`.

- [ ] **Step 3: Verify**

Run:

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/methodology.test.ts tests/unit/note-quality-loop.test.ts
```

Expected: PASS.

## Task 3: Focused Regression

**Files:**
- Existing tests only.

- [ ] **Step 1: Run focused tests**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test -- tests/unit/methodology.test.ts tests/unit/note-quality-loop.test.ts tests/unit/llm-provider.test.ts tests/integration/provider-failure.test.ts
```

Expected: PASS.

## Task 4: Full Verification And Report

- [ ] **Step 1: Full suite**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm test
```

- [ ] **Step 2: Typecheck**

```bash
PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin /opt/homebrew/bin/npm run typecheck
```

- [ ] **Step 3: Diff check**

```bash
git diff --check
```

- [ ] **Step 4: Update delivery report**

Record:

- methodology registry introduced;
- `lmwiki-v1` preserves current behavior;
- tests run and counts;
- no real external provider call required for this phase.

## Self Review

- Spec coverage: covers methodology registry, default profile, note quality integration, and no behavior drift.
- Placeholder scan: no TODO/TBD placeholders.
- Type consistency: `KnowledgeMethodology`, `KnowledgeMethodologySummary`, `getKnowledgeMethodology`, `listKnowledgeMethodologies`, and `lmwiki-v1` are used consistently.
