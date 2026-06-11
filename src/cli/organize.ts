import { runOrganizeWorkflow } from "../runtime/langgraph/graph.js";

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
