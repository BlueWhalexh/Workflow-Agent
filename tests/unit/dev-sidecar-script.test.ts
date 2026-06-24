import { describe, expect, it } from "vitest";
import { spawn } from "node:child_process";
import { mkdtemp, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";

const scriptPath = join(process.cwd(), "scripts/dev-sidecar.sh");

function runScript(args: string[]): Promise<{ code: number | null; stdout: string; stderr: string }> {
  return new Promise((resolve) => {
    const child = spawn("bash", [scriptPath, ...args], {
      cwd: process.cwd(),
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => {
      stdout += String(chunk);
    });
    child.stderr.on("data", (chunk) => {
      stderr += String(chunk);
    });
    child.on("close", (code) => {
      resolve({ code, stdout, stderr });
    });
  });
}

describe("dev-sidecar script skeleton", () => {
  it("prints help successfully", async () => {
    const result = await runScript(["--help"]);

    expect(result.code).toBe(0);
    expect(result.stdout).toContain("Usage:");
  });

  it("returns 1 when config is missing", async () => {
    const result = await runScript(["--config", "config/missing-sidecar.yaml"]);

    expect(result.code).toBe(1);
    expect(result.stderr).toContain("Config not found");
  });

  it("prints the listening URL for a valid config", async () => {
    const dir = await mkdtemp(join(tmpdir(), "sidecar-config-"));
    const config = join(dir, "sidecar.yaml");
    await writeFile(config, "port: 8765\n", "utf8");

    const result = await runScript(["--config", config]);

    expect(result.code).toBe(0);
    expect(result.stdout).toBe("Listening on http://127.0.0.1:8765\n");
  });

  it("returns 2 when the configured port is already occupied", async () => {
    const dir = await mkdtemp(join(tmpdir(), "sidecar-config-"));
    const config = join(dir, "sidecar.yaml");
    await writeFile(config, "port: 8765\nstubPortOccupied: true\n", "utf8");

    const result = await runScript(["--config", config]);

    expect(result.code).toBe(2);
    expect(result.stderr).toContain("Port is already in use");
  });
});
