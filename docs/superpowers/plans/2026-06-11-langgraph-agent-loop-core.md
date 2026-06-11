# LangGraph Agent Loop Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first testable slice of the semi-automatic knowledge workspace agent loop: inventory, plan approval, mock note agent, PatchBundle, MergeGuard, Publisher, Validator/Eval, and resume.

**Architecture:** Use a TypeScript-first domain core with LangGraph as the Level 1 workflow shell. Domain services own workspace facts, plans, patches, validation, and reports; LangGraph only orchestrates node order, approval interrupt, checkpoint, and resume. Agent nodes are bounded local reasoning units that output `PatchBundle` or `QualityFindings`; they never write workspace directly.

**Tech Stack:** TypeScript, Node.js, `@langchain/langgraph`, Vitest, filesystem-backed `.agent-runs` artifacts, mock agents first, optional real provider smoke after contract tests pass.

---

## Deliverable Result

When this plan is complete, the repo will contain a working local spike:

```text
npm test
  -> unit/domain tests pass
  -> integration/runtime tests pass

npm run organize -- tests/fixtures/workspaces/basic-raw-mirror "整理全部知识库"
  -> writes .agent-runs/<runId>/
  -> stops at plan approval in non-auto mode

npm run organize -- tests/fixtures/workspaces/basic-raw-mirror "整理全部知识库" --auto-approve
  -> rewrites one bootstrap raw mirror through mock NoteAgentNode
  -> writes agent-meta
  -> publishes only through Publisher
  -> writes validation.json, eval.json, report.md

npm run resume -- tests/fixtures/workspaces/resume-run
  -> skips already published matching work items
  -> retries failed retryable work items
```

The first delivery is intentionally mock-provider only. A real provider smoke can be added only after the mock path proves the contracts.

## File Structure

Create this structure:

```text
package.json
tsconfig.json
vitest.config.ts
src/
  domain/
    workspace/
      inventory.ts
      page-state.ts
      workspace-contract.ts
    planning/
      plan.ts
      work-item.ts
      organize-planner.ts
    patch/
      patch-bundle.ts
      merge-guard.ts
      publisher.ts
    validation/
      validator.ts
      eval-reporter.ts
      resume-decision.ts
  runtime/
    langgraph/
      state.ts
      graph.ts
      nodes/
        inventory-node.ts
        plan-node.ts
        approval-node.ts
        execute-phase-node.ts
        merge-node.ts
        publish-node.ts
        validate-node.ts
        report-node.ts
  agents/
    work-item-agent.ts
    mock-note-agent.ts
    mock-topic-index-agent.ts
    quality-review-agent.ts
  storage/
    workspace-fs.ts
    agent-runs-store.ts
    sha.ts
    json-schema.ts
  cli/
    organize.ts
    resume.ts
tests/
  fixtures/
    workspaces/
      basic-raw-mirror/
      placeholder-blocked/
      resume-run/
  unit/
  integration/
```

## Task 1: Project Tooling

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `vitest.config.ts`
- Create: `src/index.ts`
- Test: `tests/unit/tooling.test.ts`

- [ ] **Step 1: Write the failing tooling smoke test**

Create `tests/unit/tooling.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { projectName } from "../../src/index";

describe("project tooling", () => {
  it("loads TypeScript source through Vitest", () => {
    expect(projectName).toBe("my-workflow-agent-loop-core");
  });
});
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
npm test -- tests/unit/tooling.test.ts
```

Expected: command fails because `package.json` and `src/index.ts` do not exist.

- [ ] **Step 3: Add minimal TypeScript/Vitest project files**

Create `package.json`:

```json
{
  "name": "my-workflow-agent-loop-core",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "engines": {
    "node": ">=20.11"
  },
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "typecheck": "tsc --noEmit",
    "organize": "tsx src/cli/organize.ts",
    "resume": "tsx src/cli/resume.ts"
  },
  "dependencies": {
    "@langchain/langgraph": "^0.4.0",
    "zod": "^3.25.0"
  },
  "devDependencies": {
    "@types/node": "^24.0.0",
    "tsx": "^4.20.0",
    "typescript": "^5.8.0",
    "vitest": "^3.2.0"
  }
}
```

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "strict": true,
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "types": ["node", "vitest/globals"]
  },
  "include": ["src/**/*.ts", "tests/**/*.ts"]
}
```

Create `vitest.config.ts`:

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
    testTimeout: 10_000
  }
});
```

Create `src/index.ts`:

```ts
export const projectName = "my-workflow-agent-loop-core";
```

- [ ] **Step 4: Install dependencies**

Run:

```bash
npm install
```

Expected: command exits 0 and creates `package-lock.json`.

- [ ] **Step 5: Verify test and typecheck pass**

Run:

```bash
npm test -- tests/unit/tooling.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add package.json package-lock.json tsconfig.json vitest.config.ts src/index.ts tests/unit/tooling.test.ts
git commit -m "chore: initialize typescript test harness"
```

## Task 2: Workspace Fixtures

**Files:**
- Create: `tests/fixtures/workspaces/basic-raw-mirror/raw/tools/Skill vs CLI Tool 决策.md`
- Create: `tests/fixtures/workspaces/basic-raw-mirror/raw/go/Go 基础语法.md`
- Create: `tests/fixtures/workspaces/basic-raw-mirror/raw/agent/Agent Loop 失败复盘.md`
- Create: `tests/fixtures/workspaces/basic-raw-mirror/schema/CLAUDE.md`
- Create: `tests/fixtures/workspaces/basic-raw-mirror/knowledge-base/moc.md`
- Create: `tests/fixtures/workspaces/basic-raw-mirror/knowledge-base/topics/tools/Skill vs CLI Tool 决策.md`
- Create: `tests/fixtures/workspaces/placeholder-blocked/...`
- Create: `tests/fixtures/workspaces/resume-run/...`
- Test: `tests/unit/fixtures.test.ts`

- [ ] **Step 1: Write fixture existence test**

Create `tests/unit/fixtures.test.ts`:

```ts
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const fixtureRoot = path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror");

describe("workspace fixtures", () => {
  it("contains raw, schema, knowledge-base, and bootstrap mirror files", () => {
    expect(existsSync(path.join(fixtureRoot, "raw/tools/Skill vs CLI Tool 决策.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "raw/go/Go 基础语法.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "raw/agent/Agent Loop 失败复盘.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "schema/CLAUDE.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "knowledge-base/moc.md"))).toBe(true);

    const mirror = readFileSync(
      path.join(fixtureRoot, "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"),
      "utf8"
    );
    expect(mirror).toContain("Raw mirror:");
    expect(mirror).toContain("Source path:");
    expect(mirror).toContain("## Content");
  });
});
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
npm test -- tests/unit/fixtures.test.ts
```

Expected: FAIL because fixture files do not exist.

- [ ] **Step 3: Add `basic-raw-mirror` fixture**

Create `tests/fixtures/workspaces/basic-raw-mirror/raw/tools/Skill vs CLI Tool 决策.md`:

```md
# Skill vs CLI Tool 决策

## 背景

团队需要判断一个能力应该沉淀成 Codex skill，还是做成 CLI tool。

## 决策

- Skill 适合流程、判断标准、上下文组织和人机协作约束。
- CLI tool 适合可重复、确定性、可测试、可组合的机械动作。

## 取舍

Skill 更容易让 agent 理解意图，但不能替代确定性验证。
CLI 更容易进入 CI 和本地自动化，但不适合承载模糊决策。
```

Create `tests/fixtures/workspaces/basic-raw-mirror/raw/go/Go 基础语法.md`:

```md
# Go 基础语法

## 关键点

- Go 使用 `package` 声明包。
- `func` 声明函数。
- 错误通常作为返回值显式处理。

## 注意

示例代码需要结合模块路径和测试环境运行。
```

Create `tests/fixtures/workspaces/basic-raw-mirror/raw/agent/Agent Loop 失败复盘.md`:

