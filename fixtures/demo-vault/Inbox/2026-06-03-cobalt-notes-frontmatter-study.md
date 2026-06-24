---
title: Cobalt Notes frontmatter schema study
project: Cobalt Notes
type: research
date: 2026-06-03
status: inbox
source: clipped-research
---

# Cobalt Notes frontmatter schema study

Research question: how strict should the MVP ruleset be for imported research notes?

Findings:
- Required fields should be minimal: title, project, type, date, status, source.
- Tags are useful but should stay optional in Phase 0.
- Some legacy notes use `category` instead of `type`; validator should reject missing required fields rather than silently remap.

中文补充: 对于 Obsidian vault, frontmatter 不应该破坏正文。添加 YAML header 可以,但正文原文需要保留。

Related target: [[Projects/CobaltNotes/Cobalt Notes MOC]]
