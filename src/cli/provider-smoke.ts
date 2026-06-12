import { runRealProviderSmoke, type RealSmokeProvider } from "../runtime/provider/real-smoke.js";

function readArg(name: string): string | null {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : null;
}

const provider = readArg("--provider") ?? "deepseek-real-smoke";
const executeReal = process.argv.includes("--execute-real");

if (provider !== "deepseek-real-smoke" && provider !== "claude-code-real-smoke") {
  console.error(`Unsupported real smoke provider: ${provider}`);
  process.exit(1);
}

const result = await runRealProviderSmoke({
  provider: provider as RealSmokeProvider,
  env: process.env,
  executeReal
});

console.log(JSON.stringify(result, null, 2));
