import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const fixtureRoot = path.join(process.cwd(), "tests/fixtures/workspaces/basic-raw-mirror");

describe("workspace fixtures", () => {
  it("contains raw, schema, knowledge-base, and bootstrap mirror files", () => {
    expect(existsSync(path.join(fixtureRoot, "raw/tools/Skill vs CLI Tool 决策.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "raw/go/Go 基础语法.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "raw/agent/Agent Loop 失败复盘.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "schema/CLAUDE.md"))).toBe(true);
    expect(existsSync(path.join(fixtureRoot, "knowledge-base/moc.md"))).toBe(true);

    const mirror = readFileSync(
      path.join(fixtureRoot, "knowledge-base/topics/tools/Skill vs CLI Tool 决策.md"),
      "utf8"
    );
    expect(mirror).toContain("Raw mirror:");
    expect(mirror).toContain("Source path:");
    expect(mirror).toContain("## Content");
  });
});
