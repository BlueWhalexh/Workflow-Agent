# Runtime Checkpoint Resume Delivery Report

## Delivered

- Plan approval artifacts under `.agent-runs/<runId>/approvals/`.
- Plan approval pause contract through `WAITING_PLAN_APPROVAL`.
- Runtime checkpoint store boundary using LangGraph `MemorySaver`.
- Real resume inspection against workspace target file SHA.
- Artifact fallback resume from `.agent-runs` without relying on in-memory checkpoint state.
- Planner regression fix for topic index target paths.

## Verified Capabilities

- `organize` can stop at plan approval and emit an approval artifact.
- Approved workflow resumes through LangGraph with a checkpointer configured by `thread_id`.
- `resume` reads latest `.agent-runs` artifacts and reports decisions.
- Published work items are skipped only when current workspace content SHA matches the patch content SHA.
- Published work items are marked for replan when workspace content changed.
- Topic index work items target `knowledge-base/topics/<topic>/index.md`, not `<note>.md/index.md`.

## Verification

- `npm test -- tests/unit/resume-inspector.test.ts`
- `npm test -- tests/unit/agent-runs-store.test.ts`
- `npm test -- tests/integration/checkpoint-resume.test.ts`
- `npm test -- tests/integration/approval-pause.test.ts`
- `npm test -- tests/integration/artifact-fallback-resume.test.ts`
- `npm test -- tests/integration/cli-smoke.test.ts`
- `npm test`
- `npm run typecheck`
- `git diff --check`

## Boundary

This delivery still uses mock agents. It does not prove a real provider path.

`MemorySaver` proves the LangGraph checkpointer integration boundary only. It does not provide durable cross-process recovery. Durable file or SQLite checkpoint storage remains the required next runtime adapter before long-running provider workflows.

The durable recovery fact source is `.agent-runs` plus current workspace file SHA. If runtime checkpoint state and artifacts conflict, recovery must prefer artifacts, workspace SHA, and deterministic validation.

## Review Focus

- Correctness of resume SHA comparison for published work items.
- Whether single-file `PatchBundle` content SHA is sufficient before multi-file patches.
- Approval pause semantics before adopting LangGraph native `interrupt()` / `Command`.
- Durable checkpointer selection before real provider smoke or cross-process resume.
