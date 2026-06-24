import { describe, expect, it } from "vitest";
import { readFile, readdir, stat } from "node:fs/promises";
import { join } from "node:path";

const vaultRoot = join(process.cwd(), "fixtures/demo-vault");
const rawRoot = join(vaultRoot, "raw");
const knowledgeBaseRoot = join(vaultRoot, "knowledge-base");
const schemaRoot = join(vaultRoot, "schema");
const goldenRoot = join(vaultRoot, "_golden");

async function listMarkdownFiles(dir: string): Promise<string[]> {
  const entries = await readdir(dir, { withFileTypes: true });
  return entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
    .map((entry) => entry.name)
    .sort();
}

describe("Phase 0 Demo Vault fixture", () => {
  it("contains the LLM Wiki raw inputs without making raw writable", async () => {
    const clippings = await listMarkdownFiles(join(rawRoot, "clippings"));

    expect(clippings).toHaveLength(6);
    expect(await readFile(join(rawRoot, "项目随手记.md"), "utf8")).not.toMatch(/^---\n/);

    const contents = await Promise.all(
      clippings.map(async (name) => readFile(join(rawRoot, "clippings", name), "utf8"))
    );
    const englishChineseMixed = contents.filter((content) => /[A-Za-z]/.test(content) && /[\u4e00-\u9fff]/.test(content));

    expect(contents.every((content) => !content.startsWith("---\n"))).toBe(true);
    expect(englishChineseMixed.length).toBeGreaterThanOrEqual(5);
  });

  it("ships knowledge-base MOCs, schema rules, and all golden baseline files", async () => {
    await expect(stat(knowledgeBaseRoot)).resolves.toMatchObject({ isDirectory: expect.any(Function) });
    await expect(stat(schemaRoot)).resolves.toMatchObject({ isDirectory: expect.any(Function) });

    const ruleset = await readFile(join(schemaRoot, "ruleset.yaml"), "utf8");
    const writableRootsBlock = ruleset.match(/^writableRoots:\n((?:  - .+\n)+)/m)?.[1] ?? "";
    expect(ruleset).toContain("schemaVersion: 1");
    expect(writableRootsBlock).toContain("- knowledge-base/");
    expect(writableRootsBlock).not.toContain("- raw/");
    expect(ruleset).toContain("frontmatterSchema:");
    expect(ruleset).toContain("- title");
    expect(ruleset).toContain("- project");
    expect(ruleset).toContain("- type");
    expect(ruleset).toContain("- date");
    expect(ruleset).toContain("- status");
    expect(ruleset).toContain("- source");
    expect(ruleset).toContain("maxOperationsPerChangeset: 24");

    const goldenFiles = await readdir(goldenRoot);
    expect(goldenFiles.filter((file) => !file.startsWith(".")).sort()).toEqual([
      "assignment.json",
      "entropy.json",
      "link-graph.json",
      "moc.md"
    ]);

    const assignment = JSON.parse(await readFile(join(goldenRoot, "assignment.json"), "utf8")) as {
      notes?: Record<string, unknown>;
    };
    expect(Object.keys(assignment.notes ?? {})).toHaveLength(6);
  });
});
