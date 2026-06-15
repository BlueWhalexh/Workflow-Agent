import { inspectRun } from "../sdk/knowledge-workflow-agent.js";

const workspaceRoot = process.argv[2];

if (!workspaceRoot) {
  console.error("Usage: resume <workspaceRoot>");
  process.exit(1);
}

const result = await inspectRun({ workspaceRoot });

if (result.status === "NO_RUNS_FOUND") {
  console.log(JSON.stringify({ workspaceRoot, status: "NO_RUNS_FOUND" }, null, 2));
  process.exit(0);
}

console.log(
  JSON.stringify(
    {
      workspaceRoot,
      runId: result.runId,
      decisions: result.decisions
    },
    null,
    2
  )
);
