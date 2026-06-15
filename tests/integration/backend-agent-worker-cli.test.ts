import { spawn } from "node:child_process";
import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { describe, expect, it } from "vitest";

function runWorkerCli(input: unknown): Promise<{ stdout: string; stderr: string; exitCode: number | null }> {
  return new Promise((resolve, reject) => {
    const child = spawn(
      "node",
      ["--import", "tsx", "src/cli/backend-agent-worker.ts"],
      { cwd: process.cwd(), stdio: ["pipe", "pipe", "pipe"] }
    );
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", reject);
    child.on("close", (exitCode) => {
      resolve({ stdout, stderr, exitCode });
    });
    child.stdin.end(JSON.stringify(input));
  });
}

describe("backend agent worker CLI", () => {
  it("reads a backend request from stdin and writes agent-backend-response.v1 to stdout", async () => {
    const workspaceRoot = await mkdtemp(path.join(os.tmpdir(), "backend-agent-worker-cli-"));
    try {
      await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), workspaceRoot, {
        recursive: true
      });

      const result = await runWorkerCli({
        workspaceRoot,
        runId: "worker-cli-answer",
        userMessage: "总结当前知识库",
        mode: "deterministic-open-agent"
      });

      expect(result.exitCode).toBe(0);
      expect(result.stderr).toBe("");
      const response = JSON.parse(result.stdout);
      expect(response).toMatchObject({
        schemaVersion: "agent-backend-response.v1",
        runId: "worker-cli-answer",
        status: "SUCCEEDED",
        outputKind: "answer",
        requiresApproval: false,
        requiresConfirmation: false,
        wroteWorkspace: false
      });
      expect(response.displayText).toContain("Sources:");
    } finally {
      await rm(workspaceRoot, { recursive: true, force: true });
    }
  });
});
