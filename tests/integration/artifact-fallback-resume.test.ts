import { execFile } from "node:child_process";
import { cp, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const execFileAsync = promisify(execFile);
let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "artifact-resume-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("artifact fallback resume", () => {
  it("resumes from .agent-runs artifacts without runtime checkpoint state", async () => {
    await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/organize.ts",
      tempRoot,
      "整理全部知识库",
      "--auto-approve",
      "--run-id",
      "run-artifact"
    ]);

    const result = await execFileAsync(process.execPath, ["--import", "tsx", "src/cli/resume.ts", tempRoot]);
    const payload = JSON.parse(result.stdout) as {
      runId: string;
      decisions: Array<{ workItemId: string; action: string }>;
    };

    expect(payload.runId).toBe("run-artifact");
    expect(payload.decisions.some((decision) => decision.action === "SKIP")).toBe(true);
  });
});
