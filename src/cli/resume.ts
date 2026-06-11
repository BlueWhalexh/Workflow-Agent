import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { decideResumeAction } from "../domain/validation/resume-decision.js";
import type { WorkItemStatus } from "../domain/planning/work-item.js";

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
        status: WorkItemStatus;
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