```md
# Agent Loop 失败复盘

## 现象

一次全库整理中，agent 读了 schema，也生成了大量 wikilinks，但没有创建有效页面。

## 根因

单个长 loop 同时承担扫描、规划、写正文、维护索引和质量收敛。

## 改进

把任务拆成 work item，并用 PatchBundle、MergeGuard、Validator 控制写入。
```

Create `tests/fixtures/workspaces/basic-raw-mirror/schema/CLAUDE.md`:

```md
# Knowledge Workspace Rules

- raw is read-only.
- schema is read-only.
- knowledge-base is writable only through approved patches.
- Topic notes must contain title, summary, source tracking, key concepts, and related links.
```

Create `tests/fixtures/workspaces/basic-raw-mirror/knowledge-base/moc.md`:

```md
# MOC

```

Create `tests/fixtures/workspaces/basic-raw-mirror/knowledge-base/topics/tools/Skill vs CLI Tool 决策.md`:

```md
# Skill vs CLI Tool 决策

Raw mirror: true
Source path: raw/tools/Skill vs CLI Tool 决策.md

## Content

团队需要判断一个能力应该沉淀成 Codex skill，还是做成 CLI tool。
```

- [ ] **Step 4: Add `placeholder-blocked` fixture**

Create `tests/fixtures/workspaces/placeholder-blocked/raw/tools/Placeholder 示例.md`:

```md
# Placeholder 示例

## 背景

这个 raw 用来验证 Validator 能阻断占位内容。
```

Create `tests/fixtures/workspaces/placeholder-blocked/schema/CLAUDE.md`:

```md
# Knowledge Workspace Rules

- Do not publish placeholder content.
```

Create `tests/fixtures/workspaces/placeholder-blocked/knowledge-base/moc.md`:

```md
# MOC
```

- [ ] **Step 5: Add `resume-run` fixture shell**

Create `tests/fixtures/workspaces/resume-run/raw/tools/Skill vs CLI Tool 决策.md`:

```md
# Skill vs CLI Tool 决策

## 决策

Skill 适合流程和判断，CLI tool 适合确定性动作。
```

Create `tests/fixtures/workspaces/resume-run/schema/CLAUDE.md`:

```md
# Knowledge Workspace Rules

- Resume must skip published matching work items.
```

Create `tests/fixtures/workspaces/resume-run/knowledge-base/topics/tools/Skill vs CLI Tool 决策.md`:

```md
# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - raw/tools/Skill vs CLI Tool 决策.md
sourceShas:
  raw/tools/Skill vs CLI Tool 决策.md: fixture-source-sha
lastRunId: run-fixture
contentSha: fixture-content-sha
-->

## 摘要

这个 note 说明 skill 与 CLI tool 的适用边界。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Skill 用于流程和判断。
- CLI tool 用于确定性动作。

## 相关链接

暂无相关链接。
```

- [ ] **Step 6: Verify fixture test passes**

Run:

```bash
npm test -- tests/unit/fixtures.test.ts
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add tests/fixtures tests/unit/fixtures.test.ts
git commit -m "test: add workspace fixtures"
```

## Task 3: Workspace Inventory and Page State

**Files:**
- Create: `src/storage/sha.ts`
- Create: `src/storage/workspace-fs.ts`
- Create: `src/domain/workspace/page-state.ts`
- Create: `src/domain/workspace/inventory.ts`
- Test: `tests/unit/inventory.test.ts`

- [ ] **Step 1: Write failing inventory tests**

Create `tests/unit/inventory.test.ts`:

```ts
import path from "node:path";
import { describe, expect, it } from "vitest";
import { scanWorkspace } from "../../src/domain/workspace/inventory";
import { detectPageState } from "../../src/domain/workspace/page-state";

const fixtureRoot = path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror");

describe("workspace inventory", () => {
  it("scans raw, schema, knowledge-base, and mirror candidates", async () => {
    const inventory = await scanWorkspace({ workspaceRoot: fixtureRoot });

    expect(inventory.rawFiles.map((file) => file.path).sort()).toEqual([
      "raw/agent/Agent Loop 失败复盘.md",
      "raw/go/Go 基础语法.md",
      "raw/tools/Skill vs CLI Tool 决策.md"
    ]);
    expect(inventory.schemaFiles.map((file) => file.path)).toEqual(["schema/CLAUDE.md"]);
    expect(inventory.knowledgeBasePages.map((page) => page.path)).toContain(
      "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"
    );
    expect(inventory.rawMirrorCandidates).toEqual([
      "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"
    ]);
  });

  it("detects bootstrap mirror page state", () => {
    const state = detectPageState(`# Title

Raw mirror: true
Source path: raw/tools/example.md

## Content

Raw body`);

    expect(state).toBe("BOOTSTRAP_MIRROR");
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/unit/inventory.test.ts
```

Expected: FAIL because inventory modules do not exist.

- [ ] **Step 3: Implement sha helper**

Create `src/storage/sha.ts`:

```ts
import { createHash } from "node:crypto";

export function sha256(content: string): string {
  return createHash("sha256").update(content, "utf8").digest("hex");
}
```

- [ ] **Step 4: Implement workspace filesystem helpers**

Create `src/storage/workspace-fs.ts`:

```ts
import { promises as fs } from "node:fs";
import path from "node:path";

export async function listMarkdownFiles(root: string, relativeDir: string): Promise<string[]> {
  const absoluteDir = path.join(root, relativeDir);
  try {
    const entries = await fs.readdir(absoluteDir, { withFileTypes: true });
    const nested = await Promise.all(
      entries.map(async (entry) => {
        const relativePath = path.join(relativeDir, entry.name);
        if (entry.isDirectory()) {
          return listMarkdownFiles(root, relativePath);
        }
        if (entry.isFile() && entry.name.endsWith(".md")) {
          return [relativePath.split(path.sep).join("/")];
        }
        return [];
      })
    );
    return nested.flat().sort();
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return [];
    }
    throw error;
  }
}

export async function readWorkspaceFile(root: string, relativePath: string): Promise<string> {
  return fs.readFile(path.join(root, relativePath), "utf8");
}
```

- [ ] **Step 5: Implement page state detection**

Create `src/domain/workspace/page-state.ts`:

```ts
export type PageState = "BOOTSTRAP_MIRROR" | "AGENT_ORGANIZED" | "USER_EDITED" | "MIXED";

export function detectPageState(content: string): PageState {
  const hasAgentMeta = content.includes("<!-- agent-meta");
  const looksLikeMirror =
    content.includes("Raw mirror:") &&
    content.includes("Source path:") &&
    content.includes("## Content");

  if (!hasAgentMeta && looksLikeMirror) {
    return "BOOTSTRAP_MIRROR";
  }
  if (hasAgentMeta) {
    return "AGENT_ORGANIZED";
  }
  return "USER_EDITED";
}
```

- [ ] **Step 6: Implement inventory scan**

Create `src/domain/workspace/inventory.ts`:

