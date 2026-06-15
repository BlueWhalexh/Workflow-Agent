# Knowledge Methodology Registry Spec

> 状态：Phase 25 candidate spec。目标是把“落库规则”从固定代码里抽象为可注册、可替换、可验证的 methodology profile，使固定 deposition workflow 可以按不同知识库方法论运行。

## 1. Background

当前 workspace contract 已经定义三类路径：

```text
raw/
schema/
knowledge-base/
```

它符合 LMWiki 风格的三层结构：

- `raw/`: 原文件层，只读，不允许 agent 默认覆盖。
- `knowledge-base/`: 落库结果层，通过 `PatchBundle` 写入。
- `schema/`: 规则层，只读，定义 workspace 约束。

当前问题是：落库规则仍然散落在 planner、validator、note agent、topic index、MOC 等实现中。后续如果要支持新的知识库方法论，就会被现有目录结构和 note schema 绑死。

## 2. Goal

新增 **Knowledge Methodology Registry**：

```text
fixed deposition workflow
  -> select methodology profile
  -> scan workspace using profile layout
  -> plan work items using profile rules
  -> run agent nodes with profile note schema
  -> validate using profile quality gates
  -> publish
```

目标是保持 workflow 稳定，同时让落库规则可替换。

## 3. Non-Goals

本阶段不做：

- 不实现任意用户自定义脚本规则。
- 不允许 methodology 直接写 workspace。
- 不把 LLM prompt 当作唯一规则来源。
- 不接前端配置页面。
- 不引入数据库。
- 不把 flexible answer agent 和 deposition workflow 混在一个实现里。

## 4. Methodology Profile Contract

第一版 profile 是 TypeScript object，不从外部动态加载。

```ts
interface KnowledgeMethodology {
  id: string;
  displayName: string;
  version: string;
  layout: {
    rawDir: string;
    rulesDir: string;
    knowledgeBaseDir: string;
    topicDir: string;
    mocPath: string;
  };
  noteSchema: {
    requiredSections: string[];
    acceptedSectionAliases: Record<string, string[]>;
    placeholderBlockers: string[];
  };
  planner: {
    defaultPublishPolicy: "AUTO_PUBLISH" | "CANDIDATE_PATCH_ONLY";
    createTopicIndex: boolean;
    createMoc: boolean;
    finalQualityReview: boolean;
  };
  validation: {
    hardBlockers: string[];
    repairableIssues: string[];
  };
}
```

## 5. Default Profile: `lmwiki-v1`

`lmwiki-v1` captures the current behavior:

```text
raw/
schema/
knowledge-base/
  moc.md
  topics/<topic>/index.md
  topics/<topic>/<note>.md
```

Required note sections:

- `摘要`
- `来源追踪`
- one of:
  - `关键决策`
  - `关键概念`
  - `关键步骤`
- `相关链接`

Accepted aliases:

- `总结`, `Summary`, `Overview` -> `摘要`
- `源追踪`, `源信息`, `源文件`, `源文件追踪`, `Source Tracking`, `Source`, `Sources` -> `来源追踪`
- `关键内容`, `Key Content`, `Key Points`, `Key Concepts` -> `关键概念`
- `Related Links`, `References` -> `相关链接`

Placeholder blockers:

- `TODO`
- `TBD`
- `<placeholder>`
- `后续补充`
- related-link lines containing `待补充`

## 6. Registry API

```ts
function getKnowledgeMethodology(id?: string): KnowledgeMethodology;
function listKnowledgeMethodologies(): KnowledgeMethodologySummary[];
```

Rules:

- default methodology is `lmwiki-v1`;
- unknown methodology id fails fast;
- profiles are immutable at runtime;
- profile id is recorded in run artifacts in a later phase.

## 7. Integration Points

### Planner

Planner should derive:

- raw dir;
- knowledge-base dir;
- topic index policy;
- MOC policy;
- quality review policy;
- publish policy.

### Note Quality Loop

Heading normalization should come from `noteSchema.acceptedSectionAliases`.

Placeholder cleanup should come from `noteSchema.placeholderBlockers`.

### Validator

Required sections and hard blockers should come from methodology validation rules.

### Report / Eval

Report should include `methodologyId` once run artifacts support it.

## 8. First Implementation Slice

Keep behavior unchanged while introducing the registry:

- Add profile contract and `lmwiki-v1`.
- Move current section aliases and placeholder blockers into profile data.
- Update `runNoteQualityLoop` to use default profile data.
- Add tests proving current behavior still passes through the profile.

## 9. Future Extension

Later profiles can model:

- project handbook;
- interview question bank;
- research literature notes;
- product requirement knowledge base;
- API documentation knowledge base.

Each profile can change structure and validation rules without replacing the fixed workflow engine.

## 10. Review Focus

- Does profile abstraction reduce hard-coded rules without making arbitrary unsafe execution possible?
- Does `lmwiki-v1` exactly preserve current behavior?
- Are raw/rules directories still read-only by default?
- Are write operations still constrained by PatchBundle, MergeGuard, Validator, and Publisher?
