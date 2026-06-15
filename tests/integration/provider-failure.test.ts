import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { runOrganizeWorkflow } from "../../src/runtime/langgraph/graph.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "provider-failure-"));
  await cp(path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror"), tempRoot, {
    recursive: true
  });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("provider failure harness", () => {
  it("records timeout failure without publishing workspace changes", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-timeout",
      autoApprove: true,
      providerRuntime: {
        provider: "timeout-fixture",
        timeoutMs: 1
      }
    });

    expect(result.status).toBe("FAILED");
    expect(result.lastError).toBe("PROVIDER_TIMEOUT");

    const target = await readFile(
      path.join(tempRoot, "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"),
      "utf8"
    );
    expect(target).toContain("Raw mirror: true");

    const workItem = JSON.parse(
      await readFile(path.join(tempRoot, ".agent-runs/run-timeout/work-items/rewrite-tools-skill-vs-cli-tool-决策.json"), "utf8")
    ) as {
      status: string;
      attempts: Array<{ failureSource?: string; failureReason?: string; retryable?: boolean }>;
    };
    expect(workItem.status).toBe("FAILED_TIMEOUT");
    expect(workItem.attempts[0]).toMatchObject({
      failureSource: "provider",
      failureReason: "timeout",
      retryable: true
    });

    const trace = (await readFile(
      path.join(tempRoot, ".agent-runs/run-timeout/traces/rewrite-tools-skill-vs-cli-tool-决策.jsonl"),
      "utf8"
    ))
      .trim()
      .split("\n")
      .map((line) => JSON.parse(line) as { type: string; errorClass?: string; retryable?: boolean });
    expect(trace).toContainEqual(expect.objectContaining({ type: "llm.call.failed", errorClass: "timeout", retryable: true }));
  });

  it("retries timeout work items on the same run id while preserving attempts", async () => {
    const failed = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-timeout-retry",
      autoApprove: true,
      providerRuntime: {
        provider: "timeout-fixture",
        timeoutMs: 1
      }
    });

    expect(failed.status).toBe("FAILED");
    expect(failed.lastError).toBe("PROVIDER_TIMEOUT");

    const retried = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-timeout-retry",
      autoApprove: true,
      providerRuntime: {
        provider: "fake",
        timeoutMs: 30000
      }
    });

    expect(retried.status).toBe("SUCCEEDED_WITH_WARNINGS");
    const workItem = JSON.parse(
      await readFile(
        path.join(tempRoot, ".agent-runs/run-timeout-retry/work-items/rewrite-tools-skill-vs-cli-tool-决策.json"),
        "utf8"
      )
    ) as { status: string; attempts: Array<{ attempt: number; status: string; message: string }> };
    expect(workItem.status).toBe("PUBLISHED");
    expect(workItem.attempts.map((attempt) => attempt.status)).toEqual(["FAILED_TIMEOUT", "PUBLISHED"]);

    const target = await readFile(
      path.join(tempRoot, "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"),
      "utf8"
    );
    expect(target).not.toContain("Raw mirror: true");
  });

  it("blocks invalid provider content through Validator without publishing", async () => {
    const result = await runOrganizeWorkflow({
      workspaceRoot: tempRoot,
      instruction: "整理全部知识库",
      runId: "run-invalid-content",
      autoApprove: true,
      providerRuntime: {
        provider: "invalid-content-fixture",
        timeoutMs: 30000
      }
    });

    expect(result.status).toBe("FAILED");
    expect(result.lastError).toBe("PATCH_BLOCKED");

    const target = await readFile(
      path.join(tempRoot, "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"),
      "utf8"
    );
    expect(target).toContain("Raw mirror: true");

    const validation = JSON.parse(
      await readFile(path.join(tempRoot, ".agent-runs/run-invalid-content/validation.json"), "utf8")
    ) as { validation: { allowed: boolean; hardBlockers: string[] } };
    expect(validation.validation.allowed).toBe(false);
    expect(validation.validation.hardBlockers).toContain("TOPIC_NOTE_MISSING_SUMMARY");

    const workItem = JSON.parse(
      await readFile(
        path.join(tempRoot, ".agent-runs/run-invalid-content/work-items/rewrite-tools-skill-vs-cli-tool-决策.json"),
        "utf8"
      )
    ) as {
      status: string;
      attempts: Array<{ failureSource?: string; failureReason?: string; retryable?: boolean }>;
    };
    expect(workItem.status).toBe("BLOCKED_BY_VALIDATOR");
    expect(workItem.attempts[0]).toMatchObject({
      failureSource: "validator",
      failureReason: "TOPIC_NOTE_MISSING_SUMMARY",
      retryable: false
    });
  });
});
