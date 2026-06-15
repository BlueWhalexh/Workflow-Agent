import { getKnowledgeMethodology } from "../domain/methodology/knowledge-methodology.js";
import { runOrganize } from "../sdk/knowledge-workflow-agent.js";
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
const methodologyId = readArg("--methodology") ?? "lmwiki-v1";
const allowRealProvider = process.argv.includes("--allow-real-provider");
const supportedProviders = [
  "fake",
  "deepseek-fixture",
  "claude-code-fixture",
  "mimo-vllm-fixture",
  "deepseek-real",
  "mimo-real"
];

if (!workspaceRoot || !instruction) {
  console.error(
    "Usage: organize <workspaceRoot> <instruction> [--auto-approve] [--run-id <runId>] [--methodology lmwiki-v1] [--provider fake|deepseek-fixture|claude-code-fixture|mimo-vllm-fixture|deepseek-real|mimo-real] [--allow-real-provider]"
  );
  process.exit(1);
}

if (!supportedProviders.includes(provider)) {
  console.error(`Unsupported provider: ${provider}`);
  process.exit(1);
}

try {
  getKnowledgeMethodology(methodologyId);
} catch (error) {
  console.error((error as Error).message);
  process.exit(1);
}

if (provider === "deepseek-real") {
  if (!allowRealProvider) {
    console.error("Provider deepseek-real requires --allow-real-provider");
    process.exit(1);
  }
  if (!process.env.DEEPSEEK_API_KEY) {
    console.error("Provider deepseek-real requires DEEPSEEK_API_KEY");
    process.exit(1);
  }
}

if (provider === "mimo-real") {
  if (!allowRealProvider) {
    console.error("Provider mimo-real requires --allow-real-provider");
    process.exit(1);
  }
  if (!process.env.MIMO_API_KEY) {
    console.error("Provider mimo-real requires MIMO_API_KEY");
    process.exit(1);
  }
  if (!process.env.MIMO_MODEL) {
    console.error("Provider mimo-real requires MIMO_MODEL");
    process.exit(1);
  }
}

const result = await runOrganize({
  workspaceRoot,
  instruction,
  runId,
  methodologyId,
  autoApprove,
  providerRuntime: {
    provider: provider as ProviderRuntimeName,
    timeoutMs: 30000,
    model: provider === "mimo-real" ? process.env.MIMO_MODEL : process.env.DEEPSEEK_MODEL,
    baseUrl: provider === "mimo-real" ? process.env.MIMO_BASE_URL : process.env.DEEPSEEK_BASE_URL,
    apiKeyEnvName: provider === "mimo-real" ? "MIMO_API_KEY" : "DEEPSEEK_API_KEY"
  }
});

console.log(JSON.stringify(result, null, 2));
