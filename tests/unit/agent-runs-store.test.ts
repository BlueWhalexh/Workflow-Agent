import { mkdir, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "agent-runs-store-"));
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("AgentRunsStore", () => {
  it("writes and reads JSON artifacts under .agent-runs", async () => {
    const store = new AgentRunsStore(tempRoot, "run-test");
    await store.writeJson("plan.json", { runId: "run-test", mode: "SEMI_AUTOMATIC" });

    await expect(store.readJson<{ runId: string; mode: string }>("plan.json")).resolves.toEqual({
      runId: "run-test",
      mode: "SEMI_AUTOMATIC"
    });
  });

  it("returns the latest run id from agent run artifacts", async () => {
    await mkdir(path.join(tempRoot, ".agent-runs", "run-001"), { recursive: true });
    await mkdir(path.join(tempRoot, ".agent-runs", "run-003"), { recursive: true });
    await mkdir(path.join(tempRoot, ".agent-runs", "run-002"), { recursive: true });

    await expect(AgentRunsStore.latestRunId(tempRoot)).resolves.toBe("run-003");
  });
});
