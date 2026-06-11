import path from "node:path";
import { describe, expect, it } from "vitest";
import { scanWorkspace } from "../../src/domain/workspace/inventory.js";
import { detectPageState } from "../../src/domain/workspace/page-state.js";

const fixtureRoot = path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror");

describe("workspace inventory", () => {
  it("scans raw, schema, knowledge-base, and mirror candidates", async () => {
    const inventory = await scanWorkspace({ workspaceRoot: fixtureRoot });

    expect(inventory.rawFiles.map((file) => file.path).sort()).toEqual([
      "raw/agent/Agent Loop 失败复盘.md",
      "raw/go/Go 基础语法.md",
      "raw/tools/Skill vs CLI Tool 决策.md"
    ]);
    expect(inventory.schemaFiles.map((file) => file.path)).toEqual(["schema/CLAUDE.md"]);
    expect(inventory.knowledgeBasePages.map((page) => page.path)).toContain(
      "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"
    );
    expect(inventory.rawMirrorCandidates).toEqual([
      "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"
    ]);
  });

  it("detects bootstrap mirror page state", () => {
    const state = detectPageState(`# Title

Raw mirror: true
Source path: raw/tools/example.md

## Content

Raw body`);

    expect(state).toBe("BOOTSTRAP_MIRROR");
  });
});
