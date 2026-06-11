import { runOrganizeWorkflow } from "../runtime/langgraph/graph.js";
import type { ProviderRuntimeName } from "../runtime/provider/provider-runtime-config.js";

function readArg(name: string): string | null {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
}

const workspaceRoot = process.argv[2];
const instruction = process.argv[3];
const autoApprove = process.argv.includes("--auto-approve");
const runId = readArg("--run-id") ?? `run-${Date.now()}`;
const provider = readArg("--provider") ?? "fake";

if (!workspaceRoot || !instruction) {
  console.error("Usage: organize <workspaceRoot> <instruction> [--auto-approve] [--run-id <runId>] [--provider fake]");
  process.exit(1);
}

if (provider !== "fake") {
  console.error(`Unsupported provider: ${provider}`);
  process.exit(1);
}

const result = await runOrganizeWorkflow({
  workspaceRoot,
  instruction,
  runId,
  autoApprove,
  providerRuntime: {
    provider: provider as ProviderRuntimeName,
    timeoutMs: 30000
  }
});

console.log(JSON.stringify(result, null, 2));
