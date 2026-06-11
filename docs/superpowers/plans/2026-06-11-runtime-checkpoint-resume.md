# Runtime Checkpoint Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the current LangGraph runtime so plan approval pause, checkpoint state, and `.agent-runs` artifact resume form one consistent recovery contract.

**Architecture:** Keep Domain Core pure and deterministic. Add a runtime checkpoint layer using LangGraph `MemorySaver` first, with a `CheckpointStore` boundary that can later be replaced by a persistent file or SQLite saver. Approval remains represented in `.agent-runs/approvals`, while graph state stores only lightweight references and resumes are validated against current workspace shas before any publish.

**Tech Stack:** TypeScript, `@langchain/langgraph` `StateGraph`/`MemorySaver`, Vitest, filesystem `.agent-runs` artifacts, deterministic sha validation.

---

## Deliverable Result

When this plan is complete:

```text
npm test
  -> all unit and integration tests pass

npm run organize -- <fixture> "整理全部知识库"
  -> writes inventory.json, plan.json, work-items/*.json
  -> writes approvals/plan-approval.json with PENDING
  -> returns WAITING_PLAN_APPROVAL without executing Phase A

npm run organize -- <fixture> "整理全部知识库" --auto-approve
  -> writes approvals/plan-approval.json with APPROVED
  -> publishes authorized mock note patch
  -> writes validation.json, eval.json, report.md

npm run resume -- <fixture>
  -> loads latest run artifacts
  -> computes real current sha for published target paths
  -> returns SKIP for published matching items
  -> returns REPLAN for base/content sha mismatch
```

This plan does not add a real provider. It hardens runtime contracts before any real provider smoke.

## File Structure

Create or modify:

```text
src/runtime/langgraph/
  checkpoint-store.ts
  graph.ts
  state.ts
  nodes/
    approval-node.ts
    plan-node.ts
    execute-phase-node.ts

src/storage/
  agent-runs-store.ts
  workspace-fs.ts

src/domain/validation/
  resume-decision.ts
  resume-inspector.ts

src/cli/
  organize.ts
  resume.ts

tests/unit/
  approval-artifacts.test.ts
  resume-inspector.test.ts

tests/integration/
  approval-pause.test.ts
  checkpoint-resume.test.ts
  cli-smoke.test.ts
```

## Task 1: Approval Artifacts

**Files:**
- Modify: `src/storage/agent-runs-store.ts`
- Create: `src/runtime/langgraph/nodes/approval-node.ts`
- Modify: `src/runtime/langgraph/nodes/plan-node.ts`
- Test: `tests/unit/approval-artifacts.test.ts`

- [ ] **Step 1: Write failing approval artifact tests**

Create `tests/unit/approval-artifacts.test.ts`:

```ts
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";
import { writePlanApproval } from "../../src/runtime/langgraph/nodes/approval-node.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "approval-artifacts-"));
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("approval artifacts", () => {
  it("writes pending and approved plan approval artifacts", async () => {
    const store = new AgentRunsStore(tempRoot, "run-approval");

    await writePlanApproval({ store, status: "PENDING" });
    await expect(store.readJson("approvals/plan-approval.json")).resolves.toMatchObject({
      type: "PLAN_APPROVAL",
      status: "PENDING"
    });

    await writePlanApproval({ store, status: "APPROVED", approvedAt: "2026-06-11T00:00:00.000Z" });
    await expect(store.readJson("approvals/plan-approval.json")).resolves.toMatchObject({
      type: "PLAN_APPROVAL",
      status: "APPROVED",
      approvedAt: "2026-06-11T00:00:00.000Z"
    });
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
npm test -- tests/unit/approval-artifacts.test.ts
```

Expected: FAIL because `approval-node.ts` does not exist.

- [ ] **Step 3: Add approval artifact writer**

Create `src/runtime/langgraph/nodes/approval-node.ts`:

```ts
import type { AgentRunsStore } from "../../../storage/agent-runs-store.js";

export type PlanApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface PlanApprovalArtifact {
  type: "PLAN_APPROVAL";
  status: PlanApprovalStatus;
  approvedAt: string | null;
}

export async function writePlanApproval(input: {
  store: AgentRunsStore;
  status: PlanApprovalStatus;
  approvedAt?: string;
}): Promise<PlanApprovalArtifact> {
  const artifact: PlanApprovalArtifact = {
    type: "PLAN_APPROVAL",
    status: input.status,
    approvedAt: input.approvedAt ?? null
  };
  await input.store.writeJson("approvals/plan-approval.json", artifact);
  return artifact;
}
```

