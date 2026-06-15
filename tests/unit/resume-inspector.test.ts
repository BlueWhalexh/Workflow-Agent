import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { inspectResumeWorkItem } from "../../src/domain/validation/resume-inspector.js";
import { sha256 } from "../../src/storage/sha.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "resume-inspector-"));
  await mkdir(path.join(tempRoot, "knowledge-base/topics/tools"), { recursive: true });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("resume inspector", () => {
  it("skips published work item when target content sha matches", async () => {
    const targetPath = "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md";
    const content = "# Skill vs CLI Tool 决策\n";
    await writeFile(path.join(tempRoot, targetPath), content, "utf8");

    const result = await inspectResumeWorkItem({
      workspaceRoot: tempRoot,
      workItem: {
        id: "rewrite-tools",
        status: "PUBLISHED",
        targetPaths: [targetPath],
        contentSha: sha256(content)
      }
    });

    expect(result.action).toBe("SKIP");
    expect(result.currentSha).toBe(sha256(content));
  });

  it("requests replan when published target sha changed", async () => {
    const targetPath = "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md";
    await writeFile(path.join(tempRoot, targetPath), "# Changed\n", "utf8");

    const result = await inspectResumeWorkItem({
      workspaceRoot: tempRoot,
      workItem: {
        id: "rewrite-tools",
        status: "PUBLISHED",
        targetPaths: [targetPath],
        contentSha: "old-sha"
      }
    });

    expect(result.action).toBe("REPLAN");
  });

  it("reports non-retryable validator blocks instead of rerunning them", async () => {
    const result = await inspectResumeWorkItem({
      workspaceRoot: tempRoot,
      workItem: {
        id: "rewrite-tools",
        status: "BLOCKED_BY_VALIDATOR",
        targetPaths: ["knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"],
        retryable: false
      }
    });

    expect(result.action).toBe("REPORT_FAILED");
  });
});