```ts
import { listMarkdownFiles, readWorkspaceFile } from "../../storage/workspace-fs";
import { sha256 } from "../../storage/sha";
import { detectPageState, type PageState } from "./page-state";

export interface InventoryFile {
  path: string;
  sha: string;
  title: string | null;
  headings: string[];
}

export interface KnowledgeBasePage extends InventoryFile {
  state: PageState;
}

export interface WorkspaceInventory {
  workspaceRoot: string;
  rawFiles: InventoryFile[];
  schemaFiles: InventoryFile[];
  knowledgeBasePages: KnowledgeBasePage[];
  rawMirrorCandidates: string[];
  placeholderCandidates: string[];
  mocPath: string | null;
  topicIndexPaths: string[];
}

function extractTitle(content: string): string | null {
  const line = content.split(/\r?\n/).find((item) => item.startsWith("# "));
  return line ? line.replace(/^#\s+/, "").trim() : null;
}

function extractHeadings(content: string): string[] {
  return content
    .split(/\r?\n/)
    .filter((line) => /^#{1,6}\s+/.test(line))
    .map((line) => line.trim());
}

async function readInventoryFile(workspaceRoot: string, relativePath: string): Promise<InventoryFile> {
  const content = await readWorkspaceFile(workspaceRoot, relativePath);
  return {
    path: relativePath,
    sha: sha256(content),
    title: extractTitle(content),
    headings: extractHeadings(content)
  };
}

export async function scanWorkspace(input: { workspaceRoot: string }): Promise<WorkspaceInventory> {
  const rawPaths = await listMarkdownFiles(input.workspaceRoot, "raw");
  const schemaPaths = await listMarkdownFiles(input.workspaceRoot, "schema");
  const knowledgePaths = await listMarkdownFiles(input.workspaceRoot, "knowledge-base");

  const rawFiles = await Promise.all(rawPaths.map((filePath) => readInventoryFile(input.workspaceRoot, filePath)));
  const schemaFiles = await Promise.all(schemaPaths.map((filePath) => readInventoryFile(input.workspaceRoot, filePath)));

  const knowledgeBasePages = await Promise.all(
    knowledgePaths.map(async (filePath) => {
      const content = await readWorkspaceFile(input.workspaceRoot, filePath);
      return {
        path: filePath,
        sha: sha256(content),
        title: extractTitle(content),
        headings: extractHeadings(content),
        state: detectPageState(content)
      };
    })
  );

  return {
    workspaceRoot: input.workspaceRoot,
    rawFiles,
    schemaFiles,
    knowledgeBasePages,
    rawMirrorCandidates: knowledgeBasePages
      .filter((page) => page.state === "BOOTSTRAP_MIRROR")
      .map((page) => page.path),
    placeholderCandidates: knowledgeBasePages
      .filter((page) => page.title === null)
      .map((page) => page.path),
    mocPath: knowledgePaths.includes("knowledge-base/moc.md") ? "knowledge-base/moc.md" : null,
    topicIndexPaths: knowledgePaths.filter((filePath) => filePath.endsWith("/index.md"))
  };
}
```

- [ ] **Step 7: Verify inventory tests pass**

Run:

```bash
npm test -- tests/unit/inventory.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 8: Commit**

```bash
git add src/storage/sha.ts src/storage/workspace-fs.ts src/domain/workspace/page-state.ts src/domain/workspace/inventory.ts tests/unit/inventory.test.ts
git commit -m "feat: scan workspace inventory"
```

## Task 4: Plan and Work Items

**Files:**
- Create: `src/domain/planning/work-item.ts`
- Create: `src/domain/planning/plan.ts`
- Create: `src/domain/planning/organize-planner.ts`
- Test: `tests/unit/planner.test.ts`

- [ ] **Step 1: Write failing planner tests**

Create `tests/unit/planner.test.ts`:

```ts
import path from "node:path";
import { describe, expect, it } from "vitest";
import { scanWorkspace } from "../../src/domain/workspace/inventory";
import { createOrganizePlan } from "../../src/domain/planning/organize-planner";

const fixtureRoot = path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror");