- [ ] **Step 4: Wire plan node to write pending/approved approval artifacts**

Modify `src/runtime/langgraph/nodes/plan-node.ts`:

```ts
import { writePlanApproval } from "./approval-node.js";
```

Inside `planNode`, after writing work items:

```ts
await writePlanApproval({
  store,
  status: state.autoApprove ? "APPROVED" : "PENDING",
  approvedAt: state.autoApprove ? new Date(0).toISOString() : undefined
});
```

Use `new Date(0).toISOString()` in tests for deterministic approval artifacts. A later runtime can replace this with real clock injection.

- [ ] **Step 5: Verify approval artifact tests pass**

Run:

```bash
npm test -- tests/unit/approval-artifacts.test.ts
npm run typecheck
```

Expected: both commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/runtime/langgraph/nodes/approval-node.ts src/runtime/langgraph/nodes/plan-node.ts tests/unit/approval-artifacts.test.ts
git commit -m "feat: persist plan approval artifacts"
```

## Task 2: Runtime Checkpoint Store Boundary

**Files:**
- Create: `src/runtime/langgraph/checkpoint-store.ts`
- Modify: `src/runtime/langgraph/graph.ts`
- Test: `tests/integration/checkpoint-resume.test.ts`

- [ ] **Step 1: Write failing checkpoint test**

Create `tests/integration/checkpoint-resume.test.ts`:

```ts
import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createMemoryCheckpointStore } from "../../src/runtime/langgraph/checkpoint-store.js";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "checkpoint-resume-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("runtime checkpoint store", () => {
  it("uses the same MemorySaver across calls for one run thread", async () => {
    const checkpointStore = createMemoryCheckpointStore();

    const waiting = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-checkpoint",
      autoApprove: false,
      checkpointStore
    });

    expect(waiting.status).toBe("WAITING_PLAN_APPROVAL");

    const approved = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-checkpoint",
      autoApprove: true,
      checkpointStore
    });

    expect(approved.status).toBe("SUCCEEDED_WITH_WARNINGS");
    expect(approved.reportPath).toBe(".agent-runs/run-checkpoint/report.md");
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
npm test -- tests/integration/checkpoint-resume.test.ts
```

Expected: FAIL because `checkpoint-store.ts` does not exist and `runOrganizeWorkflow` has no `checkpointStore` parameter.

- [ ] **Step 3: Add checkpoint store wrapper**

Create `src/runtime/langgraph/checkpoint-store.ts`:

```ts
import { MemorySaver } from "@langchain/langgraph";

export interface RuntimeCheckpointStore {
  checkpointer: MemorySaver;
}

