import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import type { PatchBundle } from "../domain/patch/patch-bundle.js";
import type { WorkItemStatus } from "../domain/planning/work-item.js";
import { inspectResumeWorkItem } from "../domain/validation/resume-inspector.js";
import { AgentRunsStore } from "../storage/agent-runs-store.js";

const workspaceRoot = process.argv[2];

if (!workspaceRoot) {
  console.error("Usage: resume <workspaceRoot>");
  process.exit(1);
}

const runId = await AgentRunsStore.latestRunId(workspaceRoot);

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
        targetPaths?: string[];
      };
      let contentSha: string | undefined;
      try {
        const patchPath = path.join(workspaceRoot, ".agent-runs", runId, "patches", `${workItem.id}.patch.json`);
        const patch = JSON.parse(await readFile(patchPath, "utf8")) as PatchBundle;
        contentSha = patch.files[0]?.contentSha;
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== "ENOENT") {
          throw error;
        }
      }
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
