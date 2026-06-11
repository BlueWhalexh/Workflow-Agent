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
