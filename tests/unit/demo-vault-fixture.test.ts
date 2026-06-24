import { describe, expect, it } from "vitest";
import { readFile, readdir, stat } from "node:fs/promises";
import { join } from "node:path";

const vaultRoot = join(process.cwd(), "fixtures/demo-vault");
const inboxRoot = join(vaultRoot, "Inbox");
const projectsRoot = join(vaultRoot, "Projects");
const rulesetPath = join(vaultRoot, "_rules/ruleset.json");
const goldenRoot = join(vaultRoot, "_golden");

async function listMarkdownFiles(dir: string): Promise<string[]> {
  const entries = await readdir(dir, { withFileTypes: true });
  return entries
    .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
    .map((entry) => entry.name)
    .sort();
}

describe("Phase 0 Demo Vault fixture", () => {
  it("contains exactly 12 intentionally mixed Inbox notes", async () => {
    const inboxNotes = await listMarkdownFiles(inboxRoot);

    expect(inboxNotes).toHaveLength(12);

    const contents = await Promise.all(
      inboxNotes.map(async (name) => readFile(join(inboxRoot, name), "utf8"))
    );
    const withFrontmatter = contents.filter((content) => content.startsWith("---\n")).length;
    const withoutFrontmatter = contents.length - withFrontmatter;
    const englishChineseMixed = contents.filter((content) => /[A-Za-z]/.test(content) && /[\u4e00-\u9fff]/.test(content));

    expect(withFrontmatter).toBeGreaterThan(0);
    expect(withoutFrontmatter).toBeGreaterThan(0);
    expect(englishChineseMixed.length).toBeGreaterThanOrEqual(8);
  });

  it("ships project targets, ruleset, and all golden baseline files", async () => {
    await expect(stat(projectsRoot)).resolves.toMatchObject({ isDirectory: expect.any(Function) });

    const ruleset = JSON.parse(await readFile(rulesetPath, "utf8")) as {
      schemaVersion?: number;
      writableRoots?: string[];
      frontmatterSchema?: { required?: string[] };
      maxOperationsPerChangeset?: number;
    };
    expect(ruleset.schemaVersion).toBe(1);
    expect(ruleset.writableRoots).toEqual(["Inbox/", "Projects/"]);
    expect(ruleset.frontmatterSchema?.required).toEqual(
      expect.arrayContaining(["title", "project", "type", "date", "status", "source"])
    );
    expect(ruleset.maxOperationsPerChangeset).toBeGreaterThanOrEqual(13);

    const goldenFiles = await readdir(goldenRoot);
    expect(goldenFiles.sort()).toEqual(["assignment.json", "entropy.json", "link-graph.json", "moc.md"]);

    const assignment = JSON.parse(await readFile(join(goldenRoot, "assignment.json"), "utf8")) as {
      notes?: Record<string, unknown>;
    };
    expect(Object.keys(assignment.notes ?? {})).toHaveLength(12);
  });
});
