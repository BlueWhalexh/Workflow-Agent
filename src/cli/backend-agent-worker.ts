import { stdin, stdout } from "node:process";
import { runBackendAgent, type BackendAgentRequest } from "../sdk/backend-adapter.js";

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of stdin) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString("utf8");
}

async function main(): Promise<void> {
  const input = await readStdin();
  const request = JSON.parse(input) as BackendAgentRequest;
  const response = await runBackendAgent(request);
  stdout.write(JSON.stringify(response));
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : "Unknown backend agent worker error";
  console.error(JSON.stringify({ schemaVersion: "agent-backend-worker-error.v1", message }));
  process.exitCode = 1;
});
