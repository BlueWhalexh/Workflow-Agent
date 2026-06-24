import { describe, expect, it } from "vitest";
import { access, readFile, readdir, stat } from "node:fs/promises";
import { join } from "node:path";
import { z } from "zod";

const vaultRoot = join(process.cwd(), "fixtures/demo-vault");

async function fileExists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function yamlValue(text: string, key: string): string | undefined {
  const match = text.match(new RegExp(`^${key}:\\s*(.+)$`, "m"));
  return match?.[1]?.trim();
}

function yamlList(text: string, key: string): string[] {
  const inline = text.match(new RegExp(`^${key}:\\s*\\[(.*)\\]$`, "m"));
  if (inline) {
    return inline[1]
      .split(",")
      .map((item) => item.trim().replace(/^["']|["']$/g, ""))
      .filter(Boolean);
  }
  const block = text.match(new RegExp(`^${key}:\\n((?:  - .+\\n?)+)`, "m"));
  return block
    ? block[1].split("\n").map((line) => line.trim().replace(/^- /, "")).filter(Boolean)
    : [];
}

const MethodologySchema = z.object({
  schemaVersion: z.literal(1),
  methodologyId: z.literal("llm-wiki-three-layer"),
  rawWritable: z.literal(false),
  rawRequiresFrontmatter: z.literal(false),
  knowledgeBaseWritable: z.literal(true),
  knowledgeBaseRequiresFrontmatter: z.literal(true),
  globalSyncTargets: z.array(z.string()).superRefine((targets, ctx) => {
    for (const required of ["knowledge-base/index.md", "log.md"]) {
      if (!targets.includes(required)) {
        ctx.addIssue({ code: "custom", message: `missing ${required}` });
      }
    }
  })
});

describe("LLM Wiki Demo Vault structure", () => {
  it("uses raw / knowledge-base / schema as the top-level fixture layout", async () => {
    const topLevelEntries = await readdir(vaultRoot);

    for (const dir of ["raw", "raw/clippings", "raw/assets", "knowledge-base", "schema", "daily", "projects", "resources", "_golden"]) {
      expect((await stat(join(vaultRoot, dir))).isDirectory()).toBe(true);
    }

    for (const removed of ["Inbox", "Projects", "_rules"]) {
      expect(topLevelEntries).not.toContain(removed);
    }

    expect(await fileExists(join(vaultRoot, "AGENTS.md"))).toBe(true);
    expect(await fileExists(join(vaultRoot, "log.md"))).toBe(true);
    expect(await readFile(join(vaultRoot, "log.md"), "utf8")).toBe("# Vault Operation Log\n");
  });

  it("loads methodology.yaml into the expected LLM Wiki schema shape", async () => {
    const methodology = await readFile(join(vaultRoot, "schema/methodology.yaml"), "utf8");
    const parsed = MethodologySchema.parse({
      schemaVersion: Number(yamlValue(methodology, "schemaVersion")),
      methodologyId: yamlValue(methodology, "methodologyId"),
      rawWritable: /raw:[\s\S]*?writable: false/.test(methodology) ? false : true,
      rawRequiresFrontmatter: /raw:[\s\S]*?requires_frontmatter: false/.test(methodology) ? false : true,
      knowledgeBaseWritable: /knowledge-base:[\s\S]*?writable: true/.test(methodology),
      knowledgeBaseRequiresFrontmatter: /knowledge-base:[\s\S]*?requires_frontmatter: true/.test(methodology),
      globalSyncTargets: yamlList(methodology, "global_sync_targets")
    });

    expect(parsed.methodologyId).toBe("llm-wiki-three-layer");
  });

  it("upgrades ruleset.yaml with methodology-aware validator rules", async () => {
    const ruleset = await readFile(join(vaultRoot, "schema/ruleset.yaml"), "utf8");

    expect(yamlList(ruleset, "writableRoots")).toEqual(["knowledge-base/", "daily/", "projects/", "resources/"]);
    expect(ruleset).toContain("appendOnlyFiles:");
    expect(ruleset).toContain("- log.md");
    expect(ruleset).toContain("protectedRoots:");
    expect(ruleset).toContain("- raw/");
    expect(ruleset).toContain("- schema/");
    expect(ruleset).toContain("methodologyAware:");
    expect(ruleset).toContain("enforceLayerWritability: true");
    expect(ruleset).toContain("enforceAppendOnlyLog: true");
    expect(ruleset).toContain("enforceKbSync:");
    expect(ruleset).toContain("syncTargets: [knowledge-base/index.md, log.md]");
    expect(ruleset).toContain("enforceAtomicNoteUplink: true");
  });

  it("keeps raw notes out of writable roots and rewrites golden files for knowledge-base targets", async () => {
    const ruleset = await readFile(join(vaultRoot, "schema/ruleset.yaml"), "utf8");
    expect(yamlList(ruleset, "writableRoots")).not.toContain("raw/");

    const clippings = (await readdir(join(vaultRoot, "raw/clippings"))).filter((file) => file.endsWith(".md")).sort();
    expect(clippings).toEqual([
      "2026-06-03-frontmatter-study.md",
      "2026-06-04-research-reading.md",
      "2026-06-07-source-attribution.md",
      "2026-06-08-approval-expiry-research.md",
      "2026-06-09-sse-replay-test.md",
      "2026-06-11-moc-format-study.md"
    ]);

    const projectScratch = await readFile(join(vaultRoot, "raw/项目随手记.md"), "utf8");
    expect((projectScratch.match(/^## /gm) ?? [])).toHaveLength(6);

    for (const golden of ["assignment.json", "moc.md", "link-graph.json", "entropy.json"]) {
      expect(await fileExists(join(vaultRoot, "_golden", golden))).toBe(true);
    }

    const assignment = JSON.parse(await readFile(join(vaultRoot, "_golden/assignment.json"), "utf8")) as {
      notes: Record<string, { targetPath: string }>;
    };
    expect(Object.keys(assignment.notes)).toHaveLength(6);
    expect(Object.values(assignment.notes).every((entry) => entry.targetPath.startsWith("knowledge-base/"))).toBe(true);

    const entropy = JSON.parse(await readFile(join(vaultRoot, "_golden/entropy.json"), "utf8")) as {
      scoreModel: Record<string, number>;
      preOrganization: { rawNotesUnmapped: number; score: number };
    };
    expect(entropy.scoreModel).toMatchObject({
      missingKBLinkWeight: 1,
      orphanNoteWeight: 1,
      brokenLinkWeight: 2,
      missingFrontmatterWeight: 1
    });
    expect(entropy.preOrganization.rawNotesUnmapped).toBe(6);
    expect(entropy.preOrganization.score).toBe(13);
  });
});