export function createMemoryCheckpointStore(): RuntimeCheckpointStore {
  return {
    checkpointer: new MemorySaver()
  };
}
```

- [ ] **Step 4: Compile graph with optional checkpointer and thread id**

Modify `src/runtime/langgraph/graph.ts`:

```ts
import type { RuntimeCheckpointStore } from "./checkpoint-store.js";
```

Update input:

```ts
checkpointStore?: RuntimeCheckpointStore;
```

Compile with checkpointer:

```ts
const compiled = graph.compile({
  checkpointer: input.checkpointStore?.checkpointer
});
```

Invoke with config:

```ts
return compiled.invoke(initialState, {
  configurable: {
    thread_id: input.runId
  }
});
```

Keep graph state lightweight; do not store full patch content in graph state.

- [ ] **Step 5: Verify checkpoint tests pass**

Run:

```bash
npm test -- tests/integration/checkpoint-resume.test.ts
npm run typecheck
```

Expected: both commands exit 0. If LangGraph 0.4.9 requires a different config key than `thread_id`, inspect local typings and update only `graph.ts` and this test.

- [ ] **Step 6: Commit**

```bash
git add src/runtime/langgraph/checkpoint-store.ts src/runtime/langgraph/graph.ts tests/integration/checkpoint-resume.test.ts
git commit -m "feat: add runtime checkpoint store"
```

## Task 3: Approval Pause Contract

**Files:**
- Modify: `src/runtime/langgraph/state.ts`
- Modify: `src/runtime/langgraph/graph.ts`
- Modify: `src/runtime/langgraph/nodes/plan-node.ts`
- Test: `tests/integration/approval-pause.test.ts`

- [ ] **Step 1: Write failing approval pause test**

Create `tests/integration/approval-pause.test.ts`:

```ts
import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "approval-pause-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("plan approval pause contract", () => {
  it("returns without executing Phase A when plan approval is pending", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-approval",
      autoApprove: false
    });

    expect(result.status).toBe("WAITING_PLAN_APPROVAL");

    const approval = JSON.parse(
      await readFile(path.join(tempRoot, ".agent-runs/run-approval/approvals/plan-approval.json"), "utf8")
    );
    expect(approval.status).toBe("PENDING");

    await expect(
      readFile(path.join(tempRoot, ".agent-runs/run-approval/patches/rewrite-tools-skill-vs-cli-tool-决策.patch.json"), "utf8")
    ).rejects.toMatchObject({ code: "ENOENT" });
  });
});
```

- [ ] **Step 2: Run test and verify it fails or exposes current behavior**

Run:

```bash
npm test -- tests/integration/approval-pause.test.ts
```

Expected before Task 1 wiring: FAIL because approval artifact does not exist. After Task 1, this test becomes regression coverage for the pause contract.

- [ ] **Step 3: Add explicit pending approval fields to GraphState**

Modify `src/runtime/langgraph/state.ts`:

```ts
pendingApproval?: {
  type: "PLAN";
  artifactPath: string;
};
```

Update GraphAnnotation in `graph.ts`:

```ts
pendingApproval: Annotation<GraphState["pendingApproval"]>
```

- [ ] **Step 4: Return pending approval metadata from plan node**

In `src/runtime/langgraph/nodes/plan-node.ts`, when `autoApprove` is false, return:

```ts
pendingApproval: {
  type: "PLAN",
  artifactPath: `.agent-runs/${state.runId}/approvals/plan-approval.json`
}
```

When `autoApprove` is true, return `pendingApproval: undefined`.

- [ ] **Step 5: Add conditional edge to skip execution while waiting**

Modify `src/runtime/langgraph/graph.ts`:

```ts
.addConditionalEdges("plan", (state) => {
  return state.status === "WAITING_PLAN_APPROVAL" ? "report" : "execute";
})
```

Do not execute Phase A when plan approval is pending.

- [ ] **Step 6: Verify approval pause tests pass**

Run:

```bash
npm test -- tests/integration/approval-pause.test.ts
npm test -- tests/integration/langgraph-workflow.test.ts
npm run typecheck
```

Expected: all commands exit 0.

- [ ] **Step 7: Commit**

```bash
git add src/runtime/langgraph tests/integration/approval-pause.test.ts
git commit -m "feat: enforce plan approval pause contract"
```

## Task 4: Real Resume Inspector

**Files:**
- Create: `src/domain/validation/resume-inspector.ts`
- Modify: `src/cli/resume.ts`
- Test: `tests/unit/resume-inspector.test.ts`

- [ ] **Step 1: Write failing resume inspector tests**

Create `tests/unit/resume-inspector.test.ts`:

```ts
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { inspectResumeWorkItem } from "../../src/domain/validation/resume-inspector.js";
import { sha256 } from "../../src/storage/sha.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "resume-inspector-"));
  await mkdir(path.join(tempRoot, "knowledge-base/topics/tools"), { recursive: true });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("resume inspector", () => {
  it("skips published work item when target content sha matches", async () => {
    const targetPath = "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md";
    const content = "# Skill vs CLI Tool 决策\n";
    await writeFile(path.join(tempRoot, targetPath), content, "utf8");

    const result = await inspectResumeWorkItem({
      workspaceRoot: tempRoot,
      workItem: {
        id: "rewrite-tools",
        status: "PUBLISHED",
        targetPaths: [targetPath],
        contentSha: sha256(content)
      }
    });

    expect(result.action).toBe("SKIP");
    expect(result.currentSha).toBe(sha256(content));
  });

  it("requests replan when published target sha changed", async () => {
    const targetPath = "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md";
    await writeFile(path.join(tempRoot, targetPath), "# Changed\n", "utf8");

    const result = await inspectResumeWorkItem({
      workspaceRoot: tempRoot,
      workItem: {
        id: "rewrite-tools",
        status: "PUBLISHED",
        targetPaths: [targetPath],
        contentSha: "old-sha"
      }
    });

    expect(result.action).toBe("REPLAN");
  });
});
```

- [ ] **Step 2: Run test and verify it fails**

Run:

```bash
npm test -- tests/unit/resume-inspector.test.ts
```

Expected: FAIL because `resume-inspector.ts` does not exist.

- [ ] **Step 3: Implement resume inspector**

Create `src/domain/validation/resume-inspector.ts`:

```ts
import { readFile } from "node:fs/promises";
import path from "node:path";
import type { WorkItemStatus } from "../planning/work-item.js";
import { sha256 } from "../../storage/sha.js";
import { decideResumeAction, type ResumeAction } from "./resume-decision.js";

