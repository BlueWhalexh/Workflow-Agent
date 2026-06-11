import { readFile } from "node:fs/promises";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { appendLlmTraceEvent, previewText } from "../../src/domain/llm-trace/trace-writer.js";
import { AgentRunsStore } from "../../src/storage/agent-runs-store.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "llm-trace-writer-"));
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("llm trace writer", () => {
  it("appends canonical JSONL trace events", async () => {
    const store = new AgentRunsStore(tempRoot, "run-trace");
    await appendLlmTraceEvent(store, "work-a", {
      schemaVersion: "llm-trace.v1",
      type: "llm.call.started",
      eventId: "event-1",
      runId: "run-trace",
      workItemId: "work-a",
      agentNode: "note",
      providerCallId: "call-1",
      provider: "fake",
      model: "fake-note-model",
      timestamp: "2026-06-11T00:00:00.000Z",
      request: {
        messagesSha: "sha-messages",
        temperature: 0,
        thinkingEnabled: false
      }
    });

    const content = await readFile(path.join(tempRoot, ".agent-runs/run-trace/traces/work-a.jsonl"), "utf8");
    expect(content.trim().split("\n")).toHaveLength(1);
    expect(JSON.parse(content.trim())).toMatchObject({
      schemaVersion: "llm-trace.v1",
      type: "llm.call.started",
      provider: "fake"
    });
  });

  it("creates bounded previews without storing full text by default", () => {
    expect(previewText("abcdefghijklmnopqrstuvwxyz", 8)).toBe("abcdefgh");
  });
});
