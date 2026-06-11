import { mkdir, mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { checkMerge } from "../../src/domain/patch/merge-guard.js";
import { publishBundle } from "../../src/domain/patch/publisher.js";
import { sha256 } from "../../src/storage/sha.js";

let tempRoot: string;

beforeEach(async () => {
  tempRoot = await mkdtemp(path.join(os.tmpdir(), "patch-test-"));
  await mkdir(path.join(tempRoot, "knowledge-base/topics/tools"), { recursive: true });
});

afterEach(async () => {
  await rm(tempRoot, { recursive: true, force: true });
});

describe("patch merge and publish", () => {
  it("blocks raw writes", async () => {
    const decision = await checkMerge({
      workspaceRoot: tempRoot,
      authorizedTargetPaths: ["raw/tools/bad.md"],
      bundle: {
        workItemId: "bad",
        status: "SUCCEEDED",
        targetPaths: ["raw/tools/bad.md"],
        files: [
          {
            path: "raw/tools/bad.md",
            changeType: "CREATED",
            baseSha: null,
            contentSha: sha256("bad"),
            content: "bad"
          }
        ],
        eval: { rawFilesSeen: [], rawMirrorConverted: false, placeholderIntroduced: false, wikilinksCreated: 0 }
      }
    });

    expect(decision.allowed).toBe(false);
    expect(decision.reasons).toContain("RAW_OR_SCHEMA_WRITE_BLOCKED");
  });

  it("publishes authorized knowledge-base file", async () => {
    const targetPath = "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md";
    const content = "# Skill vs CLI Tool 决策\n\n## 摘要\n\n已整理。\n";
    const bundle = {
      workItemId: "rewrite-tools",
      status: "SUCCEEDED" as const,
      targetPaths: [targetPath],
      files: [
        {
          path: targetPath,
          changeType: "CREATED" as const,
          baseSha: null,
          contentSha: sha256(content),
          content
        }
      ],
      eval: { rawFilesSeen: [], rawMirrorConverted: true, placeholderIntroduced: false, wikilinksCreated: 0 }
    };

    const decision = await checkMerge({
      workspaceRoot: tempRoot,
      authorizedTargetPaths: [targetPath],
      bundle
    });
    expect(decision.allowed).toBe(true);

    await publishBundle({ workspaceRoot: tempRoot, bundle });
    await expect(readFile(path.join(tempRoot, targetPath), "utf8")).resolves.toBe(content);
  });
});