export interface ResumeInspectableWorkItem {
  id: string;
  status: WorkItemStatus;
  targetPaths: string[];
  contentSha?: string;
  retryable?: boolean;
}

export interface ResumeInspection {
  workItemId: string;
  action: ResumeAction;
  targetPath: string | null;
  currentSha: string | null;
  contentSha: string | null;
}

export async function inspectResumeWorkItem(input: {
  workspaceRoot: string;
  workItem: ResumeInspectableWorkItem;
}): Promise<ResumeInspection> {
  const targetPath = input.workItem.targetPaths[0] ?? null;
  let currentSha: string | null = null;

  if (targetPath) {
    try {
      const content = await readFile(path.join(input.workspaceRoot, targetPath), "utf8");
      currentSha = sha256(content);
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
        throw error;
      }
    }
  }

  return {
    workItemId: input.workItem.id,
    targetPath,
    currentSha,
    contentSha: input.workItem.contentSha ?? null,
    action: decideResumeAction({
      status: input.workItem.status,
      currentSha: currentSha ?? undefined,
      contentSha: input.workItem.contentSha,
      retryable: input.workItem.retryable
    })
  };
}
```

- [ ] **Step 4: Update resume CLI to use real sha inspection**

Modify `src/cli/resume.ts`:

```ts
import { inspectResumeWorkItem } from "../domain/validation/resume-inspector.js";
```

When reading each work item, derive `contentSha` from either:

```ts
const patchPath = path.join(workspaceRoot, ".agent-runs", runId, "patches", `${workItem.id}.patch.json`);
```

If patch exists, read first file `contentSha`; otherwise leave undefined.

Call:

```ts
return inspectResumeWorkItem({
  workspaceRoot,
  workItem: {
    id: workItem.id,
    status: workItem.status,
    targetPaths: workItem.targetPaths ?? [],
    contentSha,
    retryable: true
  }
});
```

Remove the placeholder `"published-sha"` values.

- [ ] **Step 5: Verify resume inspector tests pass**

Run:

```bash
npm test -- tests/unit/resume-inspector.test.ts
npm test -- tests/integration/cli-smoke.test.ts
npm run typecheck
```

Expected: all commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/domain/validation/resume-inspector.ts src/cli/resume.ts tests/unit/resume-inspector.test.ts
git commit -m "feat: inspect workspace sha during resume"
```

## Task 5: Artifact Fallback Resume

**Files:**
- Modify: `src/cli/resume.ts`
- Modify: `src/storage/agent-runs-store.ts`
- Test: `tests/integration/artifact-fallback-resume.test.ts`

- [ ] **Step 1: Write failing artifact fallback test**

Create `tests/integration/artifact-fallback-resume.test.ts`:

```ts
import { cp, mkdtemp, rm } from "node:fs/promises";
import { execFile } from "node:child_process";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const execFileAsync = promisify(execFile);
let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "artifact-fallback-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("artifact fallback resume", () => {
  it("resumes from .agent-runs artifacts when no checkpoint store is provided", async () => {
    await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--run-id",
      "run-artifact"
    ]);

    const result = await execFileAsync(process.execPath, ["--import", "tsx", "src/cli/resume.ts", tempRoot]);

    const output = JSON.parse(result.stdout) as {
      runId: string;
      decisions: Array<{ action: string }>;
    };
    expect(output.runId).toBe("run-artifact");
    expect(output.decisions.some((decision) => decision.action === "SKIP")).toBe(true);
  });
});
```

- [ ] **Step 2: Run test and verify it fails or exposes current placeholder behavior**

Run:

```bash
npm test -- tests/integration/artifact-fallback-resume.test.ts
```

Expected: PASS only after Task 4 removes placeholder shas and resume uses real current sha from workspace plus patch artifact contentSha.

