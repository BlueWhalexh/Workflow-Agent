import { runRealProviderSmoke, type RealSmokeProvider } from "../runtime/provider/real-smoke.js";

function readArg(name: string): string | null {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
}

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString("utf8").trim();
}

function envNamesForProvider(provider: RealSmokeProvider): {
  apiKeyEnvName?: string;
  baseUrlEnvName?: string;
  modelEnvName?: string;
} {
  if (provider === "mimo-real-smoke") {
    return {
      apiKeyEnvName: "MIMO_API_KEY",
      baseUrlEnvName: "MIMO_BASE_URL",
      modelEnvName: "MIMO_MODEL"
    };
  }
  if (provider === "deepseek-real-smoke") {
    return {
      apiKeyEnvName: "DEEPSEEK_API_KEY",
      baseUrlEnvName: "DEEPSEEK_BASE_URL",
      modelEnvName: "DEEPSEEK_MODEL"
    };
  }
  return {};
}

const provider = readArg("--provider") ?? "deepseek-real-smoke";
const executeReal = process.argv.includes("--execute-real");
const apiKeyFromStdin = process.argv.includes("--api-key-stdin");
const baseUrl = readArg("--base-url");
const model = readArg("--model");

if (provider !== "deepseek-real-smoke" && provider !== "mimo-real-smoke" && provider !== "claude-code-real-smoke") {
  console.error(`Unsupported real smoke provider: ${provider}`);
  process.exit(1);
}

const runtimeEnv: Record<string, string | undefined> = { ...process.env };
const envNames = envNamesForProvider(provider as RealSmokeProvider);
if (apiKeyFromStdin && envNames.apiKeyEnvName) {
  runtimeEnv[envNames.apiKeyEnvName] = await readStdin();
}
if (baseUrl && envNames.baseUrlEnvName) {
  runtimeEnv[envNames.baseUrlEnvName] = baseUrl;
}
if (model && envNames.modelEnvName) {
  runtimeEnv[envNames.modelEnvName] = model;
}

const result = await runRealProviderSmoke({
  provider: provider as RealSmokeProvider,
  env: runtimeEnv,
  executeReal
});

console.log(JSON.stringify(result, null, 2));