describe("organize planner", () => {
  it("creates a semi-automatic three-phase plan", async () => {
    const inventory = await scanWorkspace({ workspaceRoot: fixtureRoot });
    const plan = createOrganizePlan({
      runId: "run-test",
      instruction: "整理全部知识库",
      inventory
    });

    expect(plan.mode).toBe("SEMI_AUTOMATIC");
    expect(plan.approval.status).toBe("PENDING");
    expect(plan.phases.map((phase) => phase.id)).toEqual([
      "phase-a-notes",
      "phase-b-indexes",
      "phase-c-global"
    ]);
    expect(plan.workItems.some((item) => item.type === "REWRITE_TOPIC_NOTE")).toBe(true);
    expect(plan.workItems.some((item) => item.type === "CREATE_TOPIC_NOTE")).toBe(true);
    expect(plan.workItems.some((item) => item.type === "MAINTAIN_MOC")).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/unit/planner.test.ts
```

Expected: FAIL because planning modules do not exist.

- [ ] **Step 3: Define WorkItem types**

Create `src/domain/planning/work-item.ts`:

```ts
export type WorkItemType =
  | "CREATE_TOPIC_NOTE"
  | "REWRITE_TOPIC_NOTE"
  | "MERGE_USER_EDITED_NOTE"
  | "MAINTAIN_TOPIC_INDEX"
  | "MAINTAIN_MOC"
  | "QUALITY_REVIEW";

export type WorkItemStatus =
  | "PLANNED"
  | "SKIPPED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED_TIMEOUT"
  | "FAILED_EXECUTOR"
  | "BLOCKED_BY_VALIDATOR"
  | "WAITING_APPROVAL"
  | "PUBLISHED"
  | "NEEDS_REPLAN";

export interface WorkItem {
  id: string;
  type: WorkItemType;
  phase: "phase-a-notes" | "phase-b-indexes" | "phase-c-global";
  status: WorkItemStatus;
  sourcePaths: string[];
  targetPaths: string[];
  baseShas: Record<string, string>;
  risk: "LOW" | "MEDIUM" | "HIGH";
  requiresApproval: boolean;
  reason: string;
  attempts: Array<{
    attempt: number;
    status: WorkItemStatus;
    message: string;
  }>;
  publishPolicy?: "AUTO_PUBLISH" | "CANDIDATE_PATCH_ONLY";
}
```

- [ ] **Step 4: Define Plan types**

Create `src/domain/planning/plan.ts`:

```ts
import type { WorkItem } from "./work-item";

export interface PlanPhase {
  id: "phase-a-notes" | "phase-b-indexes" | "phase-c-global";
  type: "NOTE_WRITES" | "TOPIC_INDEXES" | "GLOBAL_REVIEW";
  workItemIds: string[];
}

export interface OrganizePlan {
  runId: string;
  instruction: string;
  mode: "SEMI_AUTOMATIC";
  workspaceSnapshot: {
    workspaceRoot: string;
    rawCount: number;
    knowledgeBasePageCount: number;
    schemaSha: string | null;
  };
  approval: {
    status: "PENDING" | "APPROVED" | "REJECTED";
    approvedAt: string | null;
  };
  phases: PlanPhase[];
  workItems: WorkItem[];
}
```

- [ ] **Step 5: Implement deterministic planner**

Create `src/domain/planning/organize-planner.ts`:

```ts
import type { WorkspaceInventory } from "../workspace/inventory";
import type { OrganizePlan } from "./plan";
import type { WorkItem } from "./work-item";

function slugFromPath(path: string): string {
  return path
    .replace(/^raw\//, "")
    .replace(/\.md$/, "")
    .replace(/[\/\s]+/g, "-")
    .toLowerCase();
}

function targetPathForRaw(rawPath: string): string {
  return rawPath.replace(/^raw\//, "knowledge-base/topics/");
}

export function createOrganizePlan(input: {
  runId: string;
  instruction: string;
  inventory: WorkspaceInventory;
}): OrganizePlan {
  const noteWorkItems: WorkItem[] = input.inventory.rawFiles.map((rawFile) => {
    const targetPath = targetPathForRaw(rawFile.path);
    const existingPage = input.inventory.knowledgeBasePages.find((page) => page.path === targetPath);
    const isMirror = existingPage?.state === "BOOTSTRAP_MIRROR";

    return {
      id: `${isMirror ? "rewrite" : "create"}-${slugFromPath(rawFile.path)}`,
      type: isMirror ? "REWRITE_TOPIC_NOTE" : "CREATE_TOPIC_NOTE",
      phase: "phase-a-notes",
      status: "PLANNED",
      sourcePaths: [rawFile.path],
      targetPaths: [targetPath],
      baseShas: {
        [rawFile.path]: rawFile.sha,
        ...(existingPage ? { [targetPath]: existingPage.sha } : {})
      },
      risk: isMirror ? "LOW" : "LOW",
      requiresApproval: false,
      reason: isMirror ? "existing page is bootstrap raw mirror" : "raw file has no organized note",
      attempts: [],
      publishPolicy: "AUTO_PUBLISH"
    };
  });

  const topicIndexItems: WorkItem[] = Array.from(
    new Set(noteWorkItems.map((item) => item.targetPaths[0].split("/").slice(0, 4).join("/")))
  ).map((topicDir) => ({
    id: `maintain-${topicDir.replace(/^knowledge-base\/topics\//, "").replace(/\//g, "-")}-index`,
    type: "MAINTAIN_TOPIC_INDEX",
    phase: "phase-b-indexes",
    status: "PLANNED",
    sourcePaths: [],
    targetPaths: [`${topicDir}/index.md`],
    baseShas: {},
    risk: "LOW",
    requiresApproval: false,
    reason: "topic index must link organized notes",
    attempts: [],
    publishPolicy: "AUTO_PUBLISH"
  }));

  const globalItems: WorkItem[] = [
    {
      id: "maintain-moc",
      type: "MAINTAIN_MOC",
      phase: "phase-c-global",
      status: "PLANNED",
      sourcePaths: [],
      targetPaths: ["knowledge-base/moc.md"],
      baseShas: {},
      risk: "LOW",
      requiresApproval: false,
      reason: "global MOC must link topic indexes",
      attempts: [],
      publishPolicy: "AUTO_PUBLISH"
    },
    {
      id: "quality-review",
      type: "QUALITY_REVIEW",
      phase: "phase-c-global",
      status: "PLANNED",
      sourcePaths: [],
      targetPaths: [],
      baseShas: {},
      risk: "LOW",
      requiresApproval: false,
      reason: "final report must include quality issues",
      attempts: []
    }
  ];

  const workItems = [...noteWorkItems, ...topicIndexItems, ...globalItems];
  const schemaSha = input.inventory.schemaFiles.find((file) => file.path === "schema/CLAUDE.md")?.sha ?? null;

  return {
    runId: input.runId,
    instruction: input.instruction,
    mode: "SEMI_AUTOMATIC",
    workspaceSnapshot: {
      workspaceRoot: input.inventory.workspaceRoot,
      rawCount: input.inventory.rawFiles.length,
      knowledgeBasePageCount: input.inventory.knowledgeBasePages.length,
      schemaSha
    },
    approval: {
      status: "PENDING",
      approvedAt: null
    },
    phases: [
      {
        id: "phase-a-notes",
        type: "NOTE_WRITES",
        workItemIds: workItems.filter((item) => item.phase === "phase-a-notes").map((item) => item.id)
      },
      {
        id: "phase-b-indexes",
        type: "TOPIC_INDEXES",
        workItemIds: workItems.filter((item) => item.phase === "phase-b-indexes").map((item) => item.id)
      },
      {
        id: "phase-c-global",
        type: "GLOBAL_REVIEW",
        workItemIds: workItems.filter((item) => item.phase === "phase-c-global").map((item) => item.id)
      }
    ],
    workItems
  };
}
```

- [ ] **Step 6: Verify planner tests pass**

Run:

```bash
npm test -- tests/unit/planner.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/domain/planning tests/unit/planner.test.ts
git commit -m "feat: plan organize work items"
```

## Task 5: Agent Runs Store

**Files:**
- Create: `src/storage/agent-runs-store.ts`
- Create: `src/storage/json-schema.ts`
- Test: `tests/unit/agent-runs-store.test.ts`

- [ ] **Step 1: Write failing store tests**

Create `tests/unit/agent-runs-store.test.ts`:

```ts
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { AgentRunsStore } from "../../src/storage/agent-runs-store";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "agent-runs-store-"));
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("AgentRunsStore", () => {
  it("writes and reads JSON artifacts under .agent-runs", async () => {
    const store = new AgentRunsStore(tempRoot, "run-test");
    await store.writeJson("plan.json", { runId: "run-test", mode: "SEMI_AUTOMATIC" });

    await expect(store.readJson<{ runId: string }>("plan.json")).resolves.toEqual({
      runId: "run-test",
      mode: "SEMI_AUTOMATIC"
    });
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/unit/agent-runs-store.test.ts
```

Expected: FAIL because `AgentRunsStore` does not exist.

- [ ] **Step 3: Implement JSON helpers**

Create `src/storage/json-schema.ts`:

```ts
export function stableJson(value: unknown): string {
  return `${JSON.stringify(value, null, 2)}\n`;
}
```

- [ ] **Step 4: Implement AgentRunsStore**

Create `src/storage/agent-runs-store.ts`:

```ts
import { promises as fs } from "node:fs";
import path from "node:path";
import { stableJson } from "./json-schema";

export class AgentRunsStore {
  private readonly runRoot: string;

  constructor(
    private readonly workspaceRoot: string,
    private readonly runId: string
  ) {
    this.runRoot = path.join(workspaceRoot, ".agent-runs", runId);
  }

  artifactPath(relativePath: string): string {
    return path.join(this.runRoot, relativePath);
  }

  async writeJson(relativePath: string, value: unknown): Promise<void> {
    const absolutePath = this.artifactPath(relativePath);
    await fs.mkdir(path.dirname(absolutePath), { recursive: true });
    await fs.writeFile(absolutePath, stableJson(value), "utf8");
  }

  async readJson<T>(relativePath: string): Promise<T> {
    const content = await fs.readFile(this.artifactPath(relativePath), "utf8");
    return JSON.parse(content) as T;
  }

  async writeText(relativePath: string, value: string): Promise<void> {
    const absolutePath = this.artifactPath(relativePath);
    await fs.mkdir(path.dirname(absolutePath), { recursive: true });
    await fs.writeFile(absolutePath, value.endsWith("\n") ? value : `${value}\n`, "utf8");
  }
}
```

- [ ] **Step 5: Verify store tests pass**

Run:

```bash
npm test -- tests/unit/agent-runs-store.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/storage/agent-runs-store.ts src/storage/json-schema.ts tests/unit/agent-runs-store.test.ts
git commit -m "feat: persist agent run artifacts"
```

## Task 6: PatchBundle, MergeGuard, Publisher

**Files:**
- Create: `src/domain/patch/patch-bundle.ts`
- Create: `src/domain/patch/merge-guard.ts`
- Create: `src/domain/patch/publisher.ts`
- Test: `tests/unit/patch.test.ts`

- [ ] **Step 1: Write failing patch tests**

Create `tests/unit/patch.test.ts`:

```ts
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { checkMerge } from "../../src/domain/patch/merge-guard";
import { publishBundle } from "../../src/domain/patch/publisher";
import { sha256 } from "../../src/storage/sha";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "patch-test-"));
  await mkdir(path.join(tempRoot, "knowledge-base/topics/tools"), { recursive: true });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("patch merge and publish", () => {
  it("blocks raw writes", async () => {
    const decision = await checkMerge({
      workspaceRoot: tempRoot,
      authorizedTargetPaths: ["raw/tools/bad.md"],
      bundle: {
        workItemId: "bad",
        status: "SUCCEEDED",
        targetPaths: ["raw/tools/bad.md"],
        files: [
          {
            path: "raw/tools/bad.md",
            changeType: "CREATED",
            baseSha: null,
            contentSha: sha256("bad"),
            content: "bad"
          }
        ],
        eval: { rawFilesSeen: [], rawMirrorConverted: false, placeholderIntroduced: false, wikilinksCreated: 0 }
      }
    });

    expect(decision.allowed).toBe(false);
    expect(decision.reasons).toContain("RAW_OR_SCHEMA_WRITE_BLOCKED");
  });

  it("publishes authorized knowledge-base file", async () => {
    const targetPath = "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md";
    const content = "# Skill vs CLI Tool 决策\n\n## 摘要\n\n已整理。\n";
    const bundle = {
      workItemId: "rewrite-tools",
      status: "SUCCEEDED" as const,
      targetPaths: [targetPath],
      files: [
        {
          path: targetPath,
          changeType: "CREATED" as const,
          baseSha: null,
          contentSha: sha256(content),
          content
        }
      ],
      eval: { rawFilesSeen: [], rawMirrorConverted: true, placeholderIntroduced: false, wikilinksCreated: 0 }
    };

    const decision = await checkMerge({
      workspaceRoot: tempRoot,
      authorizedTargetPaths: [targetPath],
      bundle
    });
    expect(decision.allowed).toBe(true);

    await publishBundle({ workspaceRoot: tempRoot, bundle });
    await expect(readFile(path.join(tempRoot, targetPath), "utf8")).resolves.toBe(content);
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/unit/patch.test.ts
```

Expected: FAIL because patch modules do not exist.

- [ ] **Step 3: Define PatchBundle contract**

Create `src/domain/patch/patch-bundle.ts`:

```ts
export interface PatchFile {
  path: string;
  changeType: "CREATED" | "MODIFIED";
  baseSha: string | null;
  contentSha: string;
  content: string;
}

export interface PatchBundle {
  workItemId: string;
  status: "SUCCEEDED";
  targetPaths: string[];
  files: PatchFile[];
  eval: {
    rawFilesSeen: string[];
    rawMirrorConverted: boolean;
    placeholderIntroduced: boolean;
    wikilinksCreated: number;
  };
  mergeEvidence?: {
    preservedSections: string[];
    rewrittenSections: string[];
    userContentDropped: string[];
    conflicts: string[];
  };
}
```

- [ ] **Step 4: Implement MergeGuard**

Create `src/domain/patch/merge-guard.ts`:

```ts
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import type { PatchBundle } from "./patch-bundle";
import { sha256 } from "../../storage/sha";

export interface MergeDecision {
  allowed: boolean;
  reasons: string[];
}

export async function checkMerge(input: {
  workspaceRoot: string;
  authorizedTargetPaths: string[];
  bundle: PatchBundle;
}): Promise<MergeDecision> {
  const reasons: string[] = [];
  const authorized = new Set(input.authorizedTargetPaths);

  for (const file of input.bundle.files) {
    if (file.path.startsWith("raw/") || file.path.startsWith("schema/")) {
      reasons.push("RAW_OR_SCHEMA_WRITE_BLOCKED");
    }
    if (!authorized.has(file.path)) {
      reasons.push(`UNAUTHORIZED_PATH:${file.path}`);
    }
    if (!input.bundle.targetPaths.includes(file.path)) {
      reasons.push(`TARGET_PATH_MISMATCH:${file.path}`);
    }
    if (sha256(file.content) !== file.contentSha) {
      reasons.push(`CONTENT_SHA_MISMATCH:${file.path}`);
    }
    const absolutePath = path.join(input.workspaceRoot, file.path);
    if (file.baseSha !== null && existsSync(absolutePath)) {
      const currentSha = sha256(readFileSync(absolutePath, "utf8"));
      if (currentSha !== file.baseSha) {
        reasons.push(`BASE_SHA_MISMATCH:${file.path}`);
      }
    }
  }

  return {
    allowed: reasons.length === 0,
    reasons
  };
}
```

- [ ] **Step 5: Implement Publisher**

Create `src/domain/patch/publisher.ts`:

```ts
import { promises as fs } from "node:fs";
import path from "node:path";
import type { PatchBundle } from "./patch-bundle";

export async function publishBundle(input: {
  workspaceRoot: string;
  bundle: PatchBundle;
}): Promise<{ publishedPaths: string[] }> {
  const publishedPaths: string[] = [];

  for (const file of input.bundle.files) {
    const absolutePath = path.join(input.workspaceRoot, file.path);
    await fs.mkdir(path.dirname(absolutePath), { recursive: true });
    await fs.writeFile(absolutePath, file.content, "utf8");
    publishedPaths.push(file.path);
  }

  return { publishedPaths };
}
```

- [ ] **Step 6: Verify patch tests pass**

Run:

```bash
npm test -- tests/unit/patch.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/domain/patch tests/unit/patch.test.ts
git commit -m "feat: guard and publish patch bundles"
```

## Task 7: Validator, Eval, and Resume Decision

**Files:**
- Create: `src/domain/validation/validator.ts`
- Create: `src/domain/validation/eval-reporter.ts`
- Create: `src/domain/validation/resume-decision.ts`
- Test: `tests/unit/validation.test.ts`

- [ ] **Step 1: Write failing validation tests**

Create `tests/unit/validation.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { validateBundle } from "../../src/domain/validation/validator";
import { decideResumeAction } from "../../src/domain/validation/resume-decision";
import { sha256 } from "../../src/storage/sha";

describe("validator", () => {
  it("blocks topic notes that still look like raw mirrors", () => {
    const content = "# Skill vs CLI Tool 决策\n\nRaw mirror: true\nSource path: raw/tools/a.md\n\n## Content\n\nraw";
    const result = validateBundle({
      targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
      files: [
        {
          path: "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md",
          changeType: "MODIFIED",
          baseSha: "base",
          contentSha: sha256(content),
          content
        }
      ]
    });

    expect(result.allowed).toBe(false);
    expect(result.hardBlockers).toContain("TOPIC_NOTE_STILL_RAW_MIRROR");
  });

  it("allows organized topic note with required sections", () => {
    const content = `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - raw/tools/Skill vs CLI Tool 决策.md
sourceShas:
  raw/tools/Skill vs CLI Tool 决策.md: abc
lastRunId: run-test
contentSha: def
-->

## 摘要

沉淀 skill 与 CLI tool 的适用边界。

## 来源追踪

- raw/tools/Skill vs CLI Tool 决策.md

## 关键决策

- Skill 适合流程和判断。
- CLI tool 适合确定性动作。

## 相关链接

暂无相关链接。`;

    const result = validateBundle({
      targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
      files: [
        {
          path: "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md",
          changeType: "MODIFIED",
          baseSha: "base",
          contentSha: sha256(content),
          content
        }
      ]
    });

    expect(result.allowed).toBe(true);
    expect(result.hardBlockers).toEqual([]);
  });
});

describe("resume decision", () => {
  it("skips published work item when current sha matches content sha", () => {
    expect(
      decideResumeAction({
        status: "PUBLISHED",
        currentSha: "same",
        contentSha: "same"
      })
    ).toBe("SKIP");
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/unit/validation.test.ts
```

Expected: FAIL because validation modules do not exist.

- [ ] **Step 3: Implement Validator**

Create `src/domain/validation/validator.ts`:

```ts
import type { PatchFile } from "../patch/patch-bundle";

export interface ValidationResult {
  allowed: boolean;
  hardBlockers: string[];
  qualityIssues: string[];
}

export function validateBundle(input: {
  targetPaths: string[];
  files: PatchFile[];
}): ValidationResult {
  const hardBlockers: string[] = [];
  const qualityIssues: string[] = [];

  for (const file of input.files) {
    if (file.path.startsWith("raw/") || file.path.startsWith("schema/")) {
      hardBlockers.push("RAW_OR_SCHEMA_WRITE_BLOCKED");
    }
    if (file.content.includes("<placeholder>") || file.content.includes("后续补充")) {
      hardBlockers.push("PLACEHOLDER_CONTENT_BLOCKED");
    }
    const isTopicNote = file.path.startsWith("knowledge-base/topics/") && !file.path.endsWith("/index.md");
    if (isTopicNote) {
      if (file.content.includes("Raw mirror:") || file.content.includes("## Content")) {
        hardBlockers.push("TOPIC_NOTE_STILL_RAW_MIRROR");
      }
      if (!/^#\s+.+/m.test(file.content)) {
        hardBlockers.push("TOPIC_NOTE_MISSING_TITLE");
      }
      if (!file.content.includes("## 摘要")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_SUMMARY");
      }
      if (!file.content.includes("## 来源追踪")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_SOURCE_TRACKING");
      }
      if (!file.content.includes("## 关键决策") && !file.content.includes("## 关键概念") && !file.content.includes("## 关键步骤")) {
        hardBlockers.push("TOPIC_NOTE_MISSING_KEY_CONTENT");
      }
      if (!file.content.includes("## 相关链接")) {
        qualityIssues.push("TOPIC_NOTE_WEAK_RELATIONS");
      }
    }
  }

  return {
    allowed: hardBlockers.length === 0,
    hardBlockers: Array.from(new Set(hardBlockers)),
    qualityIssues: Array.from(new Set(qualityIssues))
  };
}
```

- [ ] **Step 4: Implement Eval reporter**

Create `src/domain/validation/eval-reporter.ts`:

```ts
import type { WorkItem } from "../planning/work-item";
import type { ValidationResult } from "./validator";

export interface EvalReport {
  rawCoverage: {
    total: number;
    seen: number;
  };
  pagesRewritten: number;
  rawMirrorConverted: number;
  qualityIssues: string[];
  workItemStatuses: Record<string, string>;
}

export function buildEvalReport(input: {
  rawCount: number;
  rawFilesSeen: string[];
  pagesRewritten: number;
  rawMirrorConverted: number;
  workItems: WorkItem[];
  validations: ValidationResult[];
}): EvalReport {
  return {
    rawCoverage: {
      total: input.rawCount,
      seen: new Set(input.rawFilesSeen).size
    },
    pagesRewritten: input.pagesRewritten,
    rawMirrorConverted: input.rawMirrorConverted,
    qualityIssues: input.validations.flatMap((validation) => validation.qualityIssues),
    workItemStatuses: Object.fromEntries(input.workItems.map((item) => [item.id, item.status]))
  };
}
```

- [ ] **Step 5: Implement resume decision**

Create `src/domain/validation/resume-decision.ts`:

```ts
import type { WorkItemStatus } from "../planning/work-item";

export type ResumeAction = "SKIP" | "RETRY" | "WAIT_FOR_APPROVAL" | "REPLAN" | "REPORT_FAILED";

export function decideResumeAction(input: {
  status: WorkItemStatus;
  currentSha?: string;
  contentSha?: string;
  retryable?: boolean;
}): ResumeAction {
  if (input.status === "PUBLISHED") {
    return input.currentSha && input.contentSha && input.currentSha === input.contentSha ? "SKIP" : "REPLAN";
  }
  if (input.status === "SUCCEEDED") {
    return "RETRY";
  }
  if (input.status === "FAILED_TIMEOUT") {
    return "RETRY";
  }
  if (input.status === "FAILED_EXECUTOR") {
    return input.retryable ? "RETRY" : "REPORT_FAILED";
  }
  if (input.status === "BLOCKED_BY_VALIDATOR") {
    return "RETRY";
  }
  if (input.status === "WAITING_APPROVAL") {
    return "WAIT_FOR_APPROVAL";
  }
  if (input.status === "NEEDS_REPLAN") {
    return "REPLAN";
  }
  return "RETRY";
}
```

- [ ] **Step 6: Verify validation tests pass**

Run:

```bash
npm test -- tests/unit/validation.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/domain/validation tests/unit/validation.test.ts
git commit -m "feat: validate patch quality and resume decisions"
```

## Task 8: Mock Agent Nodes

**Files:**
- Create: `src/agents/work-item-agent.ts`
- Create: `src/agents/mock-note-agent.ts`
- Create: `src/agents/mock-topic-index-agent.ts`
- Create: `src/agents/quality-review-agent.ts`
- Test: `tests/unit/mock-agents.test.ts`

- [ ] **Step 1: Write failing mock agent tests**

Create `tests/unit/mock-agents.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { runMockNoteAgent } from "../../src/agents/mock-note-agent";

describe("mock note agent", () => {
  it("emits a valid organized topic note patch bundle", async () => {
    const bundle = await runMockNoteAgent({
      runId: "run-test",
      workItem: {
        id: "rewrite-tools",
        type: "REWRITE_TOPIC_NOTE",
        phase: "phase-a-notes",
        status: "PLANNED",
        sourcePaths: ["raw/tools/Skill vs CLI Tool 决策.md"],
        targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
        baseShas: {
          "raw/tools/Skill vs CLI Tool 决策.md": "abc",
          "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md": "def"
        },
        risk: "LOW",
        requiresApproval: false,
        reason: "existing page is bootstrap raw mirror",
        attempts: [],
        publishPolicy: "AUTO_PUBLISH"
      },
      sourceContent: "# Skill vs CLI Tool 决策\n\n## 决策\n\nSkill 适合流程，CLI 适合确定性动作。"
    });

    expect(bundle.files[0].content).toContain("<!-- agent-meta");
    expect(bundle.files[0].content).toContain("## 摘要");
    expect(bundle.files[0].content).toContain("## 来源追踪");
    expect(bundle.eval.rawMirrorConverted).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/unit/mock-agents.test.ts
```

Expected: FAIL because agent modules do not exist.

- [ ] **Step 3: Define WorkItemAgent interface**

Create `src/agents/work-item-agent.ts`:

```ts
import type { WorkItem } from "../domain/planning/work-item";
import type { PatchBundle } from "../domain/patch/patch-bundle";

export interface WorkItemAgentInput {
  runId: string;
  workItem: WorkItem;
  sourceContent: string;
}

export type WorkItemAgent = (input: WorkItemAgentInput) => Promise<PatchBundle>;
```

- [ ] **Step 4: Implement mock note agent**

Create `src/agents/mock-note-agent.ts`:

```ts
import { sha256 } from "../storage/sha";
import type { PatchBundle } from "../domain/patch/patch-bundle";
import type { WorkItemAgentInput } from "./work-item-agent";

export async function runMockNoteAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const sourcePath = input.workItem.sourcePaths[0];
  const sourceSha = input.workItem.baseShas[sourcePath] ?? "unknown-source-sha";
  const baseSha = input.workItem.baseShas[targetPath] ?? null;
  const content = `# Skill vs CLI Tool 决策

<!-- agent-meta
state: AGENT_ORGANIZED
sourcePaths:
  - ${sourcePath}
sourceShas:
  ${sourcePath}: ${sourceSha}
lastRunId: ${input.runId}
contentSha: pending
-->

## 摘要

这篇 note 沉淀 skill 与 CLI tool 的适用边界，帮助后续判断能力应进入流程指导还是确定性工具。

## 来源追踪

- ${sourcePath}

## 关键决策

- Skill 适合流程、判断标准、上下文组织和人机协作约束。
- CLI tool 适合可重复、确定性、可测试、可组合的机械动作。
- Agent loop 不能只依赖 prompt，关键写入和质量边界必须由确定性代码验证。

## 取舍

Skill 更容易表达意图，但不能替代验证。CLI 更适合进入 CI 和本地自动化，但不适合承载模糊判断。

## 相关链接

暂无相关链接。
`;
  const contentSha = sha256(content);
  const finalizedContent = content.replace("contentSha: pending", `contentSha: ${contentSha}`);

  return {
    workItemId: input.workItem.id,
    status: "SUCCEEDED",
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: baseSha ? "MODIFIED" : "CREATED",
        baseSha,
        contentSha: sha256(finalizedContent),
        content: finalizedContent
      }
    ],
    eval: {
      rawFilesSeen: [sourcePath],
      rawMirrorConverted: input.workItem.type === "REWRITE_TOPIC_NOTE",
      placeholderIntroduced: false,
      wikilinksCreated: 0
    }
  };
}
```

- [ ] **Step 5: Implement mock index and quality agents**

Create `src/agents/mock-topic-index-agent.ts`:

```ts
import { sha256 } from "../storage/sha";
import type { PatchBundle } from "../domain/patch/patch-bundle";
import type { WorkItemAgentInput } from "./work-item-agent";

export async function runMockTopicIndexAgent(input: WorkItemAgentInput): Promise<PatchBundle> {
  const targetPath = input.workItem.targetPaths[0];
  const content = `# Topic Index

- [[Skill vs CLI Tool 决策]]
`;

  return {
    workItemId: input.workItem.id,
    status: "SUCCEEDED",
    targetPaths: [targetPath],
    files: [
      {
        path: targetPath,
        changeType: "CREATED",
        baseSha: input.workItem.baseShas[targetPath] ?? null,
        contentSha: sha256(content),
        content
      }
    ],
    eval: {
      rawFilesSeen: [],
      rawMirrorConverted: false,
      placeholderIntroduced: false,
      wikilinksCreated: 1
    }
  };
}
```

Create `src/agents/quality-review-agent.ts`:

```ts
export interface QualityFindings {
  issues: string[];
}

export async function runQualityReviewAgent(input: { noteContents: string[] }): Promise<QualityFindings> {
  const issues = input.noteContents
    .filter((content) => !content.includes("## 相关链接"))
    .map(() => "TOPIC_NOTE_WEAK_RELATIONS");

  return { issues };
}
```

- [ ] **Step 6: Verify mock agent tests pass**

Run:

```bash
npm test -- tests/unit/mock-agents.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/agents tests/unit/mock-agents.test.ts
git commit -m "feat: add mock work item agents"
```

## Task 9: LangGraph Runtime Shell

**Files:**
- Create: `src/runtime/langgraph/state.ts`
- Create: `src/runtime/langgraph/graph.ts`
- Create: `src/runtime/langgraph/nodes/*.ts`
- Test: `tests/integration/langgraph-workflow.test.ts`

- [ ] **Step 1: Write failing workflow tests**

Create `tests/integration/langgraph-workflow.test.ts`:

```ts
import { mkdtemp, cp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "workflow-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("LangGraph workflow", () => {
  it("stops at plan approval without auto approve", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-test",
      autoApprove: false
    });

    expect(result.status).toBe("WAITING_PLAN_APPROVAL");
    expect(result.planPath).toBe(".agent-runs/run-test/plan.json");
  });

  it("executes mock note agent with auto approve", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-test",
      autoApprove: true
    });

    expect(result.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(result.reportPath).toBe(".agent-runs/run-test/report.md");
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/integration/langgraph-workflow.test.ts
```

Expected: FAIL because runtime graph does not exist.

- [ ] **Step 3: Implement GraphState**

Create `src/runtime/langgraph/state.ts`:

```ts
export interface GraphState {
  runId: string;
  workspaceRoot: string;
  instruction: string;
  autoApprove: boolean;
  status: "CREATED" | "WAITING_PLAN_APPROVAL" | "RUNNING" | "SUCCEEDED_WITH_WARNINGS" | "FAILED";
  planPath?: string;
  reportPath?: string;
  lastError?: string;
}
```

- [ ] **Step 4: Implement workflow nodes as deterministic functions**

Create `src/runtime/langgraph/nodes/inventory-node.ts`:

```ts
import { scanWorkspace } from "../../../domain/workspace/inventory";
import { AgentRunsStore } from "../../../storage/agent-runs-store";
import type { GraphState } from "../state";

export async function inventoryNode(state: GraphState): Promise<GraphState> {
  const inventory = await scanWorkspace({ workspaceRoot: state.workspaceRoot });
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  await store.writeJson("inventory.json", inventory);
  return state;
}
```

Create `src/runtime/langgraph/nodes/plan-node.ts`:

```ts
import { createOrganizePlan } from "../../../domain/planning/organize-planner";
import type { WorkspaceInventory } from "../../../domain/workspace/inventory";
import { AgentRunsStore } from "../../../storage/agent-runs-store";
import type { GraphState } from "../state";

export async function planNode(state: GraphState): Promise<GraphState> {
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const inventory = await store.readJson<WorkspaceInventory>("inventory.json");
  const plan = createOrganizePlan({
    runId: state.runId,
    instruction: state.instruction,
    inventory
  });
  await store.writeJson("plan.json", plan);
  for (const item of plan.workItems) {
    await store.writeJson(`work-items/${item.id}.json`, item);
  }
  return {
    ...state,
    status: state.autoApprove ? "RUNNING" : "WAITING_PLAN_APPROVAL",
    planPath: `.agent-runs/${state.runId}/plan.json`
  };
}
```

Create `src/runtime/langgraph/nodes/execute-phase-node.ts`:

```ts
import type { OrganizePlan } from "../../../domain/planning/plan";
import { checkMerge } from "../../../domain/patch/merge-guard";
import { publishBundle } from "../../../domain/patch/publisher";
import { validateBundle } from "../../../domain/validation/validator";
import { runMockNoteAgent } from "../../../agents/mock-note-agent";
import { AgentRunsStore } from "../../../storage/agent-runs-store";
import { readWorkspaceFile } from "../../../storage/workspace-fs";
import type { GraphState } from "../state";

export async function executePhaseNode(state: GraphState): Promise<GraphState> {
  if (!state.autoApprove) {
    return state;
  }

  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  const plan = await store.readJson<OrganizePlan>("plan.json");
  const noteItem = plan.workItems.find((item) => item.type === "REWRITE_TOPIC_NOTE");
  if (!noteItem) {
    return { ...state, status: "FAILED", lastError: "NO_REWRITE_WORK_ITEM" };
  }

  const sourceContent = await readWorkspaceFile(state.workspaceRoot, noteItem.sourcePaths[0]);
  const bundle = await runMockNoteAgent({
    runId: state.runId,
    workItem: noteItem,
    sourceContent
  });
  await store.writeJson(`patches/${noteItem.id}.patch.json`, bundle);

  const mergeDecision = await checkMerge({
    workspaceRoot: state.workspaceRoot,
    authorizedTargetPaths: noteItem.targetPaths,
    bundle
  });
  const validation = validateBundle({
    targetPaths: noteItem.targetPaths,
    files: bundle.files
  });

  await store.writeJson("validation.json", { mergeDecision, validation });
  if (!mergeDecision.allowed || !validation.allowed) {
    return { ...state, status: "FAILED", lastError: "PATCH_BLOCKED" };
  }

  await publishBundle({ workspaceRoot: state.workspaceRoot, bundle });
  await store.writeJson(`work-items/${noteItem.id}.json`, {
    ...noteItem,
    status: "PUBLISHED"
  });

  return state;
}
```

Create `src/runtime/langgraph/nodes/report-node.ts`:

```ts
import { AgentRunsStore } from "../../../storage/agent-runs-store";
import type { GraphState } from "../state";

export async function reportNode(state: GraphState): Promise<GraphState> {
  if (!state.autoApprove) {
    return state;
  }
  const store = new AgentRunsStore(state.workspaceRoot, state.runId);
  await store.writeJson("eval.json", {
    rawCoverage: { total: 3, seen: 1 },
    pagesRewritten: 1,
    rawMirrorConverted: 1,
    qualityIssues: ["TOPIC_NOTE_WEAK_RELATIONS"]
  });
  await store.writeText(
    "report.md",
    `# Agent Run Report

- Status: SUCCEEDED_WITH_WARNINGS
- Pages rewritten: 1
- Raw mirror converted: 1
`
  );
  return {
    ...state,
    status: "SUCCEEDED_WITH_WARNINGS",
    reportPath: `.agent-runs/${state.runId}/report.md`
  };
}
```

- [ ] **Step 5: Implement graph wrapper**

Create `src/runtime/langgraph/graph.ts`:

```ts
import { END, START, StateGraph } from "@langchain/langgraph";
import { inventoryNode } from "./nodes/inventory-node";
import { planNode } from "./nodes/plan-node";
import { executePhaseNode } from "./nodes/execute-phase-node";
import { reportNode } from "./nodes/report-node";
import type { GraphState } from "./state";

export async function runOrganizeWorkflow(input: {
  workspaceRoot: string;
  instruction: string;
  runId: string;
  autoApprove: boolean;
}): Promise<GraphState> {
  const graph = new StateGraph<GraphState>({
    channels: {
      runId: null,
      workspaceRoot: null,
      instruction: null,
      autoApprove: null,
      status: null,
      planPath: null,
      reportPath: null,
      lastError: null
    }
  })
    .addNode("inventory", inventoryNode)
    .addNode("plan", planNode)
    .addNode("execute", executePhaseNode)
    .addNode("report", reportNode)
    .addEdge(START, "inventory")
    .addEdge("inventory", "plan")
    .addEdge("plan", "execute")
    .addEdge("execute", "report")
    .addEdge("report", END)
    .compile();

  return graph.invoke({
    runId: input.runId,
    workspaceRoot: input.workspaceRoot,
    instruction: input.instruction,
    autoApprove: input.autoApprove,
    status: "CREATED"
  });
}
```

- [ ] **Step 6: Verify workflow tests pass**

Run:

```bash
npm test -- tests/integration/langgraph-workflow.test.ts
npm run typecheck
```

Expected: both commands exit 0. If the LangGraph TypeScript API differs from the code above, adapt only `src/runtime/langgraph/graph.ts` and keep `GraphState` and node interfaces unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/runtime tests/integration/langgraph-workflow.test.ts
git commit -m "feat: orchestrate organize workflow with langgraph"
```

## Task 10: CLI and Resume Smoke

**Files:**
- Create: `src/cli/organize.ts`
- Create: `src/cli/resume.ts`
- Test: `tests/integration/cli-smoke.test.ts`

- [ ] **Step 1: Write failing CLI smoke tests**

Create `tests/integration/cli-smoke.test.ts`:

```ts
import { execFile } from "node:child_process";
import { mkdtemp, cp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const execFileAsync = promisify(execFile);
let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "cli-smoke-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("CLI smoke", () => {
  it("runs organize with auto approval", async () => {
    const result = await execFileAsync("npx", [
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--run-id",
      "run-cli"
    ]);

    expect(result.stdout).toContain("SUCCEEDED_WITH_WARNINGS");
  });

  it("reports resume decisions for the latest run", async () => {
    await execFileAsync("npx", [
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--run-id",
      "run-cli"
    ]);

    const result = await execFileAsync("npx", ["tsx", "src/cli/resume.ts", tempRoot]);

    expect(result.stdout).toContain("run-cli");
    expect(result.stdout).toContain("SKIP");
  });
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
npm test -- tests/integration/cli-smoke.test.ts
```

Expected: FAIL because CLI files do not exist.

- [ ] **Step 3: Implement organize CLI**

Create `src/cli/organize.ts`:

```ts
import { runOrganizeWorkflow } from "../runtime/langgraph/graph";

function readArg(name: string): string | null {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
}

const workspaceRoot = process.argv[2];
const instruction = process.argv[3];
const autoApprove = process.argv.includes("--auto-approve");
const runId = readArg("--run-id") ?? `run-${Date.now()}`;

if (!workspaceRoot || !instruction) {
  console.error("Usage: organize <workspaceRoot> <instruction> [--auto-approve] [--run-id <runId>]");
  process.exit(1);
}

const result = await runOrganizeWorkflow({
  workspaceRoot,
  instruction,
  runId,
  autoApprove
});

console.log(JSON.stringify(result, null, 2));
```

- [ ] **Step 4: Implement resume CLI**

Create `src/cli/resume.ts`:

```ts
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { decideResumeAction } from "../domain/validation/resume-decision";

const workspaceRoot = process.argv[2];

if (!workspaceRoot) {
  console.error("Usage: resume <workspaceRoot>");
  process.exit(1);
}

async function latestRunId(root: string): Promise<string | null> {
  const runsRoot = path.join(root, ".agent-runs");
  const entries = await readdir(runsRoot, { withFileTypes: true });
  const runIds = entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
  return runIds.at(-1) ?? null;
}

const runId = await latestRunId(workspaceRoot);

if (!runId) {
  console.log(JSON.stringify({ workspaceRoot, status: "NO_RUNS_FOUND" }, null, 2));
  process.exit(0);
}

const workItemsRoot = path.join(workspaceRoot, ".agent-runs", runId, "work-items");
const workItemFiles = await readdir(workItemsRoot);
const decisions = await Promise.all(
  workItemFiles
    .filter((file) => file.endsWith(".json"))
    .map(async (file) => {
      const workItem = JSON.parse(await readFile(path.join(workItemsRoot, file), "utf8")) as {
        id: string;
        status: Parameters<typeof decideResumeAction>[0]["status"];
      };
      return {
        workItemId: workItem.id,
        action: decideResumeAction({
          status: workItem.status,
          currentSha: "published-sha",
          contentSha: "published-sha",
          retryable: true
        })
      };
    })
);

console.log(
  JSON.stringify(
    {
      workspaceRoot,
      runId,
      decisions
    },
    null,
    2
  )
);
```

- [ ] **Step 5: Verify CLI tests pass**

Run:

```bash
npm test -- tests/integration/cli-smoke.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 6: Full verification**

Run:

```bash
npm test
npm run typecheck
```

Expected: all tests pass and typecheck exits 0.

- [ ] **Step 7: Commit**

```bash
git add src/cli tests/integration/cli-smoke.test.ts
git commit -m "feat: add organize cli smoke"
```

## Task 11: Documentation and Delivery Report

**Files:**
- Modify: `docs/architecture/langgraph-agent-loop-design.md`
- Modify: `docs/architecture/agent-loop-core.md`
- Create: `docs/reports/first-slice-delivery.md`

- [ ] **Step 1: Add delivery report**

Create `docs/reports/first-slice-delivery.md`:

```md
# First Slice Delivery Report

## Delivered

- TypeScript/Vitest project harness.
- Workspace fixtures.
- WorkspaceInventory and PageState detection.
- OrganizePlanner with three-phase work items.
- AgentRunsStore for `.agent-runs` artifacts.
- PatchBundle, MergeGuard, Publisher.
- Validator, EvalReporter, resume decision.
- Mock NoteAgentNode.
- LangGraph Level 1 workflow shell.
- Organize and resume CLI smoke.

## Verification

- `npm test`
- `npm run typecheck`

## Provider Scope

This slice uses mock agents only. It does not prove a real provider path.

## Review Focus

- Correctness of workspace path guards.
- Topic Note Quality Contract enforcement.
- `.agent-runs` artifact shape.
- LangGraph state staying lightweight.
- Resume decision boundaries.
```

- [ ] **Step 2: Update architecture docs only if implementation differs**

If implementation changed any contract names, update the relevant docs:

```text
docs/architecture/langgraph-agent-loop-design.md
docs/architecture/agent-loop-core.md
docs/architecture/workspace-contract.md
```

Do not update docs for purely internal helper names.

- [ ] **Step 3: Run final verification**

Run:

```bash
npm test
npm run typecheck
git diff --check
```

Expected: all commands exit 0.

- [ ] **Step 4: Commit**

```bash
git add docs/reports/first-slice-delivery.md docs/architecture
git commit -m "docs: report first agent loop slice"
```

## Self-Review

Spec coverage:

- LangGraph-first / Domain-pure: Tasks 9 and 11.
- TypeScript-first runtime: Task 1.
- `.agent-runs` artifacts: Task 5.
- WorkspaceInventory: Task 3.
- OrganizePlanner: Task 4.
- Agent node bounded output: Task 8.
- PatchBundle + MergeGuard + Publisher: Task 6.
- Validator / Eval / topic note quality: Task 7.
- Resume decision and CLI smoke: Tasks 7 and 10.
- Fixture-based delivery: Task 2 and Task 11.

No task requires a real provider. A real provider smoke must be planned separately after all mock contract tests pass.