- [ ] **Step 3: Add latest run helper to AgentRunsStore**

Modify `src/storage/agent-runs-store.ts`:

```ts
static async latestRunId(workspaceRoot: string): Promise<string | null> {
  const runsRoot = path.join(workspaceRoot, ".agent-runs");
  try {
    const entries = await fs.readdir(runsRoot, { withFileTypes: true });
    const runIds = entries.filter((entry) => entry.isDirectory()).map((entry) => entry.name).sort();
    return runIds.at(-1) ?? null;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") {
      return null;
    }
    throw error;
  }
}
```

- [ ] **Step 4: Use AgentRunsStore.latestRunId in resume CLI**

Replace local `latestRunId` implementation in `src/cli/resume.ts` with:

```ts
const runId = await AgentRunsStore.latestRunId(workspaceRoot);
```

Keep output shape:

```json
{
  "workspaceRoot": "/absolute/workspace",
  "runId": "run-artifact",
  "decisions": [
    {
      "workItemId": "rewrite-tools",
      "action": "SKIP"
    }
  ]
}
```

- [ ] **Step 5: Verify fallback tests pass**

Run:

```bash
npm test -- tests/integration/artifact-fallback-resume.test.ts
npm test -- tests/integration/cli-smoke.test.ts
npm run typecheck
```

Expected: all commands exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/storage/agent-runs-store.ts src/cli/resume.ts tests/integration/artifact-fallback-resume.test.ts
git commit -m "feat: resume from agent run artifacts"
```

## Task 6: Documentation and Delivery Report

**Files:**
- Modify: `docs/architecture/langgraph-agent-loop-design.md`
- Create: `docs/reports/runtime-checkpoint-resume-delivery.md`

- [ ] **Step 1: Update runtime scope in architecture spec**

Modify `docs/architecture/langgraph-agent-loop-design.md` implementation slice note from:

```text
持久化 LangGraph checkpointer 是 runtime 加固项；接 real provider smoke 前应补齐或明确接受该 gap。
```

To:

```text
Runtime hardening adds a MemorySaver checkpoint boundary and artifact-based fallback resume. A durable file or SQLite checkpoint saver remains the next storage adapter before cross-process or long-running provider workflows.
```

- [ ] **Step 2: Add delivery report**

Create `docs/reports/runtime-checkpoint-resume-delivery.md`:

```md
# Runtime Checkpoint Resume Delivery Report

## Delivered

- Plan approval artifacts under `.agent-runs/<runId>/approvals/`.
- Runtime checkpoint store boundary using LangGraph `MemorySaver`.
- Graph invocation with `thread_id` set to run id.
- Explicit pending approval metadata in graph state.
- Resume inspector that compares current workspace sha with patch artifact content sha.
- Artifact fallback resume through `.agent-runs` when no checkpoint store is available.

## Verification

- `npm test`
- `npm run typecheck`
- `git diff --check`

## Scope

This delivery still uses mock agents only. It does not add a real provider.

## Remaining Runtime Gap

`MemorySaver` verifies checkpoint integration inside one process. A durable file or SQLite checkpoint saver is still required before cross-process resume, long-running provider calls, or multi-user runtime.

## Review Focus

- Approval artifact correctness.
- Whether graph conditional edges prevent Phase A execution while approval is pending.
- Whether resume decisions use real workspace shas.
- Whether checkpoint state remains lightweight.
```

- [ ] **Step 3: Final verification**

Run:

```bash
npm test
npm run typecheck
git diff --check
```

Expected: all commands exit 0.

- [ ] **Step 4: Commit**

```bash
git add docs/architecture/langgraph-agent-loop-design.md docs/reports/runtime-checkpoint-resume-delivery.md
git commit -m "docs: report runtime checkpoint resume hardening"
```

## Self-Review

Spec coverage:

- Plan approval artifact: Task 1.
- Checkpoint boundary: Task 2.
- Approval pause contract: Task 3.
- Real workspace sha resume: Task 4.
- Artifact fallback resume: Task 5.
- Delivery documentation: Task 6.

Known constraints:

- `MemorySaver` does not prove cross-process persistence.
- This plan does not add a real provider.
- Durable file or SQLite checkpoint saver remains a future runtime storage adapter.
- Native LangGraph `interrupt()` / `Command` resume is not introduced in this plan; it remains a later replacement for the current approval pause contract when UI or external human-review callbacks are added.
