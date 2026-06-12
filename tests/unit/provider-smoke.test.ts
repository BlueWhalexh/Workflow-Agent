import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { describe, expect, it } from "vitest";
import { inspectRealProviderSmoke } from "../../src/runtime/provider/real-smoke.js";

const execFileAsync = promisify(execFile);

describe("real provider smoke harness", () => {
  it("skips DeepSeek smoke when required env is missing", () => {
    const result = inspectRealProviderSmoke({
      provider: "deepseek-real-smoke",
      env: {}
    });

    expect(result).toEqual({
      provider: "deepseek-real-smoke",
      status: "SKIPPED",
      realExternalCall: false,
      reason: "MISSING_ENV",
      requiredEnv: ["DEEPSEEK_API_KEY", "DEEPSEEK_BASE_URL", "DEEPSEEK_MODEL"]
    });
  });

  it("reports Claude Code smoke as blocked until SDK wiring exists", () => {
    const result = inspectRealProviderSmoke({
      provider: "claude-code-real-smoke",
      env: {}
    });

    expect(result).toEqual({
      provider: "claude-code-real-smoke",
      status: "BLOCKED",
      realExternalCall: false,
      reason: "CLAUDE_CODE_SDK_NOT_WIRED",
      requiredEnv: []
    });
  });

  it("CLI skip path does not perform a real external call", async () => {
    const result = await execFileAsync(process.execPath, [
      "--import",
      "tsx",
      "src/cli/provider-smoke.ts",
      "--provider",
      "deepseek-real-smoke"
    ]);

    const payload = JSON.parse(result.stdout) as { status: string; realExternalCall: boolean };
    expect(payload.status).toBe("SKIPPED");
    expect(payload.realExternalCall).toBe(false);
  });
});
