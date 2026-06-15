import {
  runOpenAgentRealSmoke,
  type OpenAgentRealSmokeMode,
  type OpenAgentRealSmokeProvider
} from "../runtime/provider/open-agent-real-smoke.js";
import {
  configureMimoKeychainEnv,
  hydrateMimoEnvFromKeychain
} from "../runtime/provider/mimo-keychain.js";
import type { OpenAgentOutputPolicy } from "../sdk/open-agent-runtime.js";

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

const provider = (readArg("--provider") ?? "mimo-open-agent-smoke") as OpenAgentRealSmokeProvider;
if (provider !== "mimo-open-agent-smoke") {
  console.error(`Unsupported open agent smoke provider: ${provider}`);
  process.exit(1);
}

const baseUrl = readArg("--base-url");
const model = readArg("--model");
const apiKeyFromStdin = process.argv.includes("--api-key-stdin");

if (process.argv.includes("--configure-mimo-keychain")) {
  if (!apiKeyFromStdin) {
    console.error("--configure-mimo-keychain requires --api-key-stdin");
    process.exit(1);
  }
  const configured = await configureMimoKeychainEnv({
    apiKey: await readStdin(),
    baseUrl: baseUrl ?? undefined,
    model: model ?? undefined
  });
  console.log(
    JSON.stringify(
      {
        status: "CONFIGURED",
        provider,
        keychain: {
          service: configured.service,
          accounts: configured.accounts
        }
      },
      null,
      2
    )
  );
  process.exit(0);
}

const runtimeEnv: Record<string, string | undefined> = process.env.MY_WORKFLOW_AGENT_DISABLE_KEYCHAIN
  ? { ...process.env }
  : await hydrateMimoEnvFromKeychain({ ...process.env });
if (apiKeyFromStdin) {
  runtimeEnv.MIMO_API_KEY = await readStdin();
}
if (baseUrl) {
  runtimeEnv.MIMO_BASE_URL = baseUrl;
}
if (model) {
  runtimeEnv.MIMO_MODEL = model;
}

const result = await runOpenAgentRealSmoke({
  provider,
  workspaceRoot: readArg("--workspace-root") ?? process.cwd(),
  env: runtimeEnv,
  mode: (readArg("--mode") ?? "deterministic") as OpenAgentRealSmokeMode,
  outputPolicy: (readArg("--output-policy") ?? undefined) as OpenAgentOutputPolicy | undefined,
  executeReal: process.argv.includes("--execute-real")
});

console.log(JSON.stringify(result, null, 2));
