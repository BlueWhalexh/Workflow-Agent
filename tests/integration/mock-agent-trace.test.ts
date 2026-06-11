import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "mock-agent-trace-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("mock agent trace", () => {
  it("writes canonical llm trace events for note agent work items", async () => {
    await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-trace",
      autoApprove: true
    });

    const tracePath = path.join(
      tempRoot,
      ".agent-runs/run-trace/traces/rewrite-tools-skill-vs-cli-tool-决策.jsonl"
    );
    const lines = (await readFile(tracePath, "utf8"))
      .trim()
      .split("\n")
      .map((line) => JSON.parse(line) as { type: string; provider: string; schemaVersion: string; usage?: unknown });

    expect(lines.map((line) => line.type)).toEqual(["llm.call.started", "llm.call.completed"]);
    expect(lines[0]).toMatchObject({ provider: "fake", schemaVersion: "llm-trace.v1" });
    expect(lines[1].usage).toMatchObject({ inputTokens: 1, outputTokens: 1, totalTokens: 2 });
  });
});
