import { execFile } from "node:child_process";
import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const execFileAsync = promisify(execFile);
let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "cli-smoke-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("CLI smoke", () => {
  it("runs organize with auto approval", async () => {
    const result = await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--run-id",
      "run-cli"
    ]);

    expect(result.stdout).toContain("SUCCEEDED_WITH_WARNINGS");
    const payload = JSON.parse(result.stdout);
    expect(payload.artifactRoot).toBe(".agent-runs/run-cli");
    expect(payload.methodologyId).toBe("lmwiki-v1");
  });

  it("runs organize with explicit fake provider", async () => {
    const result = await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--provider",
      "fake",
      "--run-id",
      "run-cli-provider"
    ]);

    expect(result.stdout).toContain("SUCCEEDED_WITH_WARNINGS");
  });

  it("runs organize with MiMo vLLM fixture provider without token", async () => {
    const result = await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--provider",
      "mimo-vllm-fixture",
      "--run-id",
      "run-cli-mimo-vllm"
    ]);

    expect(result.stdout).toContain("SUCCEEDED_WITH_WARNINGS");
  });

  it("blocks unknown methodologies before workflow execution", async () => {
    await expect(
      execFileAsync(process.execPath, [
        "--import",
        "tsx",
        "src/cli/organize.ts",
        tempRoot,
        "整理全部知识库",
        "--auto-approve",
        "--methodology",
        "unknown",
        "--run-id",
        "run-cli-unknown-methodology"
      ])
    ).rejects.toMatchObject({
      stderr: expect.stringContaining("Unknown knowledge methodology: unknown")
    });
  });

  it("blocks deepseek-real provider unless explicitly allowed", async () => {
    await expect(
      execFileAsync(process.execPath, [
        "--import",
        "tsx",
        "src/cli/organize.ts",
        tempRoot,
        "整理全部知识库",
        "--auto-approve",
        "--provider",
        "deepseek-real",
        "--run-id",
        "run-cli-real-blocked"
      ])
    ).rejects.toMatchObject({
      stderr: expect.stringContaining("requires --allow-real-provider")
    });
  });

  it("blocks mimo-real provider unless explicitly allowed", async () => {
    await expect(
      execFileAsync(process.execPath, [
        "--import",
        "tsx",
        "src/cli/organize.ts",
        tempRoot,
        "整理全部知识库",
        "--auto-approve",
        "--provider",
        "mimo-real",
        "--run-id",
        "run-cli-mimo-real-blocked"
      ])
    ).rejects.toMatchObject({
      stderr: expect.stringContaining("requires --allow-real-provider")
    });
  });

  it("reports resume decisions for the latest run", async () => {
    await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--run-id",
      "run-cli"
    ]);

    const result = await execFileAsync(process.execPath, ["--import", "tsx", "src/cli/resume.ts", tempRoot]);

    expect(result.stdout).toContain("run-cli");
    expect(result.stdout).toContain("SKIP");
  });
});
